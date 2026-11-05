package dev.bpmcrafters.processengine.worker.quarkus.deployment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.quarkus.test.QuarkusExtensionTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class KotlinJacksonDefaultsTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusExtensionTest().overrideRuntimeConfigKey("dev.bpm-crafters.process-api.worker.enabled", "false")
  }

  @Inject lateinit var mapper: ObjectMapper

  @Test fun quarkusMapperRegistersKotlinSupportAndSerializesCoreCollectionShapes() {
    assertThat(mapper.registeredModuleIds).contains(KotlinModule.Builder().build().typeId)
    val collections = mapOf(
      "emptyList" to emptyList<String>(), "emptyMap" to emptyMap<String, String>(), "emptySet" to emptySet<String>(),
      "singletonList" to listOf("value"), "singletonMap" to mapOf("key" to "value"), "singletonSet" to setOf("value"))
    assertThat(mapper.readTree(mapper.writeValueAsString(collections))).isEqualTo(mapper.readTree("""
      {"emptyList":[],"emptyMap":{},"emptySet":[],
       "singletonList":["value"],"singletonMap":{"key":"value"},"singletonSet":["value"]}
    """))
  }

  @Test fun kotlinReflectionBuiltinsAreAvailableToTheApplicationClassloader() {
    // JVM guard; native acceptance must additionally prove resource retention.
    val loader = Thread.currentThread().contextClassLoader
    for (resource in listOf("kotlin/kotlin.kotlin_builtins", "kotlin/collections/collections.kotlin_builtins", "kotlin/reflect/reflect.kotlin_builtins")) {
      loader.getResourceAsStream(resource).use { stream ->
        assertThat(stream).`as`(resource).isNotNull()
        assertThat(stream!!.read()).`as`("$resource is nonempty").isNotEqualTo(-1)
      }
    }
  }
}
