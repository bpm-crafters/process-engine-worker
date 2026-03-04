package dev.bpmcrafters.processengine.worker.configuration

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.JpaIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.TaskLogEntry
import dev.bpmcrafters.processengine.worker.idempotency.TaskLogEntryRepository
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Autoconfiguration for JPA-based Idempotency Registry.
 * @since 0.8.0
 */
@AutoConfiguration(before = [ProcessEngineWorkerAutoConfiguration::class])
@ConditionalOnMissingBean(IdempotencyRegistry::class)
@EntityScan(basePackageClasses = [TaskLogEntry::class])
@EnableJpaRepositories(basePackageClasses = [TaskLogEntryRepository::class])
class JpaIdempotencyAutoConfiguration {

  @Bean
  fun jpaIdempotencyRegistry(taskLogEntryRepository: TaskLogEntryRepository): IdempotencyRegistry =
    JpaIdempotencyRegistry(taskLogEntryRepository)

}
