package dev.bpmcrafters.processengine.worker.registrar

/**
 * Invokes a worker using resolved arguments. Framework adapters must invoke their managed proxy.
 * @since 0.9.0
 **/
fun interface WorkerInvoker {
  /**
   * Calls the worker with the resolved arguments and returns its result.
   **/
  @Throws(Exception::class)
  fun invoke(arguments: Array<Any?>): Any?
}
