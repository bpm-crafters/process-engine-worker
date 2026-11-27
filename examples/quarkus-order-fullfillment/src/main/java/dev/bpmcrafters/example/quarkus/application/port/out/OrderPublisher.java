package dev.bpmcrafters.example.quarkus.application.port.out;

import dev.bpmcrafters.example.quarkus.domain.Order;

public interface OrderPublisher {
  void publish(Order order) throws Exception;
}
