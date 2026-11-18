package dev.bpmcrafters.example.quarkus;

import dev.bpmcrafters.processengineapi.Empty;
import dev.bpmcrafters.processengineapi.deploy.*;
import dev.bpmcrafters.processengineapi.process.*;
import dev.bpmcrafters.processengineapi.task.*;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.mockito.Mockito.*;

/** Test-only API alternatives; the production graph contains no offline engine. */
@Alternative
@Priority(1)
@ApplicationScoped
public class TestApis {
  private final List<StartProcessByMessageCmd> starts = new CopyOnWriteArrayList<>();
  private final List<RecordedDeployment> deployments = new CopyOnWriteArrayList<>();
  private final List<CompleteTaskCmd> completions = new CopyOnWriteArrayList<>();

  public List<StartProcessByMessageCmd> starts() { return List.copyOf(starts); }
  public List<RecordedDeployment> deployments() { return List.copyOf(deployments); }
  public List<CompleteTaskCmd> completions() { return List.copyOf(completions); }

  public record RecordedResource(String name, byte[] bytes) { }
  public record RecordedDeployment(List<RecordedResource> resources, String tenantId) { }

  public void reset() { starts.clear(); deployments.clear(); completions.clear(); }

  @Produces
  @Alternative
  @Priority(1)
  @ApplicationScoped
  public StartProcessApi start() {
    final var api = mock(StartProcessApi.class);
    when(api.startProcess(any())).thenAnswer(call -> {
      starts.add(call.getArgument(0));
      return CompletableFuture.completedFuture(new ProcessInformation("", Map.of()));
    });
    return api;
  }

  @Produces
  @Alternative
  @Priority(1)
  @ApplicationScoped
  public DeploymentApi deployment() {
    final var api = mock(DeploymentApi.class);
    when(api.deploy(any())).thenAnswer(call -> {
      final DeployBundleCommand command = call.getArgument(0);
      final var resources = new java.util.ArrayList<RecordedResource>();
      for (final NamedResource resource : command.getResources()) {
        resources.add(new RecordedResource(resource.getName(), resource.getResourceStream().readAllBytes()));
      }
      deployments.add(new RecordedDeployment(List.copyOf(resources), command.getTenantId()));
      return CompletableFuture.completedFuture(new DeploymentInformation("test-deployment", null, null));
    });
    return api;
  }

  @Produces
  @Alternative
  @Priority(1)
  @ApplicationScoped
  public UserTaskCompletionApi completion() {
    final var api = mock(UserTaskCompletionApi.class);
    when(api.completeTask(any())).thenAnswer(call -> {
      completions.add(call.getArgument(0));
      return CompletableFuture.completedFuture(Empty.INSTANCE);
    });
    return api;
  }
}
