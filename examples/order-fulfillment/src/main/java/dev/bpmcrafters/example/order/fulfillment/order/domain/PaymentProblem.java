package dev.bpmcrafters.example.order.fulfillment.order.domain;

import java.math.BigDecimal;

/**
 * Indicates a payment problem.
 * @param paymentReference payment reference.
 * @param rejectionReason reason for rejection.
 * @param amount payment amount.
 */
public record PaymentProblem(
  String paymentReference,
  String rejectionReason,
  BigDecimal amount
)
{ }
