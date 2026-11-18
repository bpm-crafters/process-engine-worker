package dev.bpmcrafters.example.quarkus;

import java.util.UUID;

final class TestRequests {
  private TestRequests() { }

  static String validOrder() { return validOrder(UUID.randomUUID(), "10.00", "book"); }
  static String validOrder(UUID id, String price, String name) {
    return """
      {"id":"%s","shipmentAddress":{"streetLine":"Main 1","zipCode":"12345","city":"Berlin","country":"DE"},
       "invoiceAddress":{"streetLine":"Main 1","zipCode":"12345","city":"Berlin","country":"DE"},
       "orderPositions":[{"name":"%s","amount":2,"price":%s}]}
      """.formatted(id, name, price);
  }
}
