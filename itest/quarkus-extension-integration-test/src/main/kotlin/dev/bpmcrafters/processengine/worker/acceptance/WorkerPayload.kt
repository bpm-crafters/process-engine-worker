package dev.bpmcrafters.processengine.worker.acceptance

import io.quarkus.runtime.annotations.RegisterForReflection
import java.io.Serializable

@RegisterForReflection(serialization = true)
@JvmRecord
data class WorkerPayload(val name: String, val details: Details) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }

  @RegisterForReflection(serialization = true)
  @JvmRecord
  data class Details(val amount: Int, val state: State) : Serializable {
    companion object {
      private const val serialVersionUID = 1L
    }
  }

  @RegisterForReflection(serialization = true)
  enum class State { READY, DONE }
}
