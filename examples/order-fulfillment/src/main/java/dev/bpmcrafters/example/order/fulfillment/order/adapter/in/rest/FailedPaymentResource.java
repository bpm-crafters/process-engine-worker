package dev.bpmcrafters.example.order.fulfillment.order.adapter.in.rest;

import dev.bpmcrafters.example.order.fulfillment.order.application.port.in.InformCustomerAboutFailedPaymentInPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/failed-payment")
public class FailedPaymentResource {

  private final InformCustomerAboutFailedPaymentInPort informCustomerAboutFailedPaymentInPort;

  @GetMapping(value = "/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FailedPaymentDto> loadFailedPaymentInfo(@PathVariable("taskId") String taskId) {
    var order = informCustomerAboutFailedPaymentInPort.loadFailedPaymentDetails(taskId);
    return ResponseEntity.ok(
      new FailedPaymentDto(
        order.orderId().toString(),
        order.orderId().toString(),
        BigDecimal.ONE
      )
    );
  }

  @PostMapping("/{taskId}")
  public ResponseEntity<Void> confirmFailedPayment(@PathVariable("taskId") String taskId) {
    informCustomerAboutFailedPaymentInPort.confirmFailedPayment(taskId);
    return ResponseEntity.noContent().build();
  }
}
