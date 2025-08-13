package dev.bpmcrafters.processengine.worker

import dev.bpmcrafters.processengine.worker.registrar.JacksonVariableConverter
import dev.bpmcrafters.processengine.worker.registrar.NoneVariableConverter
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter
import org.springframework.core.annotation.AliasFor
import kotlin.reflect.KClass

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
    val mandatory: Boolean = true,
    /**
     * Specifies a converter class for this variable. Defaults to Jackson converter.
     */
    val converter: KClass<out VariableConverter> = NoneVariableConverter::class,
) {
    companion object {
        /**
         * Null value for the variable name.
         */
        const val DEFAULT_UNNAMED_NAME = "__unnamed"
    }
}
