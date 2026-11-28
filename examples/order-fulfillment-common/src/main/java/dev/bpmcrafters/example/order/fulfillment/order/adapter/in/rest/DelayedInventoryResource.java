package dev.bpmcrafters.example.order.fulfillment.order.adapter.in.rest;

import dev.bpmcrafters.example.order.fulfillment.order.application.port.in.InformCustomerAboutDelayedInventoryInPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Inbound REST adapter for informing customer about inventory delay.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/delayed-inventory")
public class DelayedInventoryResource {

  private final InformCustomerAboutDelayedInventoryInPort informCustomerAboutDelayedInventoryInPort;

  @GetMapping(value = "/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<DelayedInventoryDto> loadDelayedInventoryInfo(@PathVariable("taskId") String taskId) {
    var inventoryDelay = informCustomerAboutDelayedInventoryInPort.loadDelayedInventoryDetails(taskId);
    return ResponseEntity.ok(
      new DelayedInventoryDto(
        inventoryDelay.orderId(),
        inventoryDelay.items()
      )
    );
  }

  @PostMapping("/{taskId}")
  public ResponseEntity<Void> confirmFailedPayment(@PathVariable("taskId") String taskId) {
    informCustomerAboutDelayedInventoryInPort.confirmDelayedInventory(taskId);
    return ResponseEntity.noContent().build();
  }

}
