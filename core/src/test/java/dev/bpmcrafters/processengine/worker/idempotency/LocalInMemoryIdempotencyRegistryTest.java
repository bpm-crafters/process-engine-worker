package dev.bpmcrafters.processengine.worker.idempotency;

import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class LocalInMemoryIdempotencyRegistryTest {
    @Test
    void cleanupOfUnknownProcessDoesNotRemoveOtherResults() {
        final var registry = new LocalInMemoryIdempotencyRegistry();
        final var task = task("task", "process");
        registry.register(task, Map.of("result", "retained"));
        registry.removeAllTaskResults("unknown");
        assertThat(registry.getTaskResult(task)).containsEntry("result", "retained");
    }

    @Test
    void processCleanupRemovesRemainingTasksAndPreservesOtherProcesses() {
        final var registry = new LocalInMemoryIdempotencyRegistry();
        final var first = task("first", "process");
        final var second = task("second", "process");
        final var other = task("other", "other-process");
        registry.register(first, Map.of());
        registry.register(second, Map.of());
        registry.register(other, Map.of("result", "retained"));
        registry.removeTaskResult(first.getTaskId());
        registry.removeAllTaskResults("process");
        registry.removeAllTaskResults("process");
        assertThat(registry.getTaskResult(first)).isNull();
        assertThat(registry.getTaskResult(second)).isNull();
        assertThat(registry.getTaskResult(other)).containsEntry("result", "retained");
    }

    private static TaskInformation task(final String taskId, final String processId) {
        return new TaskInformation(taskId, Map.of(CommonRestrictions.PROCESS_INSTANCE_ID, processId));
    }
}
