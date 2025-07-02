package dev.bpmcrafters.example.camunda7embedded.worker

import dev.bpmcrafters.example.camunda7embedded.db.DbService
import dev.bpmcrafters.example.camunda7embedded.db.MyEntity
import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.Throws

val logger = KotlinLogging.logger {}

@Component
@Transactional
class ExecuteActionWorker(
  private val dbService: DbService
) {

  @ProcessEngineWorker(topic = "execute-action")
  fun execute(taskInformation: TaskInformation): Map<String, Any> {
    val id = taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID] as String
    dbService.save(
      MyEntity(taskId = taskInformation.taskId, processInstanceId = id)
    )
    return mapOf("executed" to true)
  }

  @ProcessEngineWorker(topic = "execute-action-with-error")
  fun executeWithError(taskInformation: TaskInformation): Map<String, Any> {
    val id = taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID] as String
    dbService.save(
      MyEntity(taskId = taskInformation.taskId, processInstanceId = id)
    )
    throw IllegalArgumentException("executeWithError")
  }

  @ProcessEngineWorker(topic = "execute-action-with-bpmn-error")
  @Throws(BpmnErrorOccurred::class)
  fun executeWithBpmnError(taskInformation: TaskInformation): Map<String, Any> {
    val id = taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID] as String
    dbService.save(
      MyEntity(taskId = taskInformation.taskId, processInstanceId = id)
    )
    throw BpmnErrorOccurred("BPMN Error Occurred", "bpmn-error")
  }

  @ProcessEngineWorker(topic = "check-entity", autoComplete = true)
  fun checkCommitedEntity(taskInformation: TaskInformation) {
    val id = taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID] as String
    dbService.findById(id).ifPresent {
      e -> logger.info { "Found entity with id $id: created in task ${e.taskId}" }
    }
  }
}
