package dev.bpmcrafters.example.quarkus.adapter.out.simulation;

import dev.bpmcrafters.example.quarkus.application.port.out.FulfillmentServices;
import dev.bpmcrafters.example.quarkus.domain.Order;
import dev.bpmcrafters.example.quarkus.domain.PaymentResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ExampleFulfillmentServices implements FulfillmentServices {
  private static final Logger LOG = Logger.getLogger(ExampleFulfillmentServices.class);
  private final long delaySeconds;

  public ExampleFulfillmentServices(@ConfigProperty(name = "example.inventory.replenishment-seconds", defaultValue = "60") long delaySeconds) {
    this.delaySeconds = delaySeconds;
  }

  @Override
  public PaymentResult collectPayment(Order order) { return PaymentResult.forOrder(order); }

  @Override
  public void replenish(Order order) throws InterruptedException {
    LOG.infof("INVENTORY: Replenishing order %s, waiting %s seconds", order.orderId(), delaySeconds);
    Thread.sleep(Math.multiplyExact(delaySeconds, 1000));
    LOG.infof("INVENTORY: Replenished order %s", order.orderId());
  }

  @Override
  public void ship(Order order) { LOG.infof("SHIPPING: Shipping order %s to %s", order.orderId(), order.shippingAddress()); }
}
