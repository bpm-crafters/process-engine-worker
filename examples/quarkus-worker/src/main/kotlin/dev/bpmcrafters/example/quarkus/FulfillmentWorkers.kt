package dev.bpmcrafters.example.quarkus

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.math.BigDecimal

@ApplicationScoped
class InventoryUseCase(@ConfigProperty(name = "example.inventory.replenishment-seconds", defaultValue = "60") private val delaySeconds: Long) {
  fun fetch(order: Order) {
    if (order.orderPositions.any { it.name == "out-of-stock" }) {
      LOG.infof("INVENTORY: Replenishing order %s, waiting %s seconds", order.orderId, delaySeconds)
      Thread.sleep(Math.multiplyExact(delaySeconds, 1000))
      LOG.infof("INVENTORY: Replenished order %s", order.orderId)
    }
  }

  companion object {
    private val LOG = Logger.getLogger(InventoryUseCase::class.java)
  }
}

@ApplicationScoped
class FulfillmentWorkers(private val inventory: InventoryUseCase) {
  @ProcessEngineWorker(topic = "retrievePayment")
  fun receivePayment(@Variable("order") order: Order): Map<String, Any> {
    val total = order.total()
    if (total.compareTo(BigDecimal("42.00")) == 0) {
      throw BpmnErrorOccurred(
        "Payment rejected", "paymentFailed", mapOf(
          "paymentReceived" to false, "paymentFailedReason" to "The amount 42.00 is rejected by the example payment service", "paymentAmount" to total
        )
      )
    }
    return mapOf("paymentReceived" to true, "paymentId" to order.orderId.toString())
  }

  @ProcessEngineWorker(topic = "fetchGoods", autoComplete = false)
  fun fetchGoods(task: TaskInformation, completion: ServiceTaskCompletionApi, @Variable("order") order: Order) {
    inventory.fetch(order)
    completion.completeTask(CompleteTaskCmd(task.taskId) { mapOf("shipped" to true) }).get()
  }

  @ProcessEngineWorker(topic = "shipGoods")
  fun shipGoods(@Variable("order") order: Order): Map<String, Any> {
    LOG.infof("SHIPPING: Shipping order %s to %s", order.orderId, order.shippingAddress)
    return mapOf("goodsShipped" to true)
  }

  companion object {
    private val LOG = Logger.getLogger(FulfillmentWorkers::class.java)
  }
}
