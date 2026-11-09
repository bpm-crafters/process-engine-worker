package dev.bpmcrafters.example.quarkus.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.processengineapi.adapter.c8.deploy.DeploymentApiImpl;
import dev.bpmcrafters.processengineapi.adapter.c8.process.StartProcessApiImpl;
import dev.bpmcrafters.processengineapi.adapter.c8.task.completion.*;
import dev.bpmcrafters.processengineapi.adapter.c8.task.delivery.SubscribingRefreshingZeebeJobUserTaskDelivery;
import dev.bpmcrafters.processengineapi.adapter.c8.task.delivery.SubscribingServiceTaskDelivery;
import dev.bpmcrafters.processengineapi.adapter.c8.task.subscription.C8TaskSubscriptionApiImpl;
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi;
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository;
import dev.bpmcrafters.processengineapi.process.StartProcessApi;
import dev.bpmcrafters.processengineapi.task.*;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Owns infrastructure, never engine protocol behavior. */
@ApplicationScoped
public class CamundaBridge {
  private static final int SERVICE_EXECUTION_THREADS = 4;
  private static final int SERVICE_WORKER_TYPES = 3;
  private static final long COMPLETION_MARGIN_SECONDS = 60;

  private static final class OwnedClient {
    private final CamundaClient client;
    private boolean closed;
    private OwnedClient(CamundaClient client) { this.client = client; }
  }

  private final List<OwnedClient> ownedClients = new ArrayList<>();

  @Produces
  @Singleton
  public CamundaClient client(ObjectMapper mapper,
      @ConfigProperty(name = "example.camunda.grpc-address") String grpc,
      @ConfigProperty(name = "example.camunda.rest-address") String rest) {
    return own(clientBuilder(mapper, grpc, rest, SERVICE_EXECUTION_THREADS)
      .defaultJobWorkerMaxJobsActive(SERVICE_EXECUTION_THREADS).build());
  }

  @Produces
  @Singleton
  @NotificationClient
  public CamundaClient notificationClient(ObjectMapper mapper,
      @ConfigProperty(name = "example.camunda.grpc-address") String grpc,
      @ConfigProperty(name = "example.camunda.rest-address") String rest) {
    return own(clientBuilder(mapper, grpc, rest, 1).build());
  }

  private CamundaClientBuilder clientBuilder(ObjectMapper mapper, String grpc, String rest, int threads) {
    return CamundaClient.newClientBuilder().applyEnvironmentVariableOverrides(false)
      .credentialsProvider(new NoopCredentialsProvider()).grpcAddress(URI.create(grpc)).restAddress(URI.create(rest))
      .preferRestOverGrpc(false).withJsonMapper(new CamundaObjectMapper(mapper.copy())).numJobWorkerExecutionThreads(threads);
  }

  @Produces
  @Singleton
  public InMemSubscriptionRepository repository() { return new InMemSubscriptionRepository(); }

  @Produces
  @Singleton
  public SubscribingServiceTaskDelivery serviceDelivery(CamundaClient client, InMemSubscriptionRepository repository,
      @ConfigProperty(name = "example.inventory.replenishment-seconds", defaultValue = "60") long delay) {
    return new SubscribingServiceTaskDelivery(client, repository, "quarkus-order-fulfillment", 10, serviceLeaseSeconds(delay));
  }

  private static long serviceLeaseSeconds(long delay) {
    if (delay < 0) throw new IllegalArgumentException("Inventory replenishment seconds must be nonnegative");
    // Each of the three topics can admit four jobs into the shared four-thread pool: three waves.
    final long executionSeconds = Math.multiplyExact(delay, SERVICE_WORKER_TYPES);
    final long leaseSeconds = Math.max(120, Math.addExact(executionSeconds, COMPLETION_MARGIN_SECONDS));
    // The published adapter converts seconds to milliseconds with unchecked multiplication.
    Math.multiplyExact(leaseSeconds, 1000L);
    return leaseSeconds;
  }

  @Produces
  @Singleton
  public SubscribingRefreshingZeebeJobUserTaskDelivery userDelivery(@NotificationClient CamundaClient client, InMemSubscriptionRepository repository) {
    return new SubscribingRefreshingZeebeJobUserTaskDelivery(client, repository, "quarkus-order-notifications", 60_000);
  }

  @Produces
  @ApplicationScoped
  public TaskSubscriptionApi subscriptions(InMemSubscriptionRepository repository, SubscribingRefreshingZeebeJobUserTaskDelivery delivery) {
    return new C8TaskSubscriptionApiImpl(repository, delivery);
  }

  @Produces
  @ApplicationScoped
  public ServiceTaskCompletionApi serviceCompletion(CamundaClient client, InMemSubscriptionRepository repository) {
    return new C8ExternalServiceTaskCompletionApiImpl(client, repository, new LinearMemoryFailureRetrySupplier(3, 10));
  }

  @Produces
  @ApplicationScoped
  public UserTaskCompletionApi userCompletion(@NotificationClient CamundaClient client, InMemSubscriptionRepository repository) {
    return new C8CamundaClientUserTaskJobCompletionApiImpl(client, repository);
  }

  @Produces
  @ApplicationScoped
  public StartProcessApi startProcess(CamundaClient client) { return new StartProcessApiImpl(client); }

  @Produces
  @ApplicationScoped
  public DeploymentApi deployment(CamundaClient client) { return new DeploymentApiImpl(client); }

  public void closeAfterDrain(@Observes @Priority(3000) ShutdownEvent event) { closeOwned(); }
  public void dispose(@Disposes CamundaClient client) { closeOwned(); }
  public void disposeNotifications(@Disposes @NotificationClient CamundaClient client) { closeOwned(); }

  public synchronized CamundaClient own(CamundaClient client) {
    if (ownedClients.stream().noneMatch(owned -> owned.client == client)) ownedClients.add(new OwnedClient(client));
    return client;
  }

  public synchronized void closeOwned() {
    Throwable failure = null;
    for (final OwnedClient owned : ownedClients) {
      if (owned.closed) continue;
      owned.closed = true;
      try {
        owned.client.close();
      } catch (Throwable exception) {
        if (failure == null) failure = exception;
        else if (failure != exception) failure.addSuppressed(exception);
      }
    }
    if (failure instanceof Error error) throw error;
    if (failure instanceof RuntimeException exception) throw exception;
    if (failure != null) throw new IllegalStateException("Client close failed", failure);
  }
}
