package dev.bpmcrafters.example.quarkus

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import jakarta.ws.rs.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FulfillmentTest {
  private val address = Address("Street", "123", "City", "DE")
  private fun order(price: String, name: String = "book") = Order(UUID.randomUUID(), listOf(OrderPosition(name, 2, BigDecimal(price))), address, address)

  @Test
  fun `decimal totals and payment rejection are exact`() {
    val workers = FulfillmentWorkers(InventoryUseCase(0))
    assertEquals(BigDecimal("42.00"), order("21.00").total())
    val error = assertThrows(BpmnErrorOccurred::class.java) { workers.receivePayment(order("21.00")) }
    assertEquals("paymentFailed", error.errorCode)
    assertEquals(false, error.payload["paymentReceived"])
    assertEquals(BigDecimal("42.00"), error.payload["paymentAmount"])
    assertTrue((error.payload["paymentFailedReason"] as String).isNotBlank())
    assertEquals(true, workers.receivePayment(order("21.01"))["paymentReceived"])
    assertEquals(BigDecimal("0.20"), order("0.10").total())
  }

  @Test
  fun `inventory completes once manually and shipping reports completion`() {
    var completed = 0
    val completion = api<ServiceTaskCompletionApi> { _, args ->
      val command = args[0] as CompleteTaskCmd
      assertEquals(mapOf("shipped" to true), command.get())
      completed++
      CompletableFuture.completedFuture(Empty)
    }
    val workers = FulfillmentWorkers(InventoryUseCase(0))
    workers.fetchGoods(TaskInformation("job", emptyMap()), completion, order("1"))
    assertEquals(1, completed)
    val method = FulfillmentWorkers::class.java.methods.single { it.name == "fetchGoods" }
    assertFalse(method.getAnnotation(ProcessEngineWorker::class.java).autoComplete)
    assertEquals(true, workers.shipGoods(order("1"))["goodsShipped"])
  }

  @Test
  fun `inventory wait leaves independent notification execution available`() {
    val executor = Executors.newFixedThreadPool(4)
    try {
      val inventory = executor.submit { InventoryUseCase(1).fetch(order("1", "out-of-stock")) }
      assertEquals("notification", executor.submit<String> { "notification" }.get(500, TimeUnit.MILLISECONDS))
      inventory.get(3, TimeUnit.SECONDS)
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun `pool validates types deduplicates and preserves failed completions`() {
    var fail = true
    val completion = api<UserTaskCompletionApi> { _, args ->
      assertEquals(mapOf("customer-informed" to true), (args[0] as CompleteTaskCmd).get())
      if (fail) CompletableFuture.failedFuture<Empty>(IllegalStateException("offline"))
      else CompletableFuture.completedFuture(Empty)
    }
    val pool = NotificationPool(jacksonObjectMapper(), completion)
    val task = TaskInformation("123", mapOf(CommonRestrictions.ACTIVITY_ID to FAILED_PAYMENT, CommonRestrictions.PROCESS_DEFINITION_KEY to PROCESS_ID))
    val payload = mapOf("order" to order("21"), "paymentFailedReason" to "Rejected", "paymentAmount" to "42.00")
    pool.accept(task, payload)
    pool.accept(task.withReason("update"), payload)
    assertEquals(1, pool.list().size)
    assertThrows(NotFoundException::class.java) { pool.get("123", DELAYED_INVENTORY) }
    assertThrows(NotFoundException::class.java) { pool.get("unknown", FAILED_PAYMENT) }
    assertThrows(Exception::class.java) { pool.complete("123", FAILED_PAYMENT) }
    assertEquals(1, pool.list().size)
    fail = false
    pool.complete("123", FAILED_PAYMENT)
    assertTrue(pool.list().isEmpty())
    pool.command().termination.accept(task)
    pool.command().termination.accept(task)
    pool.accept(task, emptyMap())
    pool.accept(task.copy(meta = task.meta + (CommonRestrictions.PROCESS_DEFINITION_KEY to "other")), payload)
    assertTrue(pool.list().isEmpty())
  }

  @Test
  fun `refresh failure does not kill subsequent refresh`() {
    var calls = 0
    CamundaDeliveryLifecycle.refreshSafely { calls++; error("temporary") }
    CamundaDeliveryLifecycle.refreshSafely { calls++ }
    assertEquals(2, calls)
    CamundaBridge().closeOwned() // Disabled shutdown must not construct a client.
  }
}
