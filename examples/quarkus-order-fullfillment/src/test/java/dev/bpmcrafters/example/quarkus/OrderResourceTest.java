package dev.bpmcrafters.example.quarkus;

import dev.bpmcrafters.example.quarkus.adapter.OrderMapper;
import dev.bpmcrafters.example.quarkus.adapter.OrderWire;
import dev.bpmcrafters.example.quarkus.application.port.in.ManageNotifications;
import dev.bpmcrafters.example.quarkus.domain.Notification;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static dev.bpmcrafters.example.quarkus.TestRequests.*;
import static dev.bpmcrafters.example.quarkus.adapter.NotificationMapper.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class OrderResourceTest {
  @Inject TestApis apis;
  @Inject ManageNotifications notifications;

  @BeforeEach
  void resetRecordings() {
    apis.reset();
    notifications.list().forEach(task -> notifications.terminate(task.taskId()));
  }

  @Test
  void ingressMapsOrderAndReturnsNoInventedProcessId() {
    final var id = UUID.randomUUID();
    final String request = validOrder(id, "10.00", "book")
      .replace("\"invoiceAddress\":{\"streetLine\":\"Main 1\"", "\"invoiceAddress\":{\"streetLine\":\"Invoice 2\"");
    given().contentType("application/json").body(request).post("/ingress/order-received").then().statusCode(204).body(equalTo(""));
    assertThat(apis.starts()).hasSize(1);
    final var command = apis.starts().get(0);
    assertThat(command.getMessageName()).isEqualTo("Msg_OrderReceived");
    assertThat(command.getRestrictions()).isEmpty();
    assertThat(command.get()).containsOnlyKeys("order");
    final var order = OrderMapper.toDomain((OrderWire) command.get().get("order"));
    assertThat(order.orderId()).isEqualTo(id);
    assertThat(order.shippingAddress()).isEqualTo(DomainTestData.address());
    assertThat(order.invoiceAddress().streetLine()).isEqualTo("Invoice 2");
    assertThat(order.orderPositions()).containsExactly(new dev.bpmcrafters.example.quarkus.domain.OrderPosition("book", 2, new BigDecimal("10.00")));
  }

  @Test
  void invalidMissingNullAndMalformedOrderInputIsRejectedBeforeEngineCalls() {
    for (final String json : List.of("null", "{}", "{\"id\":null}", "{\"id\":\"wrong\"}",
        validOrder().replace("\"amount\":2", "\"amount\":null"),
        validOrder().replace("\"amount\":2", "\"amount\":0"),
        validOrder().replace("\"amount\":2", "\"amount\":2.5"),
        validOrder().replace("\"amount\":2", "\"amount\":\"2\""),
        validOrder().replace("\"amount\":2", "\"amount\":2147483648"),
        validOrder().replace("\"amount\":2", "\"amount\":-1"),
        validOrder().replace("\"orderPositions\":[", "\"wrongField\":["),
        validOrder().replace("\"price\":10.00", "\"price\":\"wrong\""),
        validOrder().replace("\"price\":10.00", "\"price\":null"),
        validOrder().replace("\"price\":10.00", "\"price\":-0.01"),
        validOrder().replace("\"name\":\"book\"", "\"name\":\" \""),
        validOrder().replace("\"streetLine\":\"Main 1\"", "\"streetLine\":null"),
        validOrder().replace("\"zipCode\":\"12345\"", "\"zipCode\":\" \""),
        validOrder().replace("[{\"name\":\"book\",\"amount\":2,\"price\":10.00}]", "[]"),
        validOrder().replace("[{\"name\":\"book\",\"amount\":2,\"price\":10.00}]", "[null]"))) {
      given().contentType("application/json").body(json).post("/ingress/order-received").then().statusCode(400);
    }
    assertThat(apis.starts()).isEmpty();
  }

  @Test
  void zeroPriceAndUntrimmedNonblankInputAreAccepted() {
    given().contentType("application/json").body(validOrder(UUID.randomUUID(), "0", " book "))
      .post("/ingress/order-received").then().statusCode(204);
    assertThat(((OrderWire) apis.starts().get(0).get().get("order")).orderPositions().get(0).name()).isEqualTo(" book ");
  }

  @Test
  void manualDeploymentSendsOnlyThePackagedSharedModel() throws Exception {
    given().contentType("application/json").body("{\"path\":\"C8/Order Fulfillment.bpmn\"}")
      .post("/deploy").then().statusCode(200).body("deploymentKey", equalTo("test-deployment"))
      .body("deploymentTime", nullValue()).body("tenantId", nullValue());
    assertThat(apis.deployments()).hasSize(1);
    final var command = apis.deployments().get(0);
    assertThat(command.tenantId()).isNull();
    assertThat(command.resources()).hasSize(1);
    assertThat(command.resources().get(0).name()).isEqualTo("C8/Order Fulfillment.bpmn");
    try (final var expected = getClass().getClassLoader().getResourceAsStream("C8/Order Fulfillment.bpmn")) {
      assertThat(command.resources().get(0).bytes()).isNotEmpty().isEqualTo(expected.readAllBytes());
    }
  }

  @Test
  void manualDeploymentRejectsMissingNullAndArbitraryClasspathPathsWithoutEngineCalls() {
    for (final String json : List.of("null", "{}", "{\"path\":null}", "{\"path\":\"application.yaml\"}",
        "{\"path\":\"C7/Order Fulfillment C7.bpmn\"}")) {
      given().contentType("application/json").body(json).post("/deploy").then().statusCode(400);
    }
    assertThat(apis.deployments()).isEmpty();
  }

  @Test
  void unknownNotificationEndpointsReturnNotFoundWithoutCompletionCalls() {
    for (final String type : List.of("failed-payment", "delayed-inventory")) {
      given().get("/" + type + "/unknown").then().statusCode(404);
      given().post("/" + type + "/unknown").then().statusCode(404);
    }
    given().get("/user-tasks").then().statusCode(200).body("size()", equalTo(0));
    assertThat(apis.completions()).isEmpty();
  }

  @Test
  void notificationDetailsIncludeNullFieldsAndCompletionChecksType() {
    final Notification failed = DomainTestData.failed("10");
    final Notification delayed = DomainTestData.delayed("2");
    notifications.accept(delayed);
    notifications.accept(failed);
    given().get("/user-tasks").then().statusCode(200).body("taskId", contains("10", "2"))
      .body("[0].type", equalTo(FAILED_PAYMENT)).body("[0].orderId", equalTo(failed.order().orderId().toString()));
    given().get("/failed-payment/10").then().statusCode(200)
      .body("taskId", equalTo("10")).body("type", equalTo(FAILED_PAYMENT))
      .body("order.orderId", equalTo(failed.order().orderId().toString()))
      .body("paymentReference", equalTo(failed.paymentReference())).body("failureReason", equalTo("Rejected"))
      .body("amount", equalTo(42.0f)).body("items", nullValue());
    given().get("/delayed-inventory/2").then().statusCode(200).body("items", contains("book"))
      .body("paymentReference", nullValue()).body("failureReason", nullValue()).body("amount", nullValue());
    given().get("/delayed-inventory/10").then().statusCode(404);
    given().post("/delayed-inventory/10").then().statusCode(404);
    given().get("/failed-payment/2").then().statusCode(404);
    given().post("/failed-payment/2").then().statusCode(404);
    assertThat(apis.completions()).isEmpty();
    given().post("/failed-payment/10").then().statusCode(204).body(equalTo(""));
    given().post("/delayed-inventory/2").then().statusCode(204);
    assertThat(apis.completions()).hasSize(2).allSatisfy(command -> assertThat(command.get())
      .containsExactlyInAnyOrderEntriesOf(Map.of("customer-informed", true)));
    given().get("/user-tasks").then().statusCode(200).body("size()", equalTo(0));
  }
}
