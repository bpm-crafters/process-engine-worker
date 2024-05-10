package dev.bpmcrafters.example.order.fulfillment.order.adapter.in.rest;

import java.math.BigDecimal;

/**
 * DTO to inform about failed payment.
 */
public record FailedPaymentDto(
  String paymentReference,
  String orderid,
  BigDecimal amount
) { }
