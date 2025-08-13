package dev.bpmcrafters.processengine.worker.registrar

/**
 * Interface for variable converter.
 * @since 0.6.0
 */
interface VariableConverter {
    /**
     * Reads from the value to a target type.
     * @param value value structure.
     * @param type target class.
     * @return cast value.
     */
    fun <T : Any> mapToType(value: Any?, type: Class<T>): T
}