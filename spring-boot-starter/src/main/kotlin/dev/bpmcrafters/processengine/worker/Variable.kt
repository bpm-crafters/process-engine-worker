package dev.bpmcrafters.processengine.worker

import org.springframework.core.annotation.AliasFor

/**
 * Indicates a typed process variable to be injected into the worker method.
 * @since 0.0.1
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.ANNOTATION_CLASS)
@MustBeDocumented
annotation class Variable(
    /**
     * Name of the variable.
     */
    @get:AliasFor(attribute = "value")
    val name: String = DEFAULT_UNNAMED_NAME,
    /**
     * Name of the variable.
     */
    @get:AliasFor(attribute = "name")
    val value: String = DEFAULT_UNNAMED_NAME,
    /**
     * Indicates that a variable is mandatory. Defaults to `true`.
     */
    val mandatory: Boolean = true
) {
    companion object {
        /**
         * Null value for the variable name.
         */
        const val DEFAULT_UNNAMED_NAME = "__unnamed"
    }
}
