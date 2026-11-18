package dev.bpmcrafters.processengine.worker.quarkus

import io.quarkus.arc.Arc
import io.quarkus.runtime.annotations.Recorder

@Recorder
class WorkerRecorder {
  fun configure(descriptors: List<WorkerDefinition>) {
    Arc.container().instance(WorkerRuntime::class.java).get().configure(descriptors)
  }
}
