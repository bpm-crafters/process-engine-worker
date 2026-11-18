package dev.bpmcrafters.processengine.worker.registrar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectionFacadeLinkageTest {
    @TempDir Path directory;

    @Test
    void precompiledLegacyClientLinksEveryOriginalStaticHelper() throws Exception {
        final var stub = compile("stub", "ReflectionUtilsKt", LEGACY_STUB, classPath());
        final var client = compile("client", "LegacyClient", CLIENT, stub + java.io.File.pathSeparator + classPath());
        invokeClient(client);
    }

    @Test
    void javaStaticImportsCompileAndLinkAgainstActualFacade() throws Exception {
        invokeClient(compile("source-client", "LegacyClient", CLIENT, classPath()));
    }

    private Path compile(final String folder, final String name, final String source, final String classPath) throws Exception {
        final var output = Files.createDirectory(directory.resolve(folder));
        final var file = output.resolve(name + ".java");
        Files.writeString(file, source);
        try (final var diagnostics = new ByteArrayOutputStream()) {
            final var compiler = ToolProvider.getSystemJavaCompiler();
            assertThat(compiler).as("Tests require a JDK").isNotNull();
            final var status = compiler.run(null, diagnostics, diagnostics,
                    "--release", "17", "-classpath", classPath, "-d", output.toString(), file.toString());
            assertThat(status).as(diagnostics.toString()).isZero();
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    private void invokeClient(final Path classes) throws Exception {
        // Parent-first loading links to the real library. The legacy stub directory is never on this loader's path.
        try (final var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            final var values = (List<Boolean>) loader.loadClass("LegacyClient").getMethod("run").invoke(null);
            assertThat(values).hasSize(21).containsOnly(true);
        }
    }

    private static String classPath() { return System.getProperty("java.class.path"); }

    private static final String LEGACY_STUB = """
            package dev.bpmcrafters.processengine.worker.registrar;
            import java.lang.reflect.*;
            import java.util.*;
            import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Completion;
            public final class ReflectionUtilsKt {
              public static boolean isPayload(Parameter p) { return false; }
              public static boolean isTaskInformation(Parameter p) { return false; }
              public static boolean isVariableConverter(Parameter p) { return false; }
              public static boolean isTaskCompletionApiParameter(Parameter p) { return false; }
              public static boolean isVariable(Parameter p) { return false; }
              public static String extractVariableName(Parameter p) { return null; }
              public static VariableConverter extractVariableConverter(Parameter p, VariableConverter c) { return null; }
              public static boolean extractVariableMandatoryFlag(Parameter p) { return false; }
              public static boolean isOptional(Parameter p) { return false; }
              public static Set<String> extractVariableNames(List<Parameter> p) { return null; }
              public static boolean hasPayloadReturnType(Method m) { return false; }
              public static boolean hasVoidReturnType(Method m) { return false; }
              public static List<Method> getAnnotatedWorkers(Object o) { return null; }
              public static String getTopic(Method m) { return null; }
              public static boolean getAutoComplete(Method m) { return false; }
              public static Completion getCompletion(Method m) { return null; }
              public static Long getLockDuration(Method m) { return null; }
              public static String getTenantId(Method m) { return null; }
              public static boolean isTransactional(Method m) { return false; }
              public static Method workerMetadata(Method m) { return null; }
            }
            """;

    private static final String CLIENT = """
            import static dev.bpmcrafters.processengine.worker.registrar.ReflectionUtilsKt.*;
            import dev.bpmcrafters.processengine.worker.*;
            import dev.bpmcrafters.processengine.worker.registrar.*;
            import dev.bpmcrafters.processengineapi.task.*;
            import org.springframework.transaction.annotation.Transactional;
            import java.util.*;
            public class LegacyClient {
              public static List<Boolean> run() throws Exception {
                final var method = Worker.class.getMethod("work", String.class, Map.class, Optional.class,
                    TaskInformation.class, VariableConverter.class, ServiceTaskCompletionApi.class);
                final var p = method.getParameters();
                final VariableConverter converter = new VariableConverter() {
                  public <T> T mapToType(Object value, Class<T> type) { return type.cast(value); }
                };
                return List.of(isPayload(p[1]), isTaskInformation(p[3]), isVariableConverter(p[4]),
                    isTaskCompletionApiParameter(p[5]), isVariable(p[0]), extractVariableName(p[0]).equals("value"),
                    extractVariableConverter(p[0], converter) == converter, !extractVariableMandatoryFlag(p[0]),
                    isOptional(p[2]), extractVariableNames(List.of(p[0])).equals(Set.of("value")),
                    hasPayloadReturnType(method), hasVoidReturnType(Worker.class.getMethod("nothing")),
                    getAnnotatedWorkers(new Worker()).size() == 1, getTopic(method).equals("legacy"),
                    getAutoComplete(method), getCompletion(method) == ProcessEngineWorker.Completion.DEFAULT,
                    getLockDuration(method).equals(7L), getTenantId(method).equals("tenant"), isTransactional(method),
                    SpringReflectionUtilsKt.isTransactional(method), workerMetadata(method).equals(method));
              }
              public static class Worker {
                @Transactional @ProcessEngineWorker(topic="legacy", lockDuration=7, tenantId="tenant")
                public Map<String,Object> work(@Variable(value="value",mandatory=false) String value,
                    Map<String,Object> payload, Optional<String> optional, TaskInformation task,
                    VariableConverter converter, ServiceTaskCompletionApi completion) { return Map.of(); }
                public void nothing() {}
              }
            }
            """;
}
