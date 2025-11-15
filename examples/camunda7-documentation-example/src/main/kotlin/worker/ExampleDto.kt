package worker

import dev.bpmcrafters.processengine.worker.documentation.api.ProcessEngineWorkerPropertyDocumentation

data class ExampleDto(
  @field:ProcessEngineWorkerPropertyDocumentation(label = "Example Input")
  val exampleInput: String
) {
}
