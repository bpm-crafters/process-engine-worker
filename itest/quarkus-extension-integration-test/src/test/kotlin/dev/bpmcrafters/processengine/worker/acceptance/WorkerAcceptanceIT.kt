package dev.bpmcrafters.processengine.worker.acceptance

import dev.bpmcrafters.processengine.worker.acceptance.AcceptanceHttp.given
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.http.ContentType
import io.restassured.response.Response
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Executes unchanged against the packaged JVM application and the native executable. */
@QuarkusIntegrationTest
@QuarkusTestResource(PostgreSqlResource::class)
class WorkerAcceptanceIT {
  companion object {
    private val RETAINED = java.lang.Boolean.getBoolean("acceptance.retained")
    private const val ACTIVE = 0
    private const val COMMITTED = 3
    private const val ROLLED_BACK = 4
    private const val NO_TRANSACTION = 6
  }

  // Quarkus test-resource injection, not application CDI injection.
  lateinit var database: DatabaseAccess

  @Test
  fun launchesTheExpectedPackagedRunnerOnTheDiscoveredIsolatedPort() {
    val runtime = given().get("/harness/runtime").then().statusCode(200).extract().jsonPath()
    assertThat(runtime.getInt("port")).isPositive().isEqualTo(io.restassured.RestAssured.port)
    assertThat(runtime.getLong("pid")).isNotEqualTo(ProcessHandle.current().pid())
    assertThat(runtime.getString("imageCode"))
      .isEqualTo(if (java.lang.Boolean.getBoolean("acceptance.native")) "runtime" else "jvm")
  }

  @Test
  fun registersWorkersAndDeploysPackagedResourcesAtStartupAndOnDemand() {
    val state = given().get("/harness").then().statusCode(200).extract().jsonPath()
    assertThat(state.getList<String>("topics")).contains("required-before", "required-after", "new-before",
      "new-after", "typed", "generic", "manual", "mandatory", "supports", "business-error", "retry")
    assertThat(state.getList<String>("subscriptions.typed.variables"))
      .containsExactlyInAnyOrder("payload", "state", "label")
    assertThat(state.getList<String>("subscriptions.generic.variables")).containsExactly("payload")
    assertThat(state.getBoolean("removeOnCompletion")).isEqualTo(!RETAINED)
    val count = state.getInt("deploymentCount")
    assertThat(count).isGreaterThanOrEqualTo(1)
    val resources = state.getList<Map<String, String>>("deployments")
    assertThat(resources).hasSize(count * 2)
    val basedir = Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
    assertResourceDigest(resources, "example-process.bpmn", basedir.resolve(
      "../spring-boot-starter-integration-test/src/test/resources/bpmn/example-process.bpmn"))
    assertResourceDigest(resources, "decision-fixture.txt", basedir.resolve(
      "src/main/resources/deployment/decision-fixture.txt"))
    val priorHistorySize = resources.size
    given().post("/harness/deploy").then().statusCode(200).body("key", equalTo("fixture-${count + 1}"))
    val manual = given().get("/harness").then().body("deploymentCount", equalTo(count + 1)).extract().jsonPath()
    val history = manual.getList<Map<String, String>>("deployments")
    assertThat(history).hasSize(priorHistorySize + 2)
    val appended = history.subList(priorHistorySize, history.size)
    assertThat(appended.map { it["name"] }).containsExactlyInAnyOrder("example-process.bpmn", "decision-fixture.txt")
    assertResourceDigest(appended, "example-process.bpmn", basedir.resolve(
      "../spring-boot-starter-integration-test/src/test/resources/bpmn/example-process.bpmn"))
    assertResourceDigest(appended, "decision-fixture.txt", basedir.resolve(
      "src/main/resources/deployment/decision-fixture.txt"))
  }

  @Test
  fun convertsTypedVariablesCustomConverterAndGenericInterfaceMetadata() {
    val payload = mapOf("payload" to mapOf("name" to "Ada",
      "details" to mapOf("amount" to 7, "state" to "READY")), "state" to "DONE", "label" to "hello")
    deliver("typed", "typed", payload).then().statusCode(200)
      .body("result.name", equalTo("Ada"), "result.amount", equalTo(7), "result.state", equalTo("DONE"),
        "result.nestedState", equalTo("READY"), "result.label", equalTo("HELLO"))
    deliver("generic", "generic", payload).then().statusCode(200)
      .body("result.generic", equalTo("Ada"), "result.nestedAmount", equalTo(7))
    given().get("/harness/metrics/typed").then().statusCode(200)
      .body("received", equalTo(1.0F), "completed", equalTo(1.0F))
  }

  @ParameterizedTest
  @ValueSource(strings = ["/harness/deliver/typed/unsupported-media", "/harness/registry/unsupported-media"])
  fun jsonPayloadEndpointsRejectNonJsonContentTypes(path: String) {
    given().contentType(ContentType.TEXT).body("not JSON").post(path).then().statusCode(415)
  }

  @Test
  fun reportsBusinessErrorsAndExplicitRetryBackoffAndMicrometerCounters() {
    deliver("business-error", "business-error", emptyMap()).then().statusCode(200)
      .body("error.code", equalTo("fixture-error"))
    deliver("retry", "retry", emptyMap()).then().statusCode(200)
      .body("failure.retryCount", equalTo(4), "failure.backoff", equalTo("PT30S"))
    val errors = given().get("/harness/metrics/business-error").then().extract().jsonPath()
    assertThat(errors.getDouble("'bpmn.error'")).isEqualTo(1.0)
    given().get("/harness/metrics/retry").then().body("failed", equalTo(1.0F))
  }

  @ParameterizedTest
  @ValueSource(strings = ["required-before", "new-before"])
  fun failedBeforeCommitCompletionRollsBackBusinessAndCache(topic: String) {
    val id = "$topic-failure"
    completion(id, "fail")
    deliver(topic, id, emptyMap()).then().statusCode(200).body("invocations", equalTo(1), "attempts", equalTo(1))
    assertThat(database.businessCount(id)).isZero()
    assertThat(database.registryRow(id).exists).isFalse()
    val state = given().get("/harness/tasks/$id").then().extract().jsonPath()
    assertThat(state.getInt("completionStatus")).isEqualTo(ACTIVE)
    assertThat(state.getMap<String, Any?>("failure")).isNotEmpty()
    assertThat(state.getList<String>("events")).contains("$id:transaction-ended:$ROLLED_BACK")
    completion(id, "success")
    deliver(topic, id, emptyMap()).then().statusCode(200)
      .body("invocations", equalTo(2), "attempts", equalTo(2), "result.value", equalTo("business-result"))
    assertThat(database.businessCount(id)).isEqualTo(1)
    assertThat(database.registryRow(id).exists).isEqualTo(RETAINED)
  }

  @ParameterizedTest
  @ValueSource(strings = ["required-after", "new-after"])
  fun afterCommitFailureRetainsCacheAndRedeliveryDoesNotRepeatBusiness(topic: String) {
    val id = "$topic-retry"
    completion(id, "fail")
    deliver(topic, id, emptyMap()).then().statusCode(200).body("invocations", equalTo(1), "attempts", equalTo(1))
    assertThat(database.businessCount(id)).isEqualTo(1)
    assertThat(database.registryRow(id).exists).isTrue()
    assertThat(database.deserialize(database.registryRow(id).bytes)).isEqualTo(mapOf("value" to "business-result"))
    val state = given().get("/harness/tasks/$id").then().extract().jsonPath()
    assertThat(state.getInt("completionStatus")).isEqualTo(NO_TRANSACTION)
    assertThat(state.getList<String>("events")).contains("$id:transaction-ended:$COMMITTED")
    val cachedBytes = database.registryRow(id).bytes
    deliver(topic, id, emptyMap()).then().statusCode(200).body("invocations", equalTo(1), "attempts", equalTo(2))
    assertThat(database.registryRow(id).bytes).isEqualTo(cachedBytes)
    completion(id, "success")
    deliver(topic, id, emptyMap()).then().statusCode(200)
      .body("invocations", equalTo(1), "attempts", equalTo(3), "result.value", equalTo("business-result"))
    assertThat(database.businessCount(id)).isEqualTo(1)
    assertThat(database.registryRow(id).exists).isEqualTo(RETAINED)
    if (RETAINED) {
      deliver(topic, id, emptyMap()).then().statusCode(200).body("invocations", equalTo(1), "attempts", equalTo(4))
      assertThat(database.registryRow(id).bytes).isEqualTo(cachedBytes)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["required-before", "required-after", "new-before", "new-after", "supports", "mandatory"])
  fun successfulTransactionalCompletionCommitsBusinessAndAppliesRetentionPolicy(topic: String) {
    val id = "$topic-success"
    deliver(topic, id, emptyMap()).then().statusCode(200)
      .body("invocations", equalTo(1), "attempts", equalTo(1), "result.value", equalTo("business-result"))
    assertThat(database.businessCount(id)).isEqualTo(1)
    assertThat(database.registryRow(id).exists).isEqualTo(RETAINED)
    val status = given().get("/harness/tasks/$id").then().extract().jsonPath().getInt("completionStatus")
    assertThat(status).isEqualTo(if (topic.endsWith("before")) ACTIVE else NO_TRANSACTION)
    val state = given().get("/harness/tasks/$id").then().extract().jsonPath()
    assertThat(state.getString("workerTransaction")).isNotBlank()
    assertThat(state.getList<String>("events").filter { it.startsWith("$id:transaction-ended:") })
      .containsExactly("$id:transaction-ended:$COMMITTED")
  }

  @Test
  fun preservesManualCompletionWithoutIssuingAnotherAutomaticCompletion() {
    deliver("manual", "manual", emptyMap()).then().statusCode(200)
      .body("invocations", equalTo(1), "attempts", equalTo(1), "result.manual", equalTo(true))
    assertThat(database.businessCount("manual")).isEqualTo(1)
  }

  @ParameterizedTest
  @ValueSource(strings = ["required-after:success", "required-after:failure", "required-before:success", "required-before:failure"])
  fun controlsDeferredCompletionAndObservesDatabaseVisibility(scenario: String) {
    val parts = scenario.split(":")
    val topic = parts[0]
    val success = parts[1] == "success"
    val before = topic.endsWith("before")
    val id = "controlled-${scenario.replace(':', '-')}"
    ControlledDelivery(topic, id).use { delivery ->
      delivery.awaitPending()
      assertThat(database.businessCount(id)).isEqualTo(if (before) 0 else 1)
      assertThat(database.registryRow(id).exists).isEqualTo(!before)
      delivery.assertWaiting()
      delivery.finish(success).then().statusCode(200)
    }
    assertThat(database.businessCount(id)).isEqualTo(if (before && !success) 0 else 1)
    assertThat(database.registryRow(id).exists).isEqualTo(if (success) RETAINED else !before)
    given().get("/harness/tasks/$id").then().body("attempts", equalTo(1))
    val events = given().get("/harness/tasks/$id").then().extract().jsonPath().getList<String>("events")
    assertThat(events).contains("$id:transaction-ended:${if (before && !success) ROLLED_BACK else COMMITTED}")
    assertThat(events.contains("$id:completed")).isEqualTo(success)
  }

  @Test
  fun releaseBeforeRegistrationAndRepeatedReleaseNeverBlockALaterDelivery() {
    val id = "released-before-registration"
    completion(id, "pending")
    given().queryParam("success", true).post("/harness/release/$id").then().statusCode(204)
    given().queryParam("success", false).post("/harness/release/$id").then().statusCode(204)
    deliver("required-after", id, emptyMap()).then().statusCode(200)
      .body("attempts", equalTo(1), "result.value", equalTo("business-result"), "pending", equalTo(false))
    assertThat(database.businessCount(id)).isEqualTo(1)
  }

  @ParameterizedTest
  @ValueSource(strings = ["required-after", "mandatory", "supports"])
  fun joinsTheExactAmbientTransactionAndCompletesOnlyAfterCommit(topic: String) {
    val id = "$topic-ambient"
    given().queryParam("ambient", true).contentType(ContentType.JSON).body(emptyMap<String, Any?>())
      .post("/harness/deliver/$topic/$id").then().statusCode(200)
      .body("invocations", equalTo(1), "joinedAmbient", equalTo(true))
    awaitSuccessfulAmbientCompletion(id)
  }

  @ParameterizedTest
  @ValueSource(strings = ["required-after", "mandatory", "supports"])
  fun ambientRollbackUndoesBusinessAndCacheWithoutSuccessfulCompletion(topic: String) {
    val id = "$topic-joined-rollback"
    given().queryParam("ambient", true).queryParam("rollbackAmbient", true)
      .contentType(ContentType.JSON).body(emptyMap<String, Any?>()).post("/harness/deliver/$topic/$id")
      .then().statusCode(200).body("invocations", equalTo(1), "joinedAmbient", equalTo(true))
    await().atMost(Duration.ofSeconds(20)).untilAsserted {
      val state = given().get("/harness/tasks/$id").then().statusCode(200).extract().jsonPath()
      assertThat(state.getMap<String, Any?>("failure")).isNotEmpty()
      assertThat(state.getList<String>("events")).contains("$id:failed")
      assertThat(state.getInt("attempts")).isZero()
      assertRolledBackWithoutCompletion(id)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["new-before", "new-after"])
  fun requiresNewCommitsIndependentlyOfAnAmbientRollback(topic: String) {
    val id = "$topic-ambient-rollback"
    given().queryParam("ambient", true).queryParam("rollbackAmbient", true)
      .contentType(ContentType.JSON).body(emptyMap<String, Any?>()).post("/harness/deliver/$topic/$id")
      .then().statusCode(200).body("invocations", equalTo(1), "result.value", equalTo("business-result"),
        "joinedAmbient", equalTo(false))
    assertThat(database.businessCount(id)).isEqualTo(1)
    assertThat(database.registryRow(id).exists).isEqualTo(RETAINED)
  }

  @ParameterizedTest
  @ValueSource(strings = ["runtime-failure", "flush-failure", "nonserializable-result"])
  fun workerOrFlushOrSerializationFailureRollsBackWithoutAfterCommitCompletion(topic: String) {
    val id = "$topic-atomicity"
    deliver(topic, id, emptyMap()).then().statusCode(200).body("invocations", equalTo(1), "attempts", equalTo(0))
    assertRolledBackWithoutCompletion(id)
    assertThat(given().get("/harness/tasks/$id").then().extract().jsonPath().getMap<String, Any?>("failure")).isNotEmpty()
    if (topic == "nonserializable-result") {
      val state = given().get("/harness/tasks/$id").then().extract().jsonPath()
      assertThat(state.getString("failure.reason")).contains("Cannot serialize task result")
        .doesNotContain("UnsupportedFeatureError", "SerializationConstructorAccessor")
      assertThat(state.getString("failure.errorDetails")).contains("java.io.NotSerializableException")
        .doesNotContain("UnsupportedFeatureError", "SerializationConstructorAccessor")
    }
    if (topic == "flush-failure") {
      assertThat(given().get("/harness/tasks/$id").then().extract().jsonPath().getList<String>("events"))
        .contains("$id:worker-returned")
    }
  }

  @Test
  fun corruptStoredBytesFailExplicitlyRatherThanActingAsACacheMiss() {
    val id = "corrupt-result"
    val invalid = byteArrayOf(1, 2, 3, 4)
    database.insertBytes(id, invalid)
    given().get("/harness/registry/$id").then().statusCode(500)
    deliver("required-after", id, emptyMap()).then().statusCode(200)
      .body("invocations", equalTo(0), "attempts", equalTo(0))
    assertThat(given().get("/harness/tasks/$id").then().extract().jsonPath().getMap<String, Any?>("failure")).isNotEmpty()
    assertThat(database.businessCount(id)).isZero()
    assertThat(database.registryRow(id).bytes).isEqualTo(invalid)
  }

  @Test
  fun distinguishesMissingFromSqlNullUpdatesRegistrationAndCleansUpSingleAndProcessRows() {
    given().get("/harness/registry/missing").then().body("exists", equalTo(false))
    register("registry-empty", "registry-process", emptyMap())
    given().get("/harness/registry/registry-empty").then().body("exists", equalTo(true), "empty", equalTo(true))
    assertThat(database.registryRow("registry-empty").exists).isTrue()
    assertThat(database.registryRow("registry-empty").bytes).isNull()
    register("registry-update", "old-process", mapOf("value" to "old"))
    register("registry-update", "registry-process", mapOf("value" to "new"))
    assertThat(database.registryRow("registry-update").processId).isEqualTo("registry-process")
    assertThat(database.deserialize(database.registryRow("registry-update").bytes)).isEqualTo(mapOf("value" to "new"))
    given().delete("/harness/process/old-process").then().statusCode(204)
    given().get("/harness/registry/registry-update").then().body("result.value", equalTo("new"))
    register("registry-other", "other-process", mapOf("retained" to true))
    given().delete("/harness/registry/registry-empty").then().statusCode(204)
    assertThat(database.registryRow("registry-empty").exists).isFalse()
    given().delete("/harness/process/registry-process").then().statusCode(204)
    given().delete("/harness/process/registry-process").then().statusCode(204)
    assertThat(database.registryRow("registry-update").exists).isFalse()
    assertThat(database.registryRow("registry-other").exists).isTrue()
  }

  @Test
  fun readsJvmWrittenSerializationAndWritesSerializationThatTheExternalJvmCanRead() {
    val original = SerializationSamples.create()
    database.insertBytes("serialization-historical-java", SerializationSamplesTest.historicalJavaGraph())
    given().get("/harness/registry/serialization-historical-java/samples").then().statusCode(200)
      .body("matches", equalTo(true), "dtoClass", equalTo(WorkerPayload::class.java.name))
    database.insertJvmSerialized("serialization-jvm", original)
    given().get("/harness/registry/serialization-jvm/samples").then().statusCode(200)
      .body("matches", equalTo(true), "dtoClass", equalTo(WorkerPayload::class.java.name))
    given().post("/harness/registry/serialization-application/samples").then().statusCode(204)
    given().get("/harness/registry/serialization-application/samples").then().statusCode(200)
      .body("matches", equalTo(true))
    val decoded = database.deserialize(database.registryRow("serialization-application").bytes)
    assertThat(decoded).isInstanceOf(Map::class.java)
    assertThat(decoded).isInstanceOf(LinkedHashMap::class.java)
    assertThat(SerializationSamples.matches(decoded as Map<*, *>)).isTrue()
    assertArraysAndSharedReferences(decoded)
    assertContainerTypes(original, decoded)
  }

  private fun assertRolledBackWithoutCompletion(id: String) {
    assertThat(database.businessCount(id)).isZero()
    assertThat(database.registryRow(id).exists).isFalse()
    val state = given().get("/harness/tasks/$id").then().extract().jsonPath()
    assertThat(state.getMap<String, Any?>("result")).isNull()
    assertThat(state.getList<String>("events")).contains("$id:transaction-ended:$ROLLED_BACK")
      .doesNotContain("$id:completed")
  }

  private fun awaitSuccessfulAmbientCompletion(id: String) {
    await().atMost(Duration.ofSeconds(20)).untilAsserted {
      given().get("/harness/tasks/$id").then().statusCode(200)
        .body("invocations", equalTo(1), "attempts", equalTo(1), "joinedAmbient", equalTo(true),
          "result.value", equalTo("business-result"), "completionStatus", equalTo(NO_TRANSACTION))
      // Result publication precedes cache cleanup: poll both API and fresh JDBC visibility together.
      assertThat(database.businessCount(id)).isEqualTo(1)
      assertThat(database.registryRow(id).exists).isEqualTo(RETAINED)
    }
  }

  private fun assertResourceDigest(resources: List<Map<String, String>>, name: String, source: Path) {
    val expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)))
    assertThat(resources.filter { it["name"] == name }).isNotEmpty()
      .allSatisfy { resource -> assertThat(resource["sha256"]).isEqualTo(expected) }
  }

  @Suppress("UNCHECKED_CAST")
  private fun assertArraysAndSharedReferences(decoded: Map<*, *>) {
    assertThat(decoded["bytes"] as ByteArray).containsExactly(1, -2)
    assertThat(decoded["shorts"] as ShortArray).containsExactly(3, -4)
    assertThat(decoded["ints"] as IntArray).containsExactly(5, -6)
    assertThat(decoded["longs"] as LongArray).containsExactly(7L, -8L)
    assertThat(decoded["floats"] as FloatArray).containsExactly(1.5F, -2.5F)
    assertThat(decoded["doubles"] as DoubleArray).containsExactly(3.5, -4.5)
    assertThat(decoded["chars"] as CharArray).containsExactly('a', '\u03bb')
    assertThat(decoded["booleans"] as BooleanArray).containsExactly(true, false)
    val dtos = decoded["dtoArray"] as Array<WorkerPayload>
    assertThat(dtos).containsExactly(WorkerPayload("serialized", WorkerPayload.Details(7, WorkerPayload.State.READY)))
    assertThat(dtos[0]).isSameAs(decoded["dto"])
    val dtoMatrix = decoded["dtoMatrix"] as Array<Array<WorkerPayload>>
    assertThat(dtoMatrix[0]).isSameAs(dtos).isSameAs(dtoMatrix[1])
    val matrix = decoded["primitiveMatrix"] as Array<IntArray>
    assertThat(matrix).isDeepEqualTo(arrayOf(intArrayOf(5, -6), intArrayOf(5, -6)))
    assertThat(matrix[0]).isSameAs(decoded["ints"]).isSameAs(matrix[1])
    val references = decoded["references"] as Array<Array<Any>>
    assertThat(references[0][0]).isSameAs(decoded["shared"]).isSameAs(references[1][0])
  }

  private fun assertContainerTypes(original: Map<String, Any?>, decoded: Map<*, *>) {
    for (key in listOf("immutable", "mutable", "singletons", "unmodifiable", "empty", "kotlinBuilt")) {
      val expected = original[key] as List<*>
      val actual = decoded[key] as List<*>
      assertThat(actual.javaClass).`as`("%s outer container", key).isEqualTo(expected.javaClass)
      val expectedClasses = expected.map { it!!.javaClass.name }
      assertThat(actual.map { it!!.javaClass.name }).`as`("%s nested containers", key).isEqualTo(expectedClasses)
    }
  }

  private fun deliver(topic: String, id: String, payload: Map<String, Any?>): Response =
    given().contentType(ContentType.JSON).body(payload).post("/harness/deliver/$topic/$id")

  private fun completion(id: String, mode: String) {
    given().post("/harness/completion/$id/$mode").then().statusCode(204)
  }

  private fun register(id: String, process: String, result: Map<String, Any?>) {
    given().queryParam("process", process).contentType(ContentType.JSON).body(result)
      .post("/harness/registry/$id").then().statusCode(204)
  }
}
