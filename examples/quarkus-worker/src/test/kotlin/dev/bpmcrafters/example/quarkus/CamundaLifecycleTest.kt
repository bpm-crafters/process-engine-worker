package dev.bpmcrafters.example.quarkus

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.bpmcrafters.processengineapi.adapter.c8.task.delivery.SubscribingRefreshingZeebeJobUserTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c8.task.delivery.SubscribingServiceTaskDelivery
import dev.bpmcrafters.processengineapi.adapter.c8.task.subscription.C8TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.task.*
import io.quarkus.runtime.StartupEvent
import io.quarkus.runtime.ShutdownEvent
import io.camunda.client.CamundaClient
import jakarta.annotation.Priority
import jakarta.enterprise.inject.Disposes
import jakarta.enterprise.inject.Instance
import jakarta.inject.Qualifier
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class CamundaLifecycleTest {
  private fun <T> instance(value: T): Instance<T> = api { name, _ ->
    check(name == "get") { "Unexpected lookup $name" }
    value
  }

  @Test fun `disabled lifecycle never resolves engine beans`() {
    fun <T> forbidden(): Instance<T> = api { _, _ -> error("Disabled lifecycle resolved an engine bean") }
    val pool = NotificationPool(jacksonObjectMapper(), mock(UserTaskCompletionApi::class.java))
    val lifecycle = CamundaDeliveryLifecycle(false, pool, forbidden(), forbidden(), forbidden())
    lifecycle.register(StartupEvent())
    lifecycle.activate(StartupEvent())
    lifecycle.stop(ShutdownEvent())
    CamundaBridge().closeAfterDrain(ShutdownEvent())
  }

  @Test fun `owned client closes once across normal shutdown and disposer fallback`() {
    val client = mock(CamundaClient::class.java)
    val bridge = CamundaBridge()
    bridge.closeOwned()
    bridge.own(client)
    bridge.closeAfterDrain(ShutdownEvent())
    bridge.dispose(client)
    bridge.closeOwned()
    verify(client, times(1)).close()
  }

  @Test fun `both owned clients close once after drain and repeated disposer fallbacks`() {
    val serviceClient = mock(CamundaClient::class.java)
    val notificationClient = mock(CamundaClient::class.java)
    val bridge = CamundaBridge()
    bridge.own(serviceClient)
    bridge.own(notificationClient)

    verifyNoInteractions(serviceClient, notificationClient)
    bridge.closeAfterDrain(ShutdownEvent())
    bridge.dispose(serviceClient)
    bridge.disposeNotifications(notificationClient)
    bridge.closeAfterDrain(ShutdownEvent())

    verify(serviceClient, times(1)).close()
    verify(notificationClient, times(1)).close()
  }

  @Test fun `either qualified disposer closes both clients without a shutdown event`() {
    for (notificationsFirst in listOf(false, true)) {
      val serviceClient = mock(CamundaClient::class.java)
      val notificationClient = mock(CamundaClient::class.java)
      val bridge = CamundaBridge()
      bridge.own(serviceClient)
      bridge.own(notificationClient)

      if (notificationsFirst) bridge.disposeNotifications(notificationClient) else bridge.dispose(serviceClient)
      verify(serviceClient, times(1)).close()
      verify(notificationClient, times(1)).close()
      bridge.dispose(serviceClient)
      bridge.disposeNotifications(notificationClient)
      verify(serviceClient, times(1)).close()
      verify(notificationClient, times(1)).close()
    }
  }

  @Test fun `close failure does not leak other client and repeated disposal does not retry`() {
    val serviceClient = mock(CamundaClient::class.java)
    val notificationClient = mock(CamundaClient::class.java)
    val firstFailure = IllegalStateException("service close failed")
    val secondFailure = IllegalStateException("notification close failed")
    doThrow(firstFailure).`when`(serviceClient).close()
    doThrow(secondFailure).`when`(notificationClient).close()
    val bridge = CamundaBridge()
    bridge.own(serviceClient)
    bridge.own(notificationClient)

    val thrown = assertThrows(IllegalStateException::class.java) { bridge.closeAfterDrain(ShutdownEvent()) }
    assertSame(firstFailure, thrown)
    assertEquals(listOf(secondFailure), thrown.suppressed.toList())
    bridge.dispose(serviceClient)
    bridge.disposeNotifications(notificationClient)
    verify(serviceClient, times(1)).close()
    verify(notificationClient, times(1)).close()
  }

  @Test fun `concurrent ownership and duplicate shutdown preserve both clients exactly once`() {
    val clients = List(2) { mock(CamundaClient::class.java) }
    val bridge = CamundaBridge()
    CompletableFuture.allOf(*clients.map { client ->
      CompletableFuture.runAsync { bridge.own(client) }
    }.toTypedArray()).get(10, TimeUnit.SECONDS)
    CompletableFuture.allOf(*clients.map { client ->
      CompletableFuture.runAsync { bridge.own(client); bridge.closeOwned() }
    }.toTypedArray()).get(10, TimeUnit.SECONDS)
    clients.forEach { verify(it, times(1)).close() }
  }

  @Test fun `notification qualifier isolates producer delivery completion and disposer`() {
    fun method(name: String) = CamundaBridge::class.java.methods.single { it.name == name }
    fun notificationParameter(name: String) = method(name).parameterAnnotations.first()
      .any { it is NotificationClient }

    assertTrue(NotificationClient::class.java.isAnnotationPresent(Qualifier::class.java))
    assertTrue(method("notificationClient").isAnnotationPresent(NotificationClient::class.java))
    assertFalse(method("client").isAnnotationPresent(NotificationClient::class.java))
    listOf("userDelivery", "userCompletion", "disposeNotifications").forEach {
      assertTrue(notificationParameter(it), "$it must use the notification client")
    }
    listOf("serviceDelivery", "serviceCompletion", "startProcess", "deployment", "dispose").forEach {
      assertFalse(notificationParameter(it), "$it must retain the default client")
    }
    listOf("dispose", "disposeNotifications").forEach {
      assertTrue(method(it).parameterAnnotations.single().any { annotation -> annotation is Disposes })
    }
  }

  @Test fun `delivery starts only after pool and three workers register and shutdown releases pool`() {
    val repository = InMemSubscriptionRepository()
    val users = mock(SubscribingRefreshingZeebeJobUserTaskDelivery::class.java)
    val services = mock(SubscribingServiceTaskDelivery::class.java)
    val subscriptions = C8TaskSubscriptionApiImpl(repository, users)
    val pool = NotificationPool(jacksonObjectMapper(), mock(UserTaskCompletionApi::class.java))
    val lifecycle = CamundaDeliveryLifecycle(true, pool, instance<TaskSubscriptionApi>(subscriptions), instance(services), instance(users))
    lifecycle.register(StartupEvent())
    assertEquals(1, repository.getTaskSubscriptions().size)
    verifyNoInteractions(services, users)
    // Equivalent extension registration occurs at the default observer priority (2500).
    listOf("retrievePayment", "fetchGoods", "shipGoods").forEach { topic ->
      subscriptions.subscribeForTask(SubscribeForTaskCmd(emptyMap(), TaskType.EXTERNAL, topic, null,
        TaskHandler { _, _ -> }, TaskTerminationHandler { _ -> })).get()
    }
    doAnswer { assertEquals(4, repository.getTaskSubscriptions().size); null }.`when`(services).subscribe()
    lifecycle.activate(StartupEvent())
    val order = inOrder(services, users)
    order.verify(services).subscribe()
    order.verify(users).subscribe()
    lifecycle.stop(ShutdownEvent())
    order.verify(users).unsubscribeAll()
    assertEquals(3, repository.getTaskSubscriptions().size)
  }

  @Test fun `observer priorities bracket extension startup and shutdown`() {
    fun priority(method: String): Int = CamundaDeliveryLifecycle::class.java.methods.single { it.name == method }
      .parameterAnnotations.single().filterIsInstance<Priority>().single().value
    assertEquals(2400, priority("register"))
    assertEquals(3000, priority("activate"))
    assertEquals(2000, priority("stop"))
    val close = CamundaBridge::class.java.methods.single { it.name == "closeAfterDrain" }
    assertEquals(3000, close.parameterAnnotations.single().filterIsInstance<Priority>().single().value)
  }
}
