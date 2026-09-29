package dev.bpmcrafters.processengine.worker.quarkus.deployment.support;

import dev.bpmcrafters.processengineapi.Empty;
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd;
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd;
import dev.bpmcrafters.processengineapi.task.FailTaskCmd;
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Completion API for tests recording all commands.
 */
public class RecordingServiceTaskCompletionApi implements ServiceTaskCompletionApi {

  private final List<CompleteTaskCmd> completed = new CopyOnWriteArrayList<>();
  private final List<CompleteTaskByErrorCmd> completedByError = new CopyOnWriteArrayList<>();
  private final List<FailTaskCmd> failed = new CopyOnWriteArrayList<>();
  private volatile Runnable listener = () -> {
  };

  @Override
  public CompletableFuture<Empty> completeTask(CompleteTaskCmd cmd) {
    listener.run();
    completed.add(cmd);
    return CompletableFuture.completedFuture(Empty.INSTANCE);
  }

  @Override
  public CompletableFuture<Empty> completeTaskByError(CompleteTaskByErrorCmd cmd) {
    listener.run();
    completedByError.add(cmd);
    return CompletableFuture.completedFuture(Empty.INSTANCE);
  }

  @Override
  public CompletableFuture<Empty> failTask(FailTaskCmd cmd) {
    listener.run();
    failed.add(cmd);
    return CompletableFuture.completedFuture(Empty.INSTANCE);
  }

  public List<CompleteTaskCmd> getCompleted() {
    return completed;
  }

  public List<CompleteTaskByErrorCmd> getCompletedByError() {
    return completedByError;
  }

  public List<FailTaskCmd> getFailed() {
    return failed;
  }

  /**
   * Sets a listener invoked on every call (e.g. to probe the transaction state at completion time).
   */
  public void setListener(Runnable listener) {
    this.listener = listener;
  }

  public void reset() {
    completed.clear();
    completedByError.clear();
    failed.clear();
    listener = () -> {
    };
  }
}
