package dev.bpmcrafters.processengine.worker.configuration

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.JpaIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.TaskLogEntryRepository
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * Autoconfiguration for JPA-based Idempotency Registry.
 * @since 0.8.0
 */
@AutoConfiguration(before = [ProcessEngineWorkerAutoConfiguration::class])
@ConditionalOnMissingBean(IdempotencyRegistry::class)
class JpaIdempotencyAutoConfiguration {

  @Bean
  fun jpaIdempotencyRegistry(taskLogEntryRepository: TaskLogEntryRepository): IdempotencyRegistry =
    JpaIdempotencyRegistry(taskLogEntryRepository)

}
