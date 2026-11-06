package dev.bpmcrafters.example.quarkus

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengineapi.adapter.c8.deploy.DeploymentApiImpl
import dev.bpmcrafters.processengineapi.adapter.c8.process.StartProcessApiImpl
import dev.bpmcrafters.processengineapi.adapter.c8.task.completion.*
import dev.bpmcrafters.processengineapi.adapter.c8.task.delivery.*
import dev.bpmcrafters.processengineapi.adapter.c8.task.subscription.C8TaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository
import dev.bpmcrafters.processengineapi.task.*
import io.camunda.client.CamundaClient
import io.camunda.client.impl.CamundaObjectMapper
import io.camunda.client.impl.NoopCredentialsProvider
import io.quarkus.runtime.StartupEvent
import io.quarkus.runtime.ShutdownEvent
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Disposes
import jakarta.enterprise.inject.Instance
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Application-owned wiring only; all engine operations belong to the published adapter. */
@ApplicationScoped
class CamundaBridge {
  private var ownedClient: CamundaClient? = null
  private val closed = AtomicBoolean()

  @Produces @Singleton
  fun client(mapper: ObjectMapper,
    @ConfigProperty(name = "example.camunda.grpc-address") grpc: String,
    @ConfigProperty(name = "example.camunda.rest-address") rest: String
  ): CamundaClient = CamundaClient.newClientBuilder()
    .applyEnvironmentVariableOverrides(false)
    .credentialsProvider(NoopCredentialsProvider())
    .grpcAddress(URI.create(grpc)).restAddress(URI.create(rest)).preferRestOverGrpc(false)
    .withJsonMapper(CamundaObjectMapper(mapper.copy())).numJobWorkerExecutionThreads(4).build()
    .let(::own)

  @Produces @Singleton fun repository() = InMemSubscriptionRepository()
  @Produces @Singleton fun serviceDelivery(client: CamundaClient, repository: InMemSubscriptionRepository,
    @ConfigProperty(name = "example.inventory.replenishment-seconds", defaultValue = "60") delay: Long
  ) = SubscribingServiceTaskDelivery(client, repository, "quarkus-order-fulfillment", 10, maxOf(120, delay + 60))
  @Produces @Singleton fun userDelivery(client: CamundaClient, repository: InMemSubscriptionRepository) =
    SubscribingRefreshingZeebeJobUserTaskDelivery(client, repository, "quarkus-order-notifications", 60_000)
  @Produces @ApplicationScoped fun subscriptions(repository: InMemSubscriptionRepository, delivery: SubscribingRefreshingZeebeJobUserTaskDelivery): TaskSubscriptionApi =
    C8TaskSubscriptionApiImpl(repository, delivery)
  @Produces @ApplicationScoped fun serviceCompletion(client: CamundaClient, repository: InMemSubscriptionRepository): ServiceTaskCompletionApi =
    C8ExternalServiceTaskCompletionApiImpl(client, repository, LinearMemoryFailureRetrySupplier(3, 10))
  @Produces @ApplicationScoped fun userCompletion(client: CamundaClient, repository: InMemSubscriptionRepository): UserTaskCompletionApi =
    C8CamundaClientUserTaskJobCompletionApiImpl(client, repository)
  @Produces @ApplicationScoped fun startProcess(client: CamundaClient): StartProcessApi = StartProcessApiImpl(client)
  @Produces @ApplicationScoped fun deployment(client: CamundaClient): DeploymentApi = DeploymentApiImpl(client)

  fun closeAfterDrain(@Observes @Priority(3000) event: ShutdownEvent) = closeOwned()
  fun dispose(@Disposes client: CamundaClient) = closeOwned()
  internal fun own(client: CamundaClient): CamundaClient {
    check(ownedClient == null) { "The application owns exactly one Camunda client" }
    ownedClient = client
    return client
  }
  internal fun closeOwned() {
    val client = ownedClient ?: return // Never resolve an unused producer during disabled shutdown.
    if (closed.compareAndSet(false, true)) client.close()
  }
}

@ApplicationScoped
class CamundaDeliveryLifecycle(
  @ConfigProperty(name = "example.camunda.startup-enabled", defaultValue = "true") private val enabled: Boolean,
  private val pool: NotificationPool,
  private val subscriptions: Instance<TaskSubscriptionApi>,
  private val services: Instance<SubscribingServiceTaskDelivery>,
  private val users: Instance<SubscribingRefreshingZeebeJobUserTaskDelivery>
) {
  private var refresh: ScheduledExecutorService? = null
  private var startedUsers: SubscribingRefreshingZeebeJobUserTaskDelivery? = null
  private var subscription: TaskSubscription? = null

  fun register(@Observes @Priority(2400) event: StartupEvent) {
    if (!enabled) return
    subscription = subscriptions.get().subscribeForTask(pool.command()).get()
  }

  // Published delivery takes a snapshot: extension registration at priority 2500 must finish first.
  fun activate(@Observes @Priority(3000) event: StartupEvent) {
    if (!enabled) return
    services.get().subscribe()
    val delivery = users.get()
    startedUsers = delivery
    delivery.subscribe()
    refresh = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "order-user-task-refresh").apply { isDaemon = true } }
      .also { it.scheduleWithFixedDelay({ refreshSafely { delivery.refresh() } }, 5, 5, TimeUnit.SECONDS) }
  }

  fun stop(@Observes @Priority(2000) event: ShutdownEvent) {
    refresh?.shutdownNow()
    refresh?.awaitTermination(10, TimeUnit.SECONDS)
    try { startedUsers?.unsubscribeAll() } finally {
      subscription?.let { subscriptions.get().unsubscribe(UnsubscribeFromTaskCmd(it)).get() }
      subscription = null
    }
  }

  companion object {
    internal fun refreshSafely(action: () -> Unit) {
      try { action() } catch (exception: Exception) {
        Logger.getLogger(CamundaDeliveryLifecycle::class.java).warn("User task refresh failed; retrying next cycle", exception)
      }
    }
  }
}
