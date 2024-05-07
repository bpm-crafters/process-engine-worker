package dev.bpmcrafters.example.order.fulfillment.order.adapter.in.rest;

import dev.bpmcrafters.example.order.fulfillment.order.application.port.in.ReceiveOrderInPort;
import dev.bpmcrafters.example.order.fulfillment.order.domain.Address;
import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import dev.bpmcrafters.example.order.fulfillment.order.domain.OrderPosition;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.ResponseEntity.noContent;

@RestController
@RequestMapping(value = {"/ingress"})
@RequiredArgsConstructor
public class IngressResource {

  private final ReceiveOrderInPort orderReceivedInPort;


  @PostMapping(value = "/order-received", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> orderReceived(@RequestBody @Valid OrderDto orderDto) {
    orderReceivedInPort.orderReceived(
      new Order(
        orderDto.id(),
        orderDto.orderPositions().stream().map(p -> new OrderPosition(p.name(), p.amount(), p.price())).toList(),
        new Address(
          orderDto.shipmentAddress().streetLine(),
          orderDto.shipmentAddress().zipCode(),
          orderDto.shipmentAddress().city(),
          orderDto.shipmentAddress().country()
        ),
        new Address(
          orderDto.invoiceAddress().streetLine(),
          orderDto.invoiceAddress().zipCode(),
          orderDto.invoiceAddress().city(),
          orderDto.invoiceAddress().country()
        )
      )
    );
    return noContent().build();
  }
}
