package dev.bpmcrafters.processengine.worker.quarkus

/** Infrastructure failures cross CDI as runtime failures; business exceptions retain their Jakarta policy. */
class DeliveryProcessingException(cause: Throwable) : RuntimeException(cause.message, cause)
