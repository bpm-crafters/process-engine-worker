package dev.bpmcrafters.processengine.worker.registrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred;
import dev.bpmcrafters.processengine.worker.FailJobException;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import dev.bpmcrafters.processengine.worker.idempotency.LocalInMemoryIdempotencyRegistry;
import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.task.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CoreRuntimeTest {
    private final TaskSubscriptionApi subscriptions = mock(TaskSubscriptionApi.class);
    private final ServiceTaskCompletionApi completion = mock(ServiceTaskCompletionApi.class);
    private final LocalInMemoryIdempotencyRegistry registry = new LocalInMemoryIdempotencyRegistry();
    private final ProcessEngineWorkerMetrics metrics = mock(ProcessEngineWorkerMetrics.class);
    private final List<String> events = new ArrayList<>();
    private final TaskInformation task = new TaskInformation("task", Map.of(
            CommonRestrictions.PROCESS_INSTANCE_ID, "process", TaskInformation.RETRIES, "3"));

    @BeforeEach
    void setUp() {
        when(subscriptions.subscribeForTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(completion.completeTask(any())).thenAnswer(invocation -> {
            events.add("complete");
            return CompletableFuture.completedFuture(null);
        });
        when(completion.failTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(completion.completeTaskByError(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void consumesJavaAliasesAndVariablesWithoutSpring() {
        final var runtime = new ProcessEngineWorkerRegistrar(subscriptions, completion,
                new JacksonVariableConverter(new ObjectMapper()));
        runtime.register(new JavaWorker());
        final var command = command();
        assertThat(command.getTaskDescriptionKey()).isEqualTo("java-topic");
        assertThat(command.getPayloadDescription()).containsExactly("input");
        assertThat(command.getRestrictions()).containsEntry(CommonRestrictions.TENANT_ID, "annotation-tenant")
                .containsEntry(CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS, "123");
        command.getAction().accept(task, Map.of("input", "hello"));
        final var completed = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(completion).completeTask(completed.capture());
        assertThat(completed.getValue().get()).containsEntry("output", "hello");
        assertThatThrownBy(() -> Class.forName("org.springframework.context.ApplicationContext"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void acceptsEqualAliasesAndDefaultsAndRejectsConflicts() throws Exception {
        assertThat(CoreReflectionUtilsKt.getTopic(Aliases.class.getMethod("equal", String.class))).isEqualTo("same");
        assertThat(CoreReflectionUtilsKt.extractVariableName(Aliases.class.getMethod("equal", String.class)
                .getParameters()[0])).isEqualTo("same");
        assertThat(CoreReflectionUtilsKt.getTopic(Aliases.class.getMethod("defaults", String.class))).isEqualTo("defaults");
        assertThat(CoreReflectionUtilsKt.extractVariableName(Aliases.class.getMethod("defaults", String.class)
                .getParameters()[0])).isEqualTo("argument");
        assertThatThrownBy(() -> runtime(false, false).register(new ConflictingTopic()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Conflicting annotation aliases");
        assertThatThrownBy(() -> runtime(false, false).register(new ConflictingVariable()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Conflicting annotation aliases");
    }

    @Test
    void instantiatesJavaVariableConverter() throws Exception {
        final var parameter = ConvertedWorker.class.getMethod("run", String.class).getParameters()[0];
        assertThat(CoreReflectionUtilsKt.extractVariableConverter(parameter, new JacksonVariableConverter(new ObjectMapper())))
                .isInstanceOf(JavaConverter.class);
        runtime(false, false).register(new ConvertedWorker());
        command().getAction().accept(task, Map.of("input", "hello"));
        final var completed = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(completion).completeTask(completed.capture());
        assertThat(completed.getValue().get()).containsEntry("output", "HELLO");
    }

    @Test
    void usesMetadataIndependentlyOfInvokerAndCachesResults() throws Exception {
        final var runtime = runtime(false, false);
        runtime.register(Modes.class.getMethod("automatic"), false, arguments -> {
            events.add("worker");
            return Map.of("result", "cached");
        });
        final var command = command();
        command.getAction().accept(task, Map.of());
        command.getAction().accept(task, Map.of());
        assertThat(events).containsExactly("worker", "complete", "complete");
        assertThat(registry.getTaskResult(task)).containsEntry("result", "cached");
        assertThat(command.getRestrictions()).containsEntry(CommonRestrictions.TENANT_ID, "default-tenant");
        assertThat(command.getPayloadDescription()).isNull();
        registry.removeAllTaskResults("process");
        assertThat(registry.getTaskResult(task)).isNull();
        verify(metrics, times(2)).taskReceived("automatic");
        verify(metrics, times(2)).taskCompleted("automatic");
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void removesCachedResultsAfterSuccessfulCompletion() throws Exception {
        runtime(false, true).register(Modes.class.getMethod("automatic"), false, arguments -> Map.of());
        command().getAction().accept(task, Map.of());
        assertThat(registry.getTaskResult(task)).isNull();
    }

    @Test
    void leavesCompletionToManualWorker() throws Exception {
        runtime(false, false).register(Modes.class.getMethod("manual"), false, arguments -> {
            events.add("worker");
            return null;
        });
        command().getAction().accept(task, Map.of());
        assertThat(events).containsExactly("worker");
        verifyNoInteractions(completion);
    }

    @Test
    void completesBeforeCommitWhenExplicitlyRequested() throws Exception {
        assertOrdering("before", false, "begin", "worker", "complete", "commit");
    }

    @Test
    void completesAfterCommitWhenExplicitlyRequested() throws Exception {
        assertOrdering("after", true, "begin", "worker", "commit", "complete");
    }

    @Test
    void usesConfiguredDefaultCompletionOrder() throws Exception {
        assertOrdering("automatic", true, "begin", "worker", "complete", "commit");
    }

    @Test
    void defaultsToCompletingAfterCommit() throws Exception {
        assertOrdering("automatic", false, "begin", "worker", "commit", "complete");
    }

    @Test
    void manualTransactionalWorkerNeverAutoCompletes() throws Exception {
        assertOrdering("manual", true, "begin", "worker", "commit");
        verifyNoInteractions(completion);
    }

    @Test
    void completionFailureRetainsResultAndRetrySkipsWorker() throws Exception {
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")))
                .doReturn(CompletableFuture.completedFuture(null)).when(completion).completeTask(any());
        runtime(false, true).register(Modes.class.getMethod("automatic"), false, arguments -> {
            events.add("worker");
            return Map.of("result", "retained");
        });
        final var command = command();
        command.getAction().accept(task, Map.of());
        assertThat(registry.getTaskResult(task)).containsEntry("result", "retained");
        command.getAction().accept(task, Map.of());
        assertThat(events).containsExactly("worker");
        assertThat(registry.getTaskResult(task)).isNull();
        verify(metrics, times(2)).taskReceived("automatic");
        verify(metrics).taskFailed("automatic");
        verify(metrics).taskCompleted("automatic");
        verifyNoMoreInteractions(metrics);
        final var failed = ArgumentCaptor.forClass(FailTaskCmd.class);
        verify(completion).failTask(failed.capture());
        assertThat(failed.getValue().getRetryCount()).isEqualTo(2);
        assertThat(failed.getValue().getReason()).isEqualTo("offline");
    }

    @Test
    void reportsRetryFromWorkerFailure() throws Exception {
        runtime(false, false).register(Modes.class.getMethod("automatic"), false, arguments -> {
            throw new FailJobException("retry", null, 7, Duration.ofSeconds(2));
        });
        command().getAction().accept(task, Map.of());
        final var failed = ArgumentCaptor.forClass(FailTaskCmd.class);
        verify(completion).failTask(failed.capture());
        assertThat(failed.getValue().getRetryCount()).isEqualTo(7);
        assertThat(failed.getValue().getRetryBackoff()).isEqualTo(Duration.ofSeconds(2));
        assertThat(registry.getTaskResult(task)).isNull();
    }

    @Test
    void unwrapsReflectiveBpmnErrors() {
        runtime(false, false).register(new ErrorWorker());
        command().getAction().accept(task, Map.of());
        final var error = ArgumentCaptor.forClass(CompleteTaskByErrorCmd.class);
        verify(completion).completeTaskByError(error.capture());
        assertThat(error.getValue().getErrorCode()).isEqualTo("business-error");
        verify(completion, never()).failTask(any());
        verify(metrics).taskReceived("run");
        verify(metrics).taskCompletedByError("run");
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void transactionExecutorFailureBeforeInvocationFailsWithoutCompleting() throws Exception {
        runtime(action -> { throw new IllegalStateException("begin failed"); })
                .register(Modes.class.getMethod("automatic"), true, arguments -> {
                    fail("Worker must not run when transaction start fails");
                    return Map.of();
                });
        command().getAction().accept(task, Map.of());
        assertTransactionFailure("begin failed");
        assertThat(registry.getTaskResult(task)).isNull();
    }

    @Test
    void transactionExecutorCommitFailureDoesNotCompleteAfterCommit() throws Exception {
        runtime(action -> {
            action.get();
            throw new IllegalStateException("commit failed");
        }).register(Modes.class.getMethod("automatic"), true, arguments -> {
            events.add("worker");
            return Map.of();
        });
        command().getAction().accept(task, Map.of());
        assertThat(events).containsExactly("worker");
        assertTransactionFailure("commit failed");
    }

    @Test
    void workerFailureEscapesTransactionActionWithoutCommitOrCompletion() throws Exception {
        runtime(false, false).register(Modes.class.getMethod("automatic"), true, arguments -> {
            throw new IllegalStateException("worker failed");
        });
        command().getAction().accept(task, Map.of());
        assertThat(events).containsExactly("begin");
        assertThat(registry.getTaskResult(task)).isNull();
        assertTransactionFailure("worker failed");
    }

    @Test
    void reportsFallbackReasonAndHandlesFailureReportingError() throws Exception {
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("report failed")))
                .when(completion).failTask(any());
        final var failure = new IllegalStateException();
        runtime(false, false).register(Modes.class.getMethod("automatic"), false, arguments -> { throw failure; });
        command().getAction().accept(task, Map.of());
        assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("report failed");
        final var failed = ArgumentCaptor.forClass(FailTaskCmd.class);
        verify(completion).failTask(failed.capture());
        assertThat(failed.getValue().getReason()).isEqualTo("Exception during execution of external task worker");
        verify(metrics).taskReceived("automatic");
        verifyNoMoreInteractions(metrics);
    }

    private void assertTransactionFailure(final String reason) {
        final var failed = ArgumentCaptor.forClass(FailTaskCmd.class);
        verify(completion).failTask(failed.capture());
        assertThat(failed.getValue().getReason()).isEqualTo(reason);
        verify(completion, never()).completeTask(any());
        verify(metrics).taskReceived("automatic");
        verify(metrics).taskFailed("automatic");
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void unsuccessfulBpmnErrorReportingDoesNotEmitCompletionMetrics() throws Exception {
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("error report failed")))
                .when(completion).completeTaskByError(any());
        final var error = new BpmnErrorOccurred("business", "code", Map.of());
        runtime(false, false).register(Modes.class.getMethod("automatic"), false, arguments -> { throw error; });
        command().getAction().accept(task, Map.of());
        assertThat(error.getSuppressed()).extracting(Throwable::getMessage).containsExactly("error report failed");
        verify(metrics).taskReceived("automatic");
        verifyNoMoreInteractions(metrics);
        verify(completion, never()).failTask(any());
    }

    @Test
    void simpleConstructorRejectsTransactionalExecutionWithoutExecutor() throws Exception {
        final var runtime = new ProcessEngineWorkerRegistrar(subscriptions, completion,
                new JacksonVariableConverter(new ObjectMapper()));
        runtime.register(Modes.class.getMethod("automatic"), true, arguments -> {
            fail("No transaction executor was configured");
            return Map.of();
        });
        command().getAction().accept(task, Map.of());
        final var failed = ArgumentCaptor.forClass(FailTaskCmd.class);
        verify(completion).failTask(failed.capture());
        assertThat(failed.getValue().getReason()).isEqualTo("No transaction executor configured");
        verify(completion, never()).completeTask(any());
    }

    @Test
    void unsupportedResultTypeCompletesWithEmptyPayload() {
        runtime(false, false).register(new NonPayloadWorker());
        command().getAction().accept(task, Map.of());
        final var completed = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(completion).completeTask(completed.capture());
        assertThat(completed.getValue().get()).isEmpty();
        verify(metrics).taskReceived("run");
        verify(metrics).taskCompleted("run");
        verifyNoMoreInteractions(metrics);
    }

    public static class NonPayloadWorker {
        @ProcessEngineWorker public String run() { return "not a payload"; }
    }

    private ProcessEngineWorkerRegistrar runtime(final WorkerTransactionExecutor executor) {
        return new ProcessEngineWorkerRegistrar(new WorkerRuntimeOptions(), subscriptions, completion,
                new JacksonVariableConverter(new ObjectMapper()), ParameterResolver.builder().build(),
                ResultResolver.builder().build(), executor, metrics, registry);
    }

    @Test
    void discoversInheritedPublicAndInterfaceWorkers() {
        runtime(false, false).register(new InheritedWorker());
        assertThat(command().getTaskDescriptionKey()).isEqualTo("inherited");
        reset(subscriptions);
        when(subscriptions.subscribeForTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        runtime(false, false).register(new InterfaceWorker());
        assertThat(command().getTaskDescriptionKey()).isEqualTo("interface");
        command().getAction().accept(task, Map.of("value", "interface-value"));
    }

    private void assertOrdering(final String method, final boolean beforeDefault, final String... expected) throws Exception {
        runtime(beforeDefault, false).register(Modes.class.getMethod(method), true, arguments -> {
            events.add("worker");
            return Map.of();
        });
        command().getAction().accept(task, Map.of());
        assertThat(events).containsExactly(expected);
    }

    private ProcessEngineWorkerRegistrar runtime(final boolean beforeDefault, final boolean cleanup) {
        return new ProcessEngineWorkerRegistrar(new WorkerRuntimeOptions(beforeDefault, cleanup, "default-tenant"),
                subscriptions, completion, new JacksonVariableConverter(new ObjectMapper()),
                ParameterResolver.builder().build(), ResultResolver.builder().build(), action -> {
                    events.add("begin");
                    final var result = action.get();
                    events.add("commit");
                    return result;
                }, metrics, registry);
    }

    private SubscribeForTaskCmd command() {
        final var captor = ArgumentCaptor.forClass(SubscribeForTaskCmd.class);
        verify(subscriptions, atLeastOnce()).subscribeForTask(captor.capture());
        return captor.getValue();
    }

    public static class JavaWorker {
        @ProcessEngineWorker(value = "java-topic", lockDuration = 123, tenantId = "annotation-tenant")
        public Map<String, Object> run(@Variable("input") final String input) {
            return Map.of("output", input);
        }
    }

    @Test
    void retainsConcreteCovariantResultSignature() {
        runtime(false, false).register(new CovariantWorker());
        final var command = command();
        verify(subscriptions, times(1)).subscribeForTask(any());
        command.getAction().accept(task, Map.of());
        final var completed = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(completion).completeTask(completed.capture());
        assertThat(completed.getValue().get()).containsEntry("result", "covariant");
    }

    @Test
    void discoveredConcreteMethodsRetainPublicAnnotationHelpers() {
        final var method = CoreReflectionUtilsKt.getAnnotatedWorkers(new CovariantWorker()).get(0);
        assertThat(method.getReturnType()).isEqualTo(Map.class);
        assertThat(CoreReflectionUtilsKt.getTopic(method)).isEqualTo("covariant");
        assertThat(CoreReflectionUtilsKt.getAutoComplete(method)).isTrue();
        assertThat(CoreReflectionUtilsKt.getCompletion(method)).isEqualTo(ProcessEngineWorker.Completion.DEFAULT);
        assertThat(CoreReflectionUtilsKt.getLockDuration(method)).isNull();
        assertThat(CoreReflectionUtilsKt.getTenantId(method)).isNull();
    }

    @Test
    void retainsGenericInterfaceAnnotationsAndConcreteParameterTypes() {
        runtime(false, false).register(new GenericWorker());
        final var command = command();
        verify(subscriptions, times(1)).subscribeForTask(any());
        assertThat(command.getPayloadDescription()).containsExactly("number");
        command.getAction().accept(task, Map.of("number", "42"));
        final var completed = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(completion).completeTask(completed.capture());
        assertThat(completed.getValue().get()).containsEntry("number", 42);
    }

    public interface CovariantContract {
        @ProcessEngineWorker("covariant") Object work();
    }
    public static class CovariantWorker implements CovariantContract {
        @Override public Map<String, Object> work() { return Map.of("result", "covariant"); }
    }
    public interface GenericContract<T, R> {
        @ProcessEngineWorker("generic") R work(@Variable("number") T number);
    }
    public interface SpecializedContract<T> extends GenericContract<T, Map<String, Object>> {}

    @Test
    void discoversSubclassInterfaceMetadataForInheritedImplementation() {
        runtime(false, false).register(new InheritedImplementationWorker());
        final var command = command();
        verify(subscriptions, times(1)).subscribeForTask(any());
        assertThat(command.getTaskDescriptionKey()).isEqualTo("generic");
        assertThat(command.getPayloadDescription()).containsExactly("number");
        command.getAction().accept(task, Map.of("number", "42"));
        final var completed = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(completion).completeTask(completed.capture());
        assertThat(completed.getValue().get()).containsEntry("number", 42);
    }

    public static class InheritedImplementationBase {
        public Map<String, Object> work(final Integer number) { return Map.of("number", number); }
        public Map<String, Object> work(final String unrelated) { throw new AssertionError("Unannotated overload"); }
    }
    public static class InheritedImplementationWorker extends InheritedImplementationBase implements SpecializedContract<Integer> {}
    public static class GenericWorker implements SpecializedContract<Integer> {
        @Override public Map<String, Object> work(final Integer number) { return Map.of("number", number); }
        public Map<String, Object> work(final String unrelated) { throw new AssertionError("Unannotated overload"); }
    }

    @Test
    void discoversGenericArrayWorkerWithoutAttributingMetadataToUnrelatedArrayOverload() {
        runtime(false, false).register(new GenericArrayWorker());
        final var command = command();
        verify(subscriptions, times(1)).subscribeForTask(any());
        assertThat(command.getPayloadDescription()).containsExactly("values");
        command.getAction().accept(task, Map.of("values", List.of("1", "2")));
        final var completed = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(completion).completeTask(completed.capture());
        assertThat(completed.getValue().get()).containsEntry("sum", 3);
    }

    public interface GenericArrayContract<T> {
        @ProcessEngineWorker("generic-array") Map<String, Object> work(@Variable("values") T[] values);
    }
    public static class GenericArrayWorker implements GenericArrayContract<Integer> {
        @Override public Map<String, Object> work(final Integer[] values) {
            assertThat(values).containsExactly(1, 2);
            return Map.of("sum", values[0] + values[1]);
        }
        public Map<String, Object> work(final String[] unrelated) { throw new AssertionError("Unannotated array overload"); }
    }

    public static class ConvertedWorker {
        @ProcessEngineWorker
        public Map<String, Object> run(@Variable(value = "input", converter = JavaConverter.class) final String input) {
            return Map.of("output", input);
        }
    }

    public static class JavaConverter implements VariableConverter {
        @Override public <T> T mapToType(final Object value, final Class<T> type) {
            return type.cast(value.toString().toUpperCase(java.util.Locale.ROOT));
        }
    }

    public static class Aliases {
        @ProcessEngineWorker(topic = "same", value = "same")
        public void equal(@Variable(name = "same", value = "same") final String argument) {}
        @ProcessEngineWorker
        public void defaults(@Variable final String argument) {}
    }

    public static class ConflictingTopic {
        @ProcessEngineWorker(topic = "one", value = "two")
        public void run() {}
    }

    public static class ConflictingVariable {
        @ProcessEngineWorker
        public void run(@Variable(name = "one", value = "two") final String argument) {}
    }

    public static class Modes {
        @ProcessEngineWorker public Map<String, Object> automatic() { return Map.of(); }
        @ProcessEngineWorker(autoComplete = false) public void manual() {}
        @ProcessEngineWorker(completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
        public void before() {}
        @ProcessEngineWorker(completion = ProcessEngineWorker.Completion.AFTER_COMMIT)
        public void after() {}
    }

    public static class ErrorWorker {
        @ProcessEngineWorker
        public void run() throws BpmnErrorOccurred {
            throw new BpmnErrorOccurred("business error", "business-error", Map.of());
        }
    }

    public static class ParentWorker {
        @ProcessEngineWorker("inherited") public void inherited() {}
    }
    public static class InheritedWorker extends ParentWorker {}
    public interface WorkerContract {
        @ProcessEngineWorker("interface") void run(@Variable("value") String value);
    }
    public static class InterfaceWorker implements WorkerContract {
        @Override public void run(final String value) { assertThat(value).isEqualTo("interface-value"); }
    }
}
