package dev.bpmcrafters.processengine.worker.quarkus

import jakarta.annotation.Priority
import jakarta.interceptor.AroundInvoke
import jakarta.interceptor.Interceptor
import jakarta.interceptor.InvocationContext

@Interceptor
@WorkerDeliveryBinding
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 201)
class WorkerDeliveryInterceptor {
  @AroundInvoke
  fun invoke(context: InvocationContext): Any? {
    val token = context.contextData[WorkerDeliveryEntryInterceptor.TOKEN] as CdiDeliveryCoordinator.Token?
    return if (token == null) context.proceed() else token.invoke(context)
  }
}
