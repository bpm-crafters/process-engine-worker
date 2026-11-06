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
import jakarta.enterprise.inject.Instance
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

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
