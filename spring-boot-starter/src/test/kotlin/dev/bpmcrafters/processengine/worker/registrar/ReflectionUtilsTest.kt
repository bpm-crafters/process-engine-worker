package dev.bpmcrafters.processengine.worker.registrar

import org.assertj.core.api.Assertions.assertThat
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
}
