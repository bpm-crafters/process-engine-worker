package dev.bpmcrafters.example.quarkus

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

@QuarkusTest
class OrderResourceTest {
  @Inject
  lateinit var apis: TestApis

  @BeforeEach
  fun resetRecordings() = apis.reset()

  @Test
  fun `ingress maps order and returns no invented process id`() {
    val id = UUID.randomUUID()
    val request = validOrder(id).replace("\"invoiceAddress\":{\"streetLine\":\"Main 1\"", "\"invoiceAddress\":{\"streetLine\":\"Invoice 2\"")
    given().contentType("application/json").body(request).post("/ingress/order-received")
      .then().statusCode(204).body(equalTo(""))
    val command = apis.starts.single()
    assertEquals("Msg_OrderReceived", command.messageName)
    assertTrue(command.restrictions.isEmpty())
    assertEquals(setOf("order"), command.get().keys)
    val order = command.get()["order"] as Order
    assertEquals(id, order.orderId)
    assertEquals(Address("Main 1", "12345", "Berlin", "DE"), order.shippingAddress)
    assertEquals(Address("Invoice 2", "12345", "Berlin", "DE"), order.invoiceAddress)
    assertEquals(listOf(OrderPosition("book", 2, BigDecimal("10.00"))), order.orderPositions)
  }

  @Test
  fun `invalid missing and null order input is rejected`() {
    listOf(
      "null", "{}", "{\"id\":null}", "{\"id\":\"wrong\"}",
      validOrder().replace("\"amount\":2", "\"amount\":null"),
      validOrder().replace("\"orderPositions\":[", "\"wrongField\":["),
      validOrder().replace("\"price\":10.00", "\"price\":\"wrong\"")
    ).forEach { json ->
      given().contentType("application/json").body(json).post("/ingress/order-received").then().statusCode(400)
    }
    assertTrue(apis.starts.isEmpty(), "Invalid orders must not reach the engine API")
  }

  @Test
  fun `manual deployment sends only the packaged shared model`() {
    given().contentType("application/json").body("""{"path":"$PROCESS_RESOURCE"}""")
      .post("/deploy").then().statusCode(200).body("deploymentKey", equalTo("test-deployment"))
    val command = apis.deployments.single()
    assertNull(command.tenantId)
    val resource = command.resources.single()
    assertEquals(PROCESS_RESOURCE, resource.name)
    val expected = requireNotNull(javaClass.classLoader.getResourceAsStream(PROCESS_RESOURCE)).use { it.readAllBytes() }
    assertArrayEquals(expected, resource.bytes)
    assertTrue(resource.bytes.isNotEmpty())
  }

  @Test
  fun `manual deployment rejects missing null and arbitrary classpath paths without engine calls`() {
    listOf(
      "null", "{}", """{"path":null}""", """{"path":"application.yaml"}""",
      """{"path":"C7/Order Fulfillment C7.bpmn"}"""
    ).forEach { json ->
      given().contentType("application/json").body(json).post("/deploy").then().statusCode(400)
    }
    assertTrue(apis.deployments.isEmpty(), "Rejected resource paths must not reach the deployment API")
  }

  @Test
  fun `unknown notification endpoints return not found`() {
    for (type in listOf("failed-payment", "delayed-inventory")) {
      given().get("/$type/unknown").then().statusCode(404)
      given().post("/$type/unknown").then().statusCode(404)
    }
    given().get("/user-tasks").then().statusCode(200)
  }
}

internal fun validOrder(id: UUID = UUID.randomUUID(), price: String = "10.00", name: String = "book"): String = """
  {"id":"$id","shipmentAddress":{"streetLine":"Main 1","zipCode":"12345","city":"Berlin","country":"DE"},
   "invoiceAddress":{"streetLine":"Main 1","zipCode":"12345","city":"Berlin","country":"DE"},
   "orderPositions":[{"name":"$name","amount":2,"price":$price}]}
""".trimIndent()
