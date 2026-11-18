package dev.bpmcrafters.example.quarkus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.StreamSupport;
import static dev.bpmcrafters.example.quarkus.adapter.NotificationMapper.*;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** Packaged JVM application, caller-owned real engine. No container lifecycle operations. */
@QuarkusIntegrationTest
class OrderFulfillmentIT {
  private final ObjectMapper mapper = new ObjectMapper();
  private final String engine = System.getenv().getOrDefault("EXAMPLE_CAMUNDA_REST_ADDRESS", "http://localhost:8088");
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private String lastResponse = "No engine response yet";

  @Test
  void happyOrderReachesPersistedCompletedInstanceWithShipmentVariables() throws Exception {
    final UUID id = UUID.randomUUID();
    ingress(id, "10.00", "book");
    final String key = instanceFor(id);
    completed(key);
    variable(key, "paymentReceived", "true");
    variable(key, "shipped", "true");
    variable(key, "goodsShipped", "true");
    assertTrue(notifications(id).isEmpty());
  }

  @Test
  void rejectedPaymentRequiresCustomerNotificationAndDoesNotShip() throws Exception {
    final UUID id = UUID.randomUUID();
    ingress(id, "21.00", "book");
    final String key = instanceFor(id);
    final JsonNode notification = eventually("failed-payment notification for " + id,
      () -> notifications(id).stream().filter(task -> task.path("type").asText().equals(FAILED_PAYMENT)).findFirst().orElse(null));
    final String taskId = notification.path("taskId").asText();
    final JsonNode details = mapper.readTree(application().get("/failed-payment/" + taskId).then().statusCode(200).extract().asString());
    assertEquals(id.toString(), details.path("order").path("orderId").asText());
    assertEquals(0, details.path("amount").decimalValue().compareTo(new java.math.BigDecimal("42.00")));
    assertFalse(details.path("failureReason").asText().isBlank());
    application().get("/delayed-inventory/" + taskId).then().statusCode(404);
    application().post("/failed-payment/" + taskId).then().statusCode(204);
    completed(key);
    // Unchanged boundary event maps only reason and amount out of the error scope.
    variable(key, "paymentAmount", "42.00");
    variable(key, "customer-informed", "true");
    assertTrue(variables(key, "goodsShipped").isEmpty());
    assertTrue(notifications(id).isEmpty());
  }

  @Test
  void inventoryTimerDeliversNotificationWhileInventoryWaitsThenShips() throws Exception {
    final UUID id = UUID.randomUUID();
    ingress(id, "10.00", "out-of-stock");
    final String key = instanceFor(id);
    final JsonNode notification = eventually("timer notification during inventory wait for " + id,
      () -> delayedNotification(id));
    final String taskId = notification.path("taskId").asText();
    final JsonNode details = mapper.readTree(application().get("/delayed-inventory/" + taskId).then().statusCode(200).extract().asString());
    assertEquals(id.toString(), details.path("order").path("orderId").asText());
    application().post("/delayed-inventory/" + taskId).then().statusCode(204);
    variable(key, "customer-informed", "true");
    completed(key);
    variable(key, "shipped", "true");
    variable(key, "goodsShipped", "true");
    // Persisted timestamps, not eventually-consistent absence, prove independent delivery.
    final ElementTiming fetch = completedElement(key, "Activity_0tmo7ip");
    final ElementTiming notify = completedElement(key, DELAYED_INVENTORY);
    final ElementTiming ship = completedElement(key, "Activity_15rdezk");
    assertFalse(fetch.start().isAfter(notify.start()), "Inventory must start before notification: " + fetch + ", " + notify);
    assertTrue(notify.start().isBefore(fetch.end()), "Timer notification must start during inventory wait: " + fetch + ", " + notify);
    assertTrue(notify.end().isBefore(fetch.end()), "Notification must complete independently during inventory wait; use default 60s delay: " + fetch + ", " + notify);
    assertFalse(fetch.end().isAfter(ship.start()), "Shipment must follow inventory completion: " + fetch + ", " + ship);
  }

  @Test
  void fourConcurrentInventoryWaitsDoNotStarveNotifications() throws Exception {
    final List<UUID> ids = java.util.stream.IntStream.range(0, 4).mapToObj(index -> UUID.randomUUID()).toList();
    try {
      CompletableFuture.allOf(ids.stream().map(id -> CompletableFuture.runAsync(() -> ingress(id, "10.00", "out-of-stock")))
        .toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
      final Map<UUID, String> keys = new LinkedHashMap<>();
      for (final UUID id : ids) keys.put(id, instanceFor(id));
      awaitAllInventoryActive(keys);
      final Map<UUID, JsonNode> delivered = eventually("all four delayed-inventory notifications", () -> {
        final Map<UUID, JsonNode> tasks = new LinkedHashMap<>();
        for (final UUID id : ids) tasks.put(id, delayedNotification(id));
        return tasks.values().stream().allMatch(java.util.Objects::nonNull) ? tasks : null;
      });
      // Complete every flow before evaluating timing, including when running against a broken bridge.
      assertAll("complete all four notifications", ids.stream().map(id -> (Executable) () ->
        application().post("/delayed-inventory/" + delivered.get(id).path("taskId").asText()).then().statusCode(204)));
      assertAll("all four orders finish", keys.values().stream().map(key -> (Executable) () -> completed(key)));
      final Map<UUID, ElementTiming> fetches = new LinkedHashMap<>();
      final Map<UUID, ElementTiming> notified = new LinkedHashMap<>();
      for (final UUID id : ids) {
        fetches.put(id, completedElement(keys.get(id), "Activity_0tmo7ip"));
        notified.put(id, completedElement(keys.get(id), DELAYED_INVENTORY));
      }
      final Instant earliestInventoryEnd = fetches.values().stream().map(ElementTiming::end).min(Instant::compareTo).orElseThrow();
      assertAll("all notifications complete before ANY inventory execution slot frees", ids.stream().map(id -> (Executable) () -> {
        final ElementTiming fetch = fetches.get(id);
        final ElementTiming notify = notified.get(id);
        assertFalse(fetch.start().isAfter(notify.start()), "Inventory must start before notification for " + id + ": " + fetch + ", " + notify);
        assertTrue(notify.end().isBefore(earliestInventoryEnd), "Notification for " + id
          + " must finish before earliest inventory completion; use default 60s delay: notify=" + notify + ", fetches=" + fetches);
      }));
    } finally {
      ids.forEach(this::cleanupNotifications);
    }
  }

  private void awaitAllInventoryActive(Map<UUID, String> keys) throws Exception {
    eventually("all four inventory activities active together", () -> {
      for (final String key : keys.values()) {
        final var active = search("element-instances", Map.of("processInstanceKey", key, "elementId", "Activity_0tmo7ip", "state", "ACTIVE"))
          .stream().filter(element -> key.equals(element.path("processInstanceKey").asText())
            && "Activity_0tmo7ip".equals(element.path("elementId").asText()) && "ACTIVE".equals(element.path("state").asText())).toList();
        if (active.size() != 1) return null;
      }
      return Boolean.TRUE;
    });
  }

  @Test
  void nineConcurrentInventoryOrdersCompleteNotificationsAndFinishWithoutIncidents() throws Exception {
    final List<UUID> ids = java.util.stream.IntStream.range(0, 9).mapToObj(index -> UUID.randomUUID()).toList();
    final long deadline = System.nanoTime() + Duration.ofSeconds(300).toNanos();
    try {
      CompletableFuture.allOf(ids.stream().map(id -> CompletableFuture.runAsync(() -> ingress(id, "10.00", "out-of-stock")))
        .toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
      final Map<UUID, String> keys = new LinkedHashMap<>();
      for (final UUID id : ids) keys.put(id, eventually("bulk engine order " + id, deadline, () -> scanOrderVariables(id)));
      final var notified = new java.util.HashSet<UUID>();
      // Do not wait for nine ACTIVE inventory activities: bounded activation may legitimately queue payment first.
      eventually("all nine orders notified and completed without incidents", deadline, () -> {
        completeBulkNotifications(ids, notified);
        boolean allCompleted = notified.size() == ids.size();
        for (final String key : keys.values()) {
          final List<JsonNode> instances = search("process-instances", Map.of("processInstanceKey", key));
          for (final JsonNode instance : instances) {
            assertEquals(PROCESS_ID, instance.path("processDefinitionId").asText());
            assertFalse(instance.path("hasIncident").asBoolean(), "Engine incident in " + key + ": " + instance);
          }
          allCompleted &= instances.size() == 1 && "COMPLETED".equals(instances.get(0).path("state").asText());
        }
        return allCompleted ? Boolean.TRUE : null;
      });
      assertAll("no incidents in all nine orders", keys.values().stream().map(key -> (Executable) () ->
        assertTrue(search("incidents", Map.of("processInstanceKey", key)).isEmpty(), "Engine recorded incident in " + key)));
      // Completion is a stress guard, not proof of no redelivery; the offline capacity test protects the lease budget.
    } finally {
      ids.forEach(this::cleanupNotifications);
    }
  }

  private void completeBulkNotifications(List<UUID> ids, java.util.Set<UUID> notified) throws Exception {
    final List<JsonNode> tasks = nodes(mapper.readTree(application().get("/user-tasks").then().statusCode(200).extract().asString()));
    for (final JsonNode task : tasks) {
      if (!DELAYED_INVENTORY.equals(task.path("type").asText())) continue;
      final UUID id = UUID.fromString(task.path("orderId").asText());
      if (!ids.contains(id) || notified.contains(id)) continue;
      application().post("/delayed-inventory/" + task.path("taskId").asText()).then().statusCode(204);
      notified.add(id);
    }
  }

  private void cleanupNotifications(UUID id) {
    try {
      for (final JsonNode task : notifications(id)) {
        if (!DELAYED_INVENTORY.equals(task.path("type").asText())) continue;
        try {
          application().post("/delayed-inventory/" + task.path("taskId").asText()).then().statusCode(204);
        } catch (Exception | AssertionError failure) {
          System.err.println("Best-effort notification cleanup failed for " + id + ": " + failure);
        }
      }
    } catch (Exception | AssertionError failure) {
      System.err.println("Best-effort notification lookup failed for " + id + ": " + failure);
    }
  }

  private RequestSpecification application() {
    return given().config(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
      .setParam("http.connection.timeout", 10_000).setParam("http.socket.timeout", 10_000)));
  }

  private void ingress(UUID id, String price, String name) {
    application().contentType("application/json").body(TestRequests.validOrder(id, price, name))
      .post("/ingress/order-received").then().statusCode(204);
  }

  private List<JsonNode> notifications(UUID id) throws Exception {
    return nodes(mapper.readTree(application().get("/user-tasks").then().statusCode(200).extract().asString())).stream()
      .filter(task -> id.toString().equals(task.path("orderId").asText())).toList();
  }

  private JsonNode delayedNotification(UUID id) throws Exception {
    return notifications(id).stream().filter(task -> DELAYED_INVENTORY.equals(task.path("type").asText())).findFirst().orElse(null);
  }

  private String instanceFor(UUID id) throws Exception {
    return eventually("persisted engine order " + id, () -> scanOrderVariables(id));
  }

  private String scanOrderVariables(UUID id) throws Exception {
    String cursor = null;
    final long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    do {
      final Map<String, Object> page = new HashMap<>(Map.of("limit", 100));
      if (cursor != null) page.put("after", cursor);
      final JsonNode result = engineRequest("POST", "/v2/variables/search", Map.of("filter", Map.of("name", "order"),
        "sort", List.of(Map.of("field", "variableKey", "order", "ASC")), "page", page));
      final List<JsonNode> items = nodes(result.path("items"));
      for (final JsonNode item : items) {
        final JsonNode full = item.path("isTruncated").asBoolean()
          ? engineRequest("GET", "/v2/variables/" + item.path("variableKey").asText(), null) : item;
        if (id.toString().equals(mapper.readTree(full.path("value").asText()).path("orderId").asText())) {
          return item.path("processInstanceKey").asText(); // Root correlation, including scope-local copies.
        }
      }
      final String next = result.path("page").path("endCursor").asText();
      if (items.size() < 100 || next.isBlank() || next.equals(cursor)) break;
      cursor = next;
    } while (System.nanoTime() < deadline);
    return null;
  }

  private void completed(String key) throws Exception {
    eventually("completed engine instance " + key, () -> {
      for (final JsonNode instance : search("process-instances", Map.of("processInstanceKey", key))) {
        assertEquals(PROCESS_ID, instance.path("processDefinitionId").asText());
        assertFalse(instance.path("hasIncident").asBoolean(), "Engine incident in " + key + ": " + instance);
        if ("COMPLETED".equals(instance.path("state").asText())) return instance;
      }
      return null;
    });
    assertTrue(search("incidents", Map.of("processInstanceKey", key)).isEmpty(), "Engine recorded incident in " + key);
  }

  private record ElementTiming(Instant start, Instant end) { }

  private ElementTiming completedElement(String key, String elementId) throws Exception {
    return eventually("completed " + elementId + " timestamps in " + key, () -> {
      final List<JsonNode> items = search("element-instances", Map.of("processInstanceKey", key, "state", "COMPLETED", "elementId", elementId));
      assertTrue(items.size() <= 1, "Expected exactly one non-looping " + elementId + " in " + key + ", found " + items);
      if (items.isEmpty()) return null;
      final JsonNode element = items.get(0);
      assertEquals(key, element.path("processInstanceKey").asText());
      assertEquals(elementId, element.path("elementId").asText());
      assertFalse(element.path("hasIncident").asBoolean(), "Engine incident in " + element);
      final JsonNode start = element.path("startDate");
      final JsonNode end = element.path("endDate");
      if (!start.isTextual() || start.asText().isBlank() || !end.isTextual() || end.asText().isBlank()) return null;
      return new ElementTiming(OffsetDateTime.parse(start.asText()).toInstant(), OffsetDateTime.parse(end.asText()).toInstant());
    });
  }

  private void variable(String key, String name, String expected) throws Exception {
    eventually("engine variable " + name + "=" + expected + " in " + key, () -> {
      for (final JsonNode item : variables(key, name)) {
        final JsonNode actual = mapper.readTree(item.path("value").asText());
        final JsonNode wanted = mapper.readTree(expected);
        if (actual.isNumber() && wanted.isNumber() ? actual.decimalValue().compareTo(wanted.decimalValue()) == 0 : actual.equals(wanted)) return item;
      }
      return null;
    });
  }

  private List<JsonNode> variables(String key, String name) throws Exception {
    return search("variables", Map.of("processInstanceKey", key, "scopeKey", key, "name", name));
  }

  private List<JsonNode> search(String resource, Map<String, ?> filter) throws Exception {
    return nodes(engineRequest("POST", "/v2/" + resource + "/search", Map.of("filter", filter, "page", Map.of("limit", 100))).path("items"));
  }

  private List<JsonNode> nodes(JsonNode array) { return StreamSupport.stream(array.spliterator(), false).toList(); }

  private JsonNode engineRequest(String method, String path, Object body) throws Exception {
    final HttpRequest request = HttpRequest.newBuilder(URI.create(engine.replaceAll("/+$", "") + path)).timeout(Duration.ofSeconds(10))
      .header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
    final HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    lastResponse = method + " " + path + " -> " + response.statusCode() + ": " + response.body();
    assertEquals(200, response.statusCode(), lastResponse);
    return mapper.readTree(response.body());
  }

  @FunctionalInterface
  private interface Condition<T> { T get() throws Exception; }

  private <T> T eventually(String description, Condition<T> condition) throws Exception {
    final long deadline = System.nanoTime() + Duration.ofSeconds(150).toNanos();
    return eventually(description, deadline, condition);
  }

  private <T> T eventually(String description, long deadline, Condition<T> condition) throws Exception {
    while (System.nanoTime() < deadline) {
      final T result = condition.get();
      if (result != null) return result;
      LockSupport.parkNanos(Duration.ofMillis(250).toNanos());
    }
    throw new AssertionError("Timed out waiting for " + description + " (engine " + engine + "). Last response: " + lastResponse);
  }
}
