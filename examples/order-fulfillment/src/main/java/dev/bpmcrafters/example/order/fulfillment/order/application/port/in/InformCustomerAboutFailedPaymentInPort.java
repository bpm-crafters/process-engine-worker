package dev.bpmcrafters.example.order.fulfillment.order.application.port.in;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;

/**
 * Use case port for informing customers about failed payment.
 */
public interface InformCustomerAboutFailedPaymentInPort {
  /**
   * Load details about failed payment.
   *
   * @param taskId id of the user task.
   * @return
   */
  Order loadFailedPaymentDetails(String taskId);

  /**
   * Confirm failed payment.
   * @param taskId id of the user task.
   */
  void confirmFailedPayment(String taskId);
}
