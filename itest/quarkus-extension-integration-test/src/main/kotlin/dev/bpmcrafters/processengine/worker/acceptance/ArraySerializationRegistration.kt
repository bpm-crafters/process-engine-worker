package dev.bpmcrafters.processengine.worker.acceptance

import io.quarkus.runtime.annotations.RegisterForReflection

/** Application DTO arrays need application metadata; do not hide common-array extension gaps. */
@RegisterForReflection(targets = [Array<WorkerPayload>::class, Array<Array<WorkerPayload>>::class], serialization = true)
class ArraySerializationRegistration private constructor()
