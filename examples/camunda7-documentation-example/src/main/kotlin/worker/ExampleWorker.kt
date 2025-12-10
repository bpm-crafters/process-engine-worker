package worker

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.documentation.api.ProcessEngineWorkerDocumentation

class ExampleWorker {

  @ProcessEngineWorker("example-worker")
  @ProcessEngineWorkerDocumentation(name = "Example Worker", description = "Example worker for documentation generation")
  fun execute(@Variable(name = "exampleDto") exampleDto: ExampleDto): ExampleDto {
    return exampleDto
  }
}
