package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.registrar.*
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import io.quarkus.arc.Arc
import io.quarkus.arc.InstanceHandle
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.AmbiguousResolutionException
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.TransactionManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

@Singleton
class WorkerRuntime {
  @Inject
  lateinit var config: WorkerConfig
  @Inject
  lateinit var transactions: TransactionManager
  private var descriptors = emptyList<WorkerDefinition>()
  private val handles = mutableListOf<InstanceHandle<*>>()
  private var subscriptions: SubscriptionTracker? = null
  private var deliveries: DeliveryLifecycle? = null
  private var stopping: StopState? = null
  private val destroying = ThreadLocal<Boolean>()

  private class StopState(val owned: List<InstanceHandle<*>>, val lifecycle: DeliveryLifecycle?) {
    val drained = CompletableFuture<Void>()
    val released = lifecycle?.resourcesReleased() ?: CompletableFuture.completedFuture<Void>(null)
    val cleanup = CompletableFuture<Void>()
    @Volatile
    var initializing: Thread? = Thread.currentThread()
  }

  fun configure(descriptors: List<WorkerDefinition>) {
    this.descriptors = java.util.List.copyOf(descriptors)
  }

  fun start(@Observes event: StartupEvent) {
    if (!config.enabled() || !config.registerProcessWorkers() || descriptors.isEmpty()) return
    try {
      val lifecycle = DeliveryLifecycle(Arc.container().executorService)
      deliveries = lifecycle
      subscriptions = SubscriptionTracker(required(TaskSubscriptionApi::class.java), lifecycle)
      val registrar = registrar()
      descriptors.forEach { register(registrar, it) }
    } catch (failure: Exception) {
      try {
        stop()
      } catch (cleanupFailure: RuntimeException) {
        failure.addSuppressed(cleanupFailure)
      }
      throw IllegalStateException("Failed to start process engine workers", failure)
    }
  }

  private fun registrar() = ProcessEngineWorkerRegistrar(
    WorkerRuntimeOptions(config.completeTasksBeforeCommit(), config.removeTaskResultOnCompletion(), config.tenantId().orElse(null)),
    checkNotNull(subscriptions), required(ServiceTaskCompletionApi::class.java), required(VariableConverter::class.java),
    required(ParameterResolver::class.java), required(ResultResolver::class.java), JtaWorkerTransactionExecutor(transactions),
    required(ProcessEngineWorkerMetrics::class.java), required(IdempotencyRegistry::class.java)
  )

  private fun register(registrar: ProcessEngineWorkerRegistrar, definition: WorkerDefinition) {
    val beanClass = Class.forName(definition.beanClass(), false, Thread.currentThread().contextClassLoader)
    val method = WorkerMethods.resolve(beanClass, definition.methodDescriptor())
    val metadata = method.workerMetadata(beanClass)
    val worker = worker(definition.beanIdentifier())
    require(method.canAccess(worker) || method.trySetAccessible()) { "Cannot access managed worker method $method" }
    val coordinator = CdiDeliveryCoordinator(definition, method, transactions, checkNotNull(deliveries))
    registrar.register(method, checkNotNull(metadata), coordinator) { arguments ->
      coordinator.invokeReference { method.invoke(worker, *arguments) }
    }
  }

  private fun worker(identifier: String): Any {
    val bean = Arc.container().bean<Any>(identifier)
      ?: error("Discovered worker CDI bean is unavailable: $identifier")
    val handle = Arc.container().instance(bean)
    check(handle.isAvailable) { "Discovered worker CDI bean is unavailable: $identifier" }
    own(handle)
    return handle.get()
  }

  private fun <T> required(type: Class<T>): T {
    val handle = try {
      Arc.container().instance(type)
    } catch (failure: AmbiguousResolutionException) {
      throw IllegalStateException("Active process engine workers require exactly one CDI bean of type ${type.name}", failure)
    }
    check(handle.isAvailable) { "Active process engine workers require exactly one CDI bean of type ${type.name}" }
    own(handle)
    return handle.get()
  }

  fun shutdown(@Observes event: ShutdownEvent) = stop()

  private fun stop() {
    val (state, initialize) = synchronized(this) {
      val previous = stopping
      if (previous != null) previous to false else {
        val created = StopState(handles.toList(), deliveries)
        handles.clear()
        stopping = created
        created to true
      }
    }
    if (initialize) initializeStop(state)
    // Synchronous unsubscribe and destruction callbacks must not wait for their own stack to finish.
    if (state.lifecycle?.inDelivery() == true || state.initializing === Thread.currentThread() || destroying.get() == true) return
    state.drained.join()
    // Never wait for caller-owned transactions; retain references until their eventual outcome instead.
    if (state.released.isDone) state.cleanup.join()
  }

  private fun initializeStop(state: StopState) {
    state.drained.handle { _, failure -> failure }.thenCombine(state.released) { failure, _ ->
      destroying.set(true)
      try {
        destroy(state.owned, failure)
      } finally {
        destroying.remove()
      }
    }.whenComplete { _, failure ->
      if (failure == null) state.cleanup.complete(null) else {
        state.cleanup.completeExceptionally(failure)
        System.getLogger(WorkerRuntime::class.java.name)
          .log(System.Logger.Level.ERROR, "Failed to release worker resources", failure)
      }
    }
    try {
      val drained = subscriptions?.closeAsync() ?: state.lifecycle?.closeAsync() ?: CompletableFuture.completedFuture<Void>(null)
      drained.whenComplete { _, failure ->
        if (failure == null) state.drained.complete(null) else state.drained.completeExceptionally(failure)
      }
    } catch (failure: Throwable) {
      // Even a synchronous close failure must close admission and settle the published result after real deliveries drain.
      val drained = state.lifecycle?.closeAsync() ?: CompletableFuture.completedFuture<Void>(null)
      drained.whenComplete { _, _ -> state.drained.completeExceptionally(failure) }
    } finally {
      state.initializing = null
    }
  }

  private fun own(handle: InstanceHandle<*>) {
    synchronized(this) {
      if (stopping == null) {
        handles.add(handle)
        return
      }
    }
    if (handle.bean?.scope == Dependent::class.java) handle.destroy()
    error("Worker runtime is stopping")
  }

  private fun destroy(owned: List<InstanceHandle<*>>, failure: Throwable?) {
    var problem = failure
    owned.forEach { handle ->
      try {
        if (handle.bean?.scope == Dependent::class.java) handle.destroy()
      } catch (destructionFailure: RuntimeException) {
        val previous = problem
        if (previous == null) problem = destructionFailure else previous.addSuppressed(destructionFailure)
      }
    }
    problem?.let { throw CompletionException(it) }
  }
}
