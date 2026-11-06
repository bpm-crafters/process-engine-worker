package dev.bpmcrafters.example.quarkus

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.NotFoundException
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

const val PROCESS_ID = "Process_OrderFulfillment_CCON2024"
const val FAILED_PAYMENT = "inform_customer_failed_payment"
const val DELAYED_INVENTORY = "inform_customer_delay"
const val PROCESS_RESOURCE = "C8/Order Fulfillment.bpmn"

data class Notification(val taskId: String, val type: String, val orderId: String)
data class NotificationDetails(
  val taskId: String, val type: String, val order: Order,
  val paymentReference: String? = null, val failureReason: String? = null, val amount: BigDecimal? = null,
  val items: List<String>? = null
)

/** Local, reconstructible pool of leased legacy job user tasks, indexed by engine task ID. */
@ApplicationScoped
class NotificationPool(private val mapper: ObjectMapper, private val completion: UserTaskCompletionApi) {
  private val tasks = ConcurrentHashMap<String, NotificationDetails>()

  fun command() = SubscribeForTaskCmd(
    restrictions = mapOf(CommonRestrictions.PROCESS_DEFINITION_KEY to PROCESS_ID),
    taskType = TaskType.USER, taskDescriptionKey = null, payloadDescription = null,
    action = TaskHandler { task, payload -> accept(task, payload) },
    termination = TaskTerminationHandler { task -> tasks.remove(task.taskId) }
  )

  internal fun accept(task: TaskInformation, payload: Map<String, Any?>) {
    if (task.meta[CommonRestrictions.PROCESS_DEFINITION_KEY] != PROCESS_ID) return
    val type = task.meta[CommonRestrictions.ACTIVITY_ID]
    if (type != FAILED_PAYMENT && type != DELAYED_INVENTORY) return
    val details = try {
      val order = mapper.convertValue(payload["order"], Order::class.java) ?: return
      if (type == FAILED_PAYMENT) {
        val reason = payload["paymentFailedReason"] as? String ?: return
        val amount = payload["paymentAmount"]?.toString()?.toBigDecimalOrNull() ?: return
        NotificationDetails(task.taskId, type, order, order.orderId.toString(), reason, amount)
      } else NotificationDetails(task.taskId, type, order, items = order.orderPositions.map { it.name })
    } catch (_: IllegalArgumentException) {
      return
    }
    tasks.putIfAbsent(task.taskId, details)
  }

  fun list(): List<Notification> = tasks.values.map { Notification(it.taskId, it.type, it.order.orderId.toString()) }.sortedBy { it.taskId }
  fun get(taskId: String, type: String): NotificationDetails = tasks[taskId]?.takeIf { it.type == type } ?: throw NotFoundException()
  fun complete(taskId: String, type: String) {
    val task = get(taskId, type)
    completion.completeTask(CompleteTaskCmd(taskId) { mapOf("customer-informed" to true) }).get()
    tasks.remove(taskId, task) // Failed engine commands retain the local task.
  }
}
