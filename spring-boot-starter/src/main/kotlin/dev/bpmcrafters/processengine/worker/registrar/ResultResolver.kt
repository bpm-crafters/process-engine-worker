package dev.bpmcrafters.processengine.worker.registrar

import java.lang.reflect.Method
import java.util.function.Predicate

/**
 * Result resolver is responsible for mapping the invocation result of the process engine into payload for task completion.
 * @since 0.0.4
 */
open class ResultResolver(
  private val strategies: List<ResultResolutionStrategy>
) {

  companion object {
    /**
     * Creates a new builder responsible for creation of the parameter resolver.
     */
    @JvmStatic
    fun builder() = ResultResolverBuilder()

    class ResultResolverBuilder(
      val strategies: MutableList<ResultResolutionStrategy> = mutableListOf()
    ) {

      init {
        addStrategy(
          /*
           * Identity matcher detecting the map type or compatible.
           */
          ResultResolutionStrategy(
            resultMatcher = { method -> method.hasPayloadReturnType() },
            resultConverter = { result ->
              if (result == null) {
                mapOf()
              } else {
                @Suppress("UNCHECKED_CAST")
                result as Map<String, Any>
              }
            }
          )
        )
      }

      /**
       * Adds a new strategy.
       * @param strategy strategy to add.
       * @return builder instance.
       */
      fun addStrategy(strategy: ResultResolutionStrategy): ResultResolverBuilder = strategies.add(strategy).let { this }

      /**
       * Builds a new result resolver.
       * @return resolver.
       */
      fun build(): ResultResolver = ResultResolver(strategies)
    }
  }


  /**
   * A strategy is responsible for mapping one return type to a Map<String, Object.>
   *   @param resultMatcher matcher for the strategy to resolve the result.
   *   @param resultConverter converter function.
   */
  data class ResultResolutionStrategy(
    val resultMatcher: Predicate<Method>, // pass the entire method to the macher
    val resultConverter: (value: Any?) -> Map<String, Any>
  )

  /**
   * Checks if at least one strategy feels responsible for converting the result of the method invocation to
   * payload result of type Map<String Any>.
   * @param method method to test.
   * @return true, if at least one strategy is matching the method.
   */
  open fun payloadReturnType(method: Method): Boolean = strategies
    .any { it.resultMatcher.test(method) }

  /**
   * Resolves the invocation results.
   * @param method invoked process engine method.
   * @param result result.
   * @return process payload map.
   */
  open fun resolve(method: Method, result: Any?): Map<String, Any> = strategies
    .first { it.resultMatcher.test(method) }
    .resultConverter
    .invoke(result)
}
