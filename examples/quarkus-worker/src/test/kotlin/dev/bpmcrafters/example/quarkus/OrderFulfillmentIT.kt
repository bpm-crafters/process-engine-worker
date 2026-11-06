package dev.bpmcrafters.example.quarkus

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import io.restassured.config.HttpClientConfig
import io.restassured.config.RestAssuredConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.locks.LockSupport

/** Opt-in, packaged JVM application against the caller-owned Compose project, not an engine fixture. */
@QuarkusIntegrationTest
class OrderFulfillmentIT {
  private val mapper = jacksonObjectMapper()
  private val engine = System.getenv("EXAMPLE_CAMUNDA_REST_ADDRESS") ?: "http://localhost:8088"
  private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
  private var lastResponse = "No engine response yet"

  @Test fun `happy order reaches a persisted completed instance with shipment variables`() {
    val id = UUID.randomUUID()
    ingress(id, "10.00")
    val key = instanceFor(id)
    completed(key)
    variable(key, "paymentReceived", "true")
    variable(key, "shipped", "true")
    variable(key, "goodsShipped", "true")
    assertTrue(notifications(id).isEmpty())
  }

  @Test fun `rejected payment requires customer notification and does not ship`() {
    val id = UUID.randomUUID()
    ingress(id, "21.00")
    val key = instanceFor(id)
    val notification = eventually("failed-payment notification for $id") {
      notifications(id).firstOrNull { it.path("type").asText() == FAILED_PAYMENT }
    }
    val taskId = notification.path("taskId").asText()
    val details = mapper.readTree(application().get("/failed-payment/$taskId").then().statusCode(200).extract().asString())
    assertEquals(id.toString(), details.path("order").path("orderId").asText())
    assertEquals(0, details.path("amount").decimalValue().compareTo("42.00".toBigDecimal()))
    assertFalse(details.path("failureReason").asText().isBlank())
    application().get("/delayed-inventory/$taskId").then().statusCode(404)
    application().post("/failed-payment/$taskId").then().statusCode(204)
    completed(key)
    // The unchanged boundary event maps only reason and amount out of the error scope.
    variable(key, "paymentAmount", "42.00")
    variable(key, "customer-informed", "true")
    assertTrue(variables(key, "goodsShipped").isEmpty())
    assertTrue(notifications(id).isEmpty())
  }

  @Test fun `inventory timer delivers notification while inventory waits then ships`() {
    val id = UUID.randomUUID()
    ingress(id, "10.00", "out-of-stock")
    val key = instanceFor(id)
    val notification = eventually("timer notification while inventory is delayed for $id") {
      notifications(id).firstOrNull { it.path("type").asText() == DELAYED_INVENTORY }
    }
    val taskId = notification.path("taskId").asText()
    val details = mapper.readTree(application().get("/delayed-inventory/$taskId").then().statusCode(200).extract().asString())
    assertEquals(id.toString(), details.path("order").path("orderId").asText())
    application().post("/delayed-inventory/$taskId").then().statusCode(204)
    variable(key, "customer-informed", "true")
    completed(key)
    variable(key, "shipped", "true")
    variable(key, "goodsShipped", "true")
    // Search indexes can lag independently. Compare persisted execution timestamps after
    // completion rather than treating an absent shipment search result as concurrency proof.
    val fetch = completedElement(key, "Activity_0tmo7ip")
    val notify = completedElement(key, DELAYED_INVENTORY)
    val ship = completedElement(key, "Activity_15rdezk")
    assertFalse(fetch.start.isAfter(notify.start), "Inventory must start before notification: fetch=$fetch, notify=$notify")
    assertTrue(notify.start.isBefore(fetch.end), "Timer notification must start during inventory wait: fetch=$fetch, notify=$notify")
    assertTrue(notify.end.isBefore(fetch.end), "Notification must be delivered and completed independently during inventory wait; run with the default 60s delay: fetch=$fetch, notify=$notify")
    assertFalse(fetch.end.isAfter(ship.start), "Shipment must follow inventory completion: fetch=$fetch, ship=$ship")
  }

  private fun application() = given().config(RestAssuredConfig.config().httpClient(
    HttpClientConfig.httpClientConfig()
      .setParam("http.connection.timeout", 10_000)
      .setParam("http.socket.timeout", 10_000)
  ))

  private fun ingress(id: UUID, price: String, name: String = "book") {
    application().contentType("application/json").body(validOrder(id, price, name))
      .post("/ingress/order-received").then().statusCode(204)
  }

  private fun notifications(id: UUID): List<JsonNode> = mapper.readTree(
    application().get("/user-tasks").then().statusCode(200).extract().asString()
  ).filter { it.path("orderId").asText() == id.toString() }

  private fun instanceFor(id: UUID): String = eventually("persisted engine order $id") {
    // Scan order objects rather than relying on an unsupported nested JSON-path filter.
    // Scope-local copies may repeat the same order; correlate by root process instance key.
    var cursor: String? = null
    var match: String? = null
    val scanDeadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
    do {
      val page = mutableMapOf<String, Any>("limit" to 100)
      cursor?.let { page["after"] = it }
      val result = engineRequest("POST", "/v2/variables/search", mapOf("filter" to mapOf("name" to "order"),
        "sort" to listOf(mapOf("field" to "variableKey", "order" to "ASC")), "page" to page))
      val items = result.path("items").toList()
      for (item in items) {
        val full = if (item.path("isTruncated").asBoolean()) engineRequest("GET", "/v2/variables/${item.path("variableKey").asText()}") else item
        if (mapper.readTree(full.path("value").asText()).path("orderId").asText() == id.toString()) {
          match = item.path("processInstanceKey").asText()
          break
        }
      }
      val next = result.path("page").path("endCursor").asText().takeUnless { it.isBlank() }
      if (match != null || items.size < 100 || next == cursor) break
      cursor = next
    } while (cursor != null && System.nanoTime() < scanDeadline)
    match
  }

  private fun completed(key: String) {
    eventually("completed engine instance $key") {
      search("process-instances", mapOf("processInstanceKey" to key)).firstOrNull { instance ->
        assertEquals(PROCESS_ID, instance.path("processDefinitionId").asText())
        assertFalse(instance.path("hasIncident").asBoolean(), "Engine incident in $key: $instance")
        instance.path("state").asText() == "COMPLETED"
      }
    }
    assertTrue(search("incidents", mapOf("processInstanceKey" to key)).isEmpty(), "Engine recorded an incident in $key")
  }

  private data class ElementTiming(val start: Instant, val end: Instant)

  private fun completedElement(key: String, elementId: String): ElementTiming = eventually("completed $elementId timestamps in $key") {
    val items = search("element-instances", mapOf("processInstanceKey" to key, "state" to "COMPLETED", "elementId" to elementId))
    assertTrue(items.size <= 1, "Expected exactly one non-looping $elementId in $key, found $items")
    val element = items.singleOrNull() ?: return@eventually null
    assertEquals(key, element.path("processInstanceKey").asText())
    assertEquals(elementId, element.path("elementId").asText())
    assertFalse(element.path("hasIncident").asBoolean(), "Engine incident in $element")
    val start = element.path("startDate").takeIf { it.isTextual && it.asText().isNotBlank() } ?: return@eventually null
    val end = element.path("endDate").takeIf { it.isTextual && it.asText().isNotBlank() } ?: return@eventually null
    ElementTiming(OffsetDateTime.parse(start.asText()).toInstant(), OffsetDateTime.parse(end.asText()).toInstant())
  }

  private fun variable(key: String, name: String, expected: String) {
    eventually("engine variable $name=$expected in $key") {
      variables(key, name).firstOrNull {
        val actual = mapper.readTree(it.path("value").asText())
        val wanted = mapper.readTree(expected)
        if (actual.isNumber && wanted.isNumber) actual.decimalValue().compareTo(wanted.decimalValue()) == 0 else actual == wanted
      }
    }
  }

  private fun variables(key: String, name: String): List<JsonNode> =
    search("variables", mapOf("processInstanceKey" to key, "scopeKey" to key, "name" to name))

  private fun search(resource: String, filter: Map<String, Any>): List<JsonNode> {
    return engineRequest("POST", "/v2/$resource/search", mapOf("filter" to filter, "page" to mapOf("limit" to 100)))
      .path("items").toList()
  }

  private fun engineRequest(method: String, path: String, body: Any? = null): JsonNode {
    val request = HttpRequest.newBuilder(URI.create(engine.trimEnd('/') + path)).timeout(Duration.ofSeconds(10))
      .header("Content-Type", "application/json")
      .method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
      .build()
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    lastResponse = "$method $path -> ${response.statusCode()}: ${response.body()}"
    assertEquals(200, response.statusCode(), lastResponse)
    return mapper.readTree(response.body())
  }

  private fun <T : Any> eventually(description: String, condition: () -> T?): T {
    val deadline = System.nanoTime() + Duration.ofSeconds(150).toNanos()
    do {
      condition()?.let { return it }
      LockSupport.parkNanos(Duration.ofMillis(250).toNanos())
    } while (System.nanoTime() < deadline)
    throw AssertionError("Timed out waiting for $description (engine $engine). Last response: $lastResponse")
  }
}
