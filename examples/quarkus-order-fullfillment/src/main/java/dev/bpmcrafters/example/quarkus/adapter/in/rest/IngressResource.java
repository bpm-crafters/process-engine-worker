package dev.bpmcrafters.example.quarkus.adapter.in.rest;

import dev.bpmcrafters.example.quarkus.adapter.OrderMapper;
import dev.bpmcrafters.example.quarkus.adapter.OrderWire;
import dev.bpmcrafters.example.quarkus.application.port.in.ReceiveOrder;
import dev.bpmcrafters.example.quarkus.domain.Order;
import dev.bpmcrafters.example.quarkus.domain.Validation;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@Path("/ingress")
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class IngressResource {
  private final ReceiveOrder orders;

  public IngressResource(ReceiveOrder orders) { this.orders = orders; }

  public record OrderRequest(UUID id, OrderWire.Address shipmentAddress, OrderWire.Address invoiceAddress,
                             List<OrderWire.Position> orderPositions) { }

  @POST
  @Path("/order-received")
  public Response receive(OrderRequest request) throws Exception {
    final Order order;
    try {
      Validation.required(request, "Order");
      order = OrderMapper.toDomain(new OrderWire(request.id(), request.orderPositions(), request.shipmentAddress(), request.invoiceAddress()));
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException(exception.getMessage(), exception);
    }
    orders.receive(new ReceiveOrder.Command(order));
    return Response.noContent().build();
  }
}
