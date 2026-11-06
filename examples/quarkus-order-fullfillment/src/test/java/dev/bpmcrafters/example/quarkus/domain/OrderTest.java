package dev.bpmcrafters.example.quarkus.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import static dev.bpmcrafters.example.quarkus.DomainTestData.*;
import static org.assertj.core.api.Assertions.*;

class OrderTest {
  @Test
  void decimalTotalsRoundOnceAfterSummingHalfEven() {
    assertThat(order("0.10", "book").total()).isEqualTo(new BigDecimal("0.20"));
    final List<OrderPosition> positions = List.of(new OrderPosition("a", 1, new BigDecimal("0.005")),
      new OrderPosition("b", 1, new BigDecimal("0.005")));
    assertThat(new Order(UUID.randomUUID(), positions, address(), address()).total()).isEqualTo(new BigDecimal("0.01"));
    assertThat(order("0.0025", "book").total()).isEqualTo(new BigDecimal("0.00"));
    assertThat(order("0.0075", "book").total()).isEqualTo(new BigDecimal("0.02"));
  }

  @Test
  void aggregateCopiesPositionsAndUsesExactInventoryName() {
    final List<OrderPosition> positions = new ArrayList<>(order().orderPositions());
    final Order aggregate = new Order(UUID.randomUUID(), positions, address(), address());
    positions.clear();
    assertThat(aggregate.orderPositions()).hasSize(1);
    assertThatThrownBy(() -> aggregate.orderPositions().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThat(aggregate.requiresReplenishment()).isFalse();
    assertThat(order("0", "out-of-stock").requiresReplenishment()).isTrue();
    assertThat(order("0", " out-of-stock").requiresReplenishment()).isFalse();
    assertThat(order("0", "OUT-OF-STOCK").requiresReplenishment()).isFalse();
  }

  @Test
  void aggregateRejectsMissingIdentityAddressesAndPositions() {
    final Order valid = order();
    assertThatIllegalArgumentException().isThrownBy(() -> new Order(null, valid.orderPositions(), address(), address()));
    assertThatIllegalArgumentException().isThrownBy(() -> new Order(valid.orderId(), null, address(), address()));
    assertThatIllegalArgumentException().isThrownBy(() -> new Order(valid.orderId(), List.of(), address(), address()));
    assertThatIllegalArgumentException().isThrownBy(() -> new Order(valid.orderId(), Arrays.asList((OrderPosition) null), address(), address()));
    assertThatIllegalArgumentException().isThrownBy(() -> new Order(valid.orderId(), valid.orderPositions(), null, address()));
    assertThatIllegalArgumentException().isThrownBy(() -> new Order(valid.orderId(), valid.orderPositions(), address(), null));
  }

  @Test
  void valuesValidateWithoutTrimmingAndAllowZeroPrice() {
    for (final String text : Arrays.asList(null, "", "  \t")) {
      assertThatIllegalArgumentException().isThrownBy(() -> new Address(text, "z", "c", "DE"));
      assertThatIllegalArgumentException().isThrownBy(() -> new Address("s", text, "c", "DE"));
      assertThatIllegalArgumentException().isThrownBy(() -> new Address("s", "z", text, "DE"));
      assertThatIllegalArgumentException().isThrownBy(() -> new Address("s", "z", "c", text));
      assertThatIllegalArgumentException().isThrownBy(() -> new OrderPosition(text, 1, BigDecimal.ZERO));
    }
    for (final Integer quantity : Arrays.asList(null, 0, -1)) {
      assertThatIllegalArgumentException().isThrownBy(() -> new OrderPosition("book", quantity, BigDecimal.ZERO));
    }
    assertThatIllegalArgumentException().isThrownBy(() -> new OrderPosition("book", 1, null));
    assertThatIllegalArgumentException().isThrownBy(() -> new OrderPosition("book", 1, new BigDecimal("-0.01")));
    assertThat(new OrderPosition(" book ", 1, BigDecimal.ZERO).name()).isEqualTo(" book ");
    assertThat(new Address(" s ", "z", "c", "DE").streetLine()).isEqualTo(" s ");
  }

  @Test
  void paymentRejectsRoundedFortyTwoAndReturnsOrderReferenceOtherwise() {
    final Order rejected = order("21.001", "book");
    assertThat(PaymentResult.forOrder(rejected)).isEqualTo(new PaymentResult(false, rejected.orderId().toString(),
      PaymentResult.REJECTION_REASON, new BigDecimal("42.00")));
    final Order accepted = order("21.01", "book");
    assertThat(PaymentResult.forOrder(accepted).received()).isTrue();
    assertThat(PaymentResult.forOrder(accepted).reference()).isEqualTo(accepted.orderId().toString());
    assertThat(PaymentResult.forOrder(accepted).failureReason()).isNull();
  }

  @Test
  void notificationValidatesRequiredFieldsAndCopiesItems() {
    final Notification valid = failed("1");
    assertThatIllegalArgumentException().isThrownBy(() -> new Notification(null, valid.type(), valid.order(), "ref", "reason", BigDecimal.ONE, null));
    assertThatIllegalArgumentException().isThrownBy(() -> new Notification("1", null, valid.order(), "ref", "reason", BigDecimal.ONE, null));
    assertThatIllegalArgumentException().isThrownBy(() -> new Notification("1", valid.type(), null, "ref", "reason", BigDecimal.ONE, null));
    assertThatIllegalArgumentException().isThrownBy(() -> new Notification("1", valid.type(), valid.order(), null, "reason", BigDecimal.ONE, null));
    assertThatIllegalArgumentException().isThrownBy(() -> new Notification("1", valid.type(), valid.order(), "ref", null, BigDecimal.ONE, null));
    assertThatIllegalArgumentException().isThrownBy(() -> new Notification("1", valid.type(), valid.order(), "ref", "reason", null, null));
    assertThatIllegalArgumentException().isThrownBy(() -> new Notification("1", Notification.Type.DELAYED_INVENTORY, valid.order(), null, null, null, null));
    final List<String> items = new ArrayList<>(List.of("book"));
    final Notification delayed = new Notification("1", Notification.Type.DELAYED_INVENTORY, valid.order(), null, null, null, items);
    items.clear();
    assertThat(delayed.items()).containsExactly("book");
  }
}
