package dev.bpmcrafters.processengine.worker.quarkus

import io.quarkus.arc.runtime.BeanContainer
import io.quarkus.runtime.annotations.Recorder

/**
 * Recorder registering the process engine workers detected at build time during runtime initialization.
 * @since 0.8.6
 */
@Recorder
open class ProcessEngineWorkerRecorder {

  /**
   * Registers the workers of the given classes.
   * @param beanContainer bean container.
   * @param workerClassNames names of the bean classes declaring worker methods.
   */
  open fun registerWorkers(beanContainer: BeanContainer, workerClassNames: List<String>) {
    val classLoader = Thread.currentThread().contextClassLoader ?: ProcessEngineWorkerRecorder::class.java.classLoader
    val workerClasses = workerClassNames.map { Class.forName(it, true, classLoader) }
    beanContainer.beanInstance(ProcessEngineWorkerRegistration::class.java).register(workerClasses)
  }
}
