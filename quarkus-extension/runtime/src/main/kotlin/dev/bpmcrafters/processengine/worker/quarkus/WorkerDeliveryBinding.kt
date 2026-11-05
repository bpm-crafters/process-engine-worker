package dev.bpmcrafters.processengine.worker.quarkus

import jakarta.interceptor.InterceptorBinding

@InterceptorBinding
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class WorkerDeliveryBinding
