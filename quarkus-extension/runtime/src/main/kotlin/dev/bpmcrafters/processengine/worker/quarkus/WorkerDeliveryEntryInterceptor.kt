package dev.bpmcrafters.processengine.worker.quarkus

import io.quarkus.arc.InjectableBean
import jakarta.annotation.Priority
import jakarta.enterprise.inject.Intercepted
import jakarta.enterprise.inject.spi.Bean
import jakarta.inject.Inject
import jakarta.interceptor.AroundInvoke
import jakarta.interceptor.Interceptor
import jakarta.interceptor.InvocationContext

/** Claims the exact invocation before user interceptors can make ordinary reentrant calls. */
@Interceptor
@WorkerDeliveryBinding
@Priority(Int.MIN_VALUE)
class WorkerDeliveryEntryInterceptor {
  @Inject
  @Intercepted
  lateinit var bean: Bean<*>

  @AroundInvoke
  fun invoke(context: InvocationContext): Any? {
    val token = CdiDeliveryCoordinator.claim((bean as InjectableBean<*>).identifier, context.method)
      ?: return context.proceed()
    context.contextData[TOKEN] = token
    try {
      return context.proceed()
    } finally {
      context.contextData.remove(TOKEN)
    }
  }

  companion object {
    val TOKEN = WorkerDeliveryEntryInterceptor::class.java.name + ".token"
  }
}
