package io.miragon.tasklist.payment

import java.math.BigDecimal
import java.time.Instant

fun aPayment(status: PaymentStatus = PaymentStatus.OPEN) = Payment(
    id = "PAY-1001",
    orderId = "ORD-1001",
    amount = BigDecimal("129.90"),
    currency = "EUR",
    customer = Customer("Alice Smith", "alice@example.com"),
    callbackUrl = "http://order-app:8081/api/v1/orders/ORD-1001/payment-result",
    status = status,
    rejectionReason = null,
    amountReceived = null,
    remindersSent = 0,
    processInstanceKey = 2251799813685328,
    createdAt = Instant.parse("2026-11-06T09:00:00Z"),
    decidedAt = null,
    reportedAt = null,
)
