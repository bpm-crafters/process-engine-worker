package dev.bpmcrafters.processengine.worker.registrar;

import org.junit.jupiter.api.Test;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ReflectionSignatureTest {
    @Test
    void resolvesNestedGenericArrayComponentsWithoutMatchingUnrelatedArrayOverloads() throws Exception {
        assertThat(ReflectionUtilsKt.getAnnotatedWorkers(new NestedArrayWorker())).containsExactly(
                NestedArrayWorker.class.getMethod("work", Integer[][].class));
    }

    interface NestedArrayContract<T> {
        @ProcessEngineWorker void work(T[][] values);
    }
    static class NestedArrayWorker implements NestedArrayContract<Integer> {
        @Override public void work(final Integer[][] values) {}
        public void work(final String[][] unrelated) { throw new AssertionError("Unannotated array overload"); }
    }

    @Test
    void acceptsUnboundedButNotBoundedOrIncompatiblePayloadSignatures() throws Exception {
        assertThat(ReflectionUtilsKt.hasPayloadReturnType(Signatures.class.getMethod("unbounded", Map.class))).isTrue();
        assertThat(ReflectionUtilsKt.isPayload(Signatures.class.getMethod("unbounded", Map.class).getParameters()[0])).isTrue();
        assertThat(ReflectionUtilsKt.hasPayloadReturnType(Signatures.class.getMethod("bounded", Map.class))).isFalse();
        assertThat(ReflectionUtilsKt.isPayload(Signatures.class.getMethod("bounded", Map.class).getParameters()[0])).isFalse();
        assertThat(ReflectionUtilsKt.hasPayloadReturnType(Signatures.class.getMethod("wrongKey"))).isFalse();
        assertThat(ReflectionUtilsKt.hasPayloadReturnType(Signatures.class.getMethod("wrongValue"))).isFalse();
    }

    @Test
    void rejectsAnnotationLookupForUnannotatedMethodClearly() throws Exception {
        final var method = Signatures.class.getMethod("wrongKey");
        assertThatThrownBy(() -> ReflectionUtilsKt.getTopic(method)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No @ProcessEngineWorker metadata");
    }

    interface Signatures {
        Map<String, ?> unbounded(Map<String, ?> payload);
        Map<String, ? extends Number> bounded(Map<String, ? extends Number> payload);
        Map<Integer, Object> wrongKey();
        Map<String, String> wrongValue();
    }
}
