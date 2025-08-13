package dev.bpmcrafters.processengine.worker.registrar

/**
 * Null object for converter.
 * @since 0.5.1
 */
object NoneVariableConverter : VariableConverter {
    override fun <T : Any> mapToType(value: Any?, type: Class<T>): T = throw NotImplementedError()
}