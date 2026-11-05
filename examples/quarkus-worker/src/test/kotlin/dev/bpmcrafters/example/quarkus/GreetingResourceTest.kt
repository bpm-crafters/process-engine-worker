package dev.bpmcrafters.example.quarkus

import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsNoOp
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@QuarkusTest
class GreetingResourceTest {
  @field:Inject
  lateinit var metrics: ProcessEngineWorkerMetrics

  @Test
  fun providesNoOpMetricsWithMicrometerPhysicallyAbsent() {
    // Unlike the extension's deployment-test classpath, this consumer does not depend on Micrometer.
    assertThatThrownBy {
      Class.forName("io.micrometer.core.instrument.MeterRegistry", false, Thread.currentThread().contextClassLoader)
    }.isInstanceOf(ClassNotFoundException::class.java)
    assertThat(metrics).isSameAs(ProcessEngineWorkerMetricsNoOp)
  }

  @Test
  fun registersConvertsInvokesAndCompletesWorker() {
    val response = given().contentType(ContentType.JSON).body(GreetingRequest("Ada"))
      .`when`().post("/demo/greetings").then().statusCode(200).extract().`as`(GreetingResponse::class.java)
    assertThat(response).isEqualTo(GreetingResponse("Hello Ada"))
  }

  @Test
  fun rejectsBlankNames() {
    given().contentType(ContentType.JSON).body(GreetingRequest(" "))
      .`when`().post("/demo/greetings").then().statusCode(400)
  }

  @ParameterizedTest
  @ValueSource(strings = ["", "null", "{}", "{\"name\":null}"])
  fun rejectsMissingOrNullRequestOrName(payload: String) {
    given().contentType(ContentType.JSON).body(payload).`when`().post("/demo/greetings").then().statusCode(400)
  }
}
