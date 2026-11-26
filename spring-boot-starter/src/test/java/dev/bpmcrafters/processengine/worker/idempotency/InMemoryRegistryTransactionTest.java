package dev.bpmcrafters.processengine.worker.idempotency;

import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRegistryTransactionTest {
    private final InMemoryIdempotencyRegistry registry = new InMemoryIdempotencyRegistry();
    private final TaskInformation task = new TaskInformation("task", Map.of(CommonRestrictions.PROCESS_INSTANCE_ID, "process"));
    private final TransactionTemplate transaction = new TransactionTemplate(new LocalTransactionManager());

    @Test
    void publishesOnlyAfterCommit() {
        transaction.executeWithoutResult(status -> {
            registry.register(task, Map.of("value", "committed"));
            assertThat(registry.getTaskResult(task)).isNull();
        });
        assertThat(registry.getTaskResult(task)).containsEntry("value", "committed");
    }

    @Test
    void discardsResultsOnRollback() {
        transaction.executeWithoutResult(status -> {
            registry.register(task, Map.of("value", "rolled back"));
            status.setRollbackOnly();
        });
        assertThat(registry.getTaskResult(task)).isNull();
    }

    @Test
    void publishesImmediatelyOutsideTransaction() {
        registry.register(task, Map.of("value", "immediate"));
        assertThat(registry.getTaskResult(task)).containsEntry("value", "immediate");
    }

    private static class LocalTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(final Object transaction, final TransactionDefinition definition) {}
        @Override protected void doCommit(final DefaultTransactionStatus status) {}
        @Override protected void doRollback(final DefaultTransactionStatus status) {}
    }
}
