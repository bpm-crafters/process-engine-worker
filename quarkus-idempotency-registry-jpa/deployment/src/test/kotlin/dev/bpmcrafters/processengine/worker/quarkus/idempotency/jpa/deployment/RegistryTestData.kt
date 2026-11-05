package dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa.deployment

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation

object RegistryTestData {
  fun task(taskId: String, processId: String): TaskInformation =
    TaskInformation(taskId, mapOf(CommonRestrictions.PROCESS_INSTANCE_ID to processId))
}
