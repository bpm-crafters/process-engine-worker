package dev.bpmcrafters.processengine.worker.registrar;

import org.junit.jupiter.api.Test;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ReflectionSignatureTest {
    @Test
    void resolvesNestedGenericArrayComponentsWithoutMatchingUnrelatedArrayOverloads() throws Exception {
        assertThat(CoreReflectionUtilsKt.getAnnotatedWorkers(new NestedArrayWorker())).containsExactly(
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
        assertThat(CoreReflectionUtilsKt.hasPayloadReturnType(Signatures.class.getMethod("unbounded", Map.class))).isTrue();
        assertThat(CoreReflectionUtilsKt.isPayload(Signatures.class.getMethod("unbounded", Map.class).getParameters()[0])).isTrue();
        assertThat(CoreReflectionUtilsKt.hasPayloadReturnType(Signatures.class.getMethod("bounded", Map.class))).isFalse();
        assertThat(CoreReflectionUtilsKt.isPayload(Signatures.class.getMethod("bounded", Map.class).getParameters()[0])).isFalse();
        assertThat(CoreReflectionUtilsKt.hasPayloadReturnType(Signatures.class.getMethod("wrongKey"))).isFalse();
        assertThat(CoreReflectionUtilsKt.hasPayloadReturnType(Signatures.class.getMethod("wrongValue"))).isFalse();
    }

    @Test
    void rejectsAnnotationLookupForUnannotatedMethodClearly() throws Exception {
        final var method = Signatures.class.getMethod("wrongKey");
        assertThatThrownBy(() -> CoreReflectionUtilsKt.getTopic(method)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No @ProcessEngineWorker metadata");
    }

    interface Signatures {
        Map<String, ?> unbounded(Map<String, ?> payload);
        Map<String, ? extends Number> bounded(Map<String, ? extends Number> payload);
        Map<Integer, Object> wrongKey();
        Map<String, String> wrongValue();
    }
}
