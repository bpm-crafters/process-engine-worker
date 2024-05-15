package dev.bpmcrafters.example.order.fulfillment.order.application.port.in;

import dev.bpmcrafters.example.order.fulfillment.order.domain.PaymentProblem;

/**
 * Use case port for informing customers about failed payment.
 */
public interface InformCustomerAboutFailedPaymentInPort {
  /**
   * Load details about failed payment.
   *
   * @param taskId id of the user task.
   * @return description of the payment problem.
   */
  PaymentProblem loadFailedPaymentDetails(String taskId);

  /**
   * Confirm failed payment.
   * @param taskId id of the user task.
   */
  void confirmFailedPayment(String taskId);
}
