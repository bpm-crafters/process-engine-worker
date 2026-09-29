package dev.bpmcrafters.processengine.worker.quarkus

/**
 * Exception thrown by the deployment module if the build-time validation of the process engine workers fails.
 * @param message description of the violation.
 * @since 0.8.6
 */
class ProcessEngineWorkerValidationException(message: String) : RuntimeException(message)
