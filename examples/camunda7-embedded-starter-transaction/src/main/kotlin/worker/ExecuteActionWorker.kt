package dev.bpmcrafters.example.camunda7embedded.worker

import dev.bpmcrafters.example.camunda7embedded.db.MyEntity
import dev.bpmcrafters.example.camunda7embedded.db.MyEntityRepository
import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.Throws

@Component
@Transactional
class ExecuteActionWorker(
  private val myEntityRepository: MyEntityRepository
) {

  @ProcessEngineWorker(topic = "execute-action")
  fun execute(taskInformation: TaskInformation): Map<String, Any> {
    myEntityRepository.save(
      MyEntity(name = taskInformation.taskId)
    )
    return mapOf("executed" to true)
  }

  @ProcessEngineWorker(topic = "execute-action-with-error")
  fun executeWithError(taskInformation: TaskInformation): Map<String, Any> {
    myEntityRepository.save(
      MyEntity(name = taskInformation.taskId)
    )
    throw IllegalArgumentException("executeWithError")
  }

  @ProcessEngineWorker(topic = "execute-action-with-bpmn-error")
  @Throws(BpmnErrorOccurred::class)
  fun executeWithBpmnError(taskInformation: TaskInformation): Map<String, Any> {
    myEntityRepository.save(
      MyEntity(name = taskInformation.taskId)
    )
    throw BpmnErrorOccurred("BPMN Error Occurred", "bpmn-error")
  }
}
