package dev.bpmcrafters.processengine.worker.registrar

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.UnexpectedRollbackException
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

internal class ExceptionResolverTest {

    private val exceptionResolver = ExceptionResolver()

    @Test
    fun `should detect plain exception`() {
        val message = "This is a test exception"
        assertThat(exceptionResolver.getCause(IllegalArgumentException(message))).message().contains(message)
    }

    @Test
    fun `should detect plain exception inside a invocation target invocation exception`() {
        val message = "This is a test exception"
        assertThat(
            exceptionResolver.getCause(
                InvocationTargetException(
                    InvocationTargetException(
                        IllegalArgumentException(message)
                    )
                )
            )
        ).message().contains(message)
    }

    @Test
    fun `should detect plain exception inside a execution exception`() {
        val message = "This is a test exception"
        assertThat(
            exceptionResolver.getCause(
                ExecutionException(
                    ExecutionException(
                        IllegalArgumentException(message)
                    )
                )
            )
        ).message().contains(message)
    }

    @Test
    fun `should detect plain exception inside a execution and invocation target exception`() {
        val message = "This is a test exception"
        assertThat(
            exceptionResolver.getCause(
                ExecutionException(
                    InvocationTargetException(
                        IllegalArgumentException(message)
                    )
                )
            )
        ).message().contains(message)
    }

    @Test
    fun `should detect plain exception inside a transaction and invocation target exception`() {
        val message = "This is a test exception"
        assertThat(
            exceptionResolver.getCause(
                UnexpectedRollbackException(
                    "Transaction silently rolled back because it has been marked as rollback-only",
                    InvocationTargetException(
                        IllegalArgumentException(message)
                    )
                )
            )
        ).message().contains(message)
    }

}
