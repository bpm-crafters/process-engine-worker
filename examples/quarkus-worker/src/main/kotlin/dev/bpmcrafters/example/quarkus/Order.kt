package dev.bpmcrafters.example.quarkus

import jakarta.ws.rs.BadRequestException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

data class Address(val streetLine: String, val zipCode: String, val city: String, val country: String)
data class OrderPosition(val name: String, val amount: Int, val price: BigDecimal)
data class Order(val orderId: UUID, val orderPositions: List<OrderPosition>, val shippingAddress: Address, val invoiceAddress: Address) {
  fun total(): BigDecimal = orderPositions.fold(BigDecimal.ZERO) { total, item ->
    total + item.price.multiply(item.amount.toBigDecimal())
  }.setScale(2, RoundingMode.HALF_EVEN)
}

// Nullable transport fields make missing and explicit null input a deliberate HTTP 400.
data class AddressRequest(val streetLine: String? = null, val zipCode: String? = null, val city: String? = null, val country: String? = null) {
  fun toDomain() = Address(required(streetLine), required(zipCode), required(city), required(country))
}

data class PositionRequest(val name: String? = null, val amount: Int? = null, val price: BigDecimal? = null) {
  fun toDomain(): OrderPosition {
    val quantity = amount?.takeIf { it > 0 } ?: throw BadRequestException("Positive amount required")
    val cost = price?.takeIf { it.signum() >= 0 } ?: throw BadRequestException("Non-negative price required")
    return OrderPosition(required(name), quantity, cost)
  }
}

data class OrderRequest(
  val id: UUID? = null, val shipmentAddress: AddressRequest? = null,
  val invoiceAddress: AddressRequest? = null, val orderPositions: List<PositionRequest?>? = null
) {
  fun toDomain() = Order(
    id ?: throw BadRequestException("id required"),
    orderPositions?.takeIf { it.isNotEmpty() }?.map { (it ?: throw BadRequestException("Null position")).toDomain() }
      ?: throw BadRequestException("orderPositions required"),
    shipmentAddress?.toDomain() ?: throw BadRequestException("shipmentAddress required"),
    invoiceAddress?.toDomain() ?: throw BadRequestException("invoiceAddress required")
  )
}

private fun required(value: String?): String = value?.takeUnless { it.isBlank() } ?: throw BadRequestException("Non-blank address/name required")
