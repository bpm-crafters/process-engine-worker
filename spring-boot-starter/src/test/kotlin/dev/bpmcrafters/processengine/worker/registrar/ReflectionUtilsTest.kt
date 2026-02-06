package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ReflectionUtilsTest {

  interface TestingClazz {

    fun map(): Map<String, Any>

    fun someMap(): SomeMap

    fun other(): TestingClazz
  }

  class SomeMap(map: Map<String, Any>) : Map<String, Any> by map

  @Test
  fun `should not detect other`() {
    TestingClazz::class.java.declaredMethods.first { m -> m.name == TestingClazz::other.name }.let { method ->
      assertThat(method.hasPayloadReturnType()).isFalse()
    }
  }


  @Test
  fun `should detect map return type`() {
    TestingClazz::class.java.declaredMethods.first { m -> m.name == TestingClazz::map.name }.let { method ->
      assertThat(method.hasPayloadReturnType()).isTrue()
    }
  }

  @Test
  fun `should detect other return type`() {
    TestingClazz::class.java.declaredMethods.first { m -> m.name == TestingClazz::someMap.name }.let { method ->
      assertThat(method.hasPayloadReturnType()).isTrue()
    }
  }

  @Nested
  inner class LockDurationTest {

    @Test
    fun `getLockDuration should return value when specified`() {
      val method = LockDurationTestWorker::class.java.getDeclaredMethod("workerWithLockDuration")
      assertThat(method.getLockDuration()).isEqualTo(30L)
    }

    @Test
    fun `getLockDuration should return null when not specified`() {
      val method = LockDurationTestWorker::class.java.getDeclaredMethod("workerWithoutLockDuration")
      assertThat(method.getLockDuration()).isNull()
    }

    @Test
    fun `getLockDuration should return null when explicit default`() {
      val method = LockDurationTestWorker::class.java.getDeclaredMethod("workerWithExplicitDefault")
      assertThat(method.getLockDuration()).isNull()
    }

    inner class LockDurationTestWorker {
      @ProcessEngineWorker(topic = "withLock", lockDuration = 30)
      fun workerWithLockDuration() {}

      @ProcessEngineWorker(topic = "withoutLock")
      fun workerWithoutLockDuration() {}

      @ProcessEngineWorker(topic = "explicitDefault", lockDuration = -1)
      fun workerWithExplicitDefault() {}
    }
  }
}
