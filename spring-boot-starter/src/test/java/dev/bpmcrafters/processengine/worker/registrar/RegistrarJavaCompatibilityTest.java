package dev.bpmcrafters.processengine.worker.registrar;

import dev.bpmcrafters.processengineapi.task.TaskInformation;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class RegistrarJavaCompatibilityTest {
    @Test
    void legacyNestedTypesRemainUsableFromJava() {
        final ProcessEngineStarterRegistrar.TaskHandlerWithResult handler = (task, payload) -> payload.get(task.getTaskId());
        assertThat(handler.invoke(new TaskInformation("task", Map.of()), Map.of("task", "result"))).isEqualTo("result");
        final var retry = new ProcessEngineStarterRegistrar.FailureRetry(3, Duration.ofSeconds(1));
        assertThat(retry.getRetryCount()).isEqualTo(3);
        assertThat(retry.copy(2, retry.getRetryBackoff()).component1()).isEqualTo(2);
    }
}
