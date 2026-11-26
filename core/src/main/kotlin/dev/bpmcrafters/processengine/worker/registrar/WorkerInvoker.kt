package dev.bpmcrafters.processengine.worker.registrar

/** Invokes a worker using resolved arguments. Framework adapters must invoke their managed proxy. */
fun interface WorkerInvoker {
  @Throws(Exception::class)
  fun invoke(arguments: Array<Any?>): Any?
}
