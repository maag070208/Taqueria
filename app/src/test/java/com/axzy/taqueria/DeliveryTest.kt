package com.axzy.taqueria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryTest {
    private fun order(id: String, account: String, status: OrderStatus, at: Long) =
        Order(id, 1, account, "1", 1, emptyList(), status, at, clientKey = id)

    private val state = AppState(
        orders = listOf(
            order("o1", "a", OrderStatus.DELIVERED, 100),
            order("o2", "b", OrderStatus.PENDING, 300),
            order("o3", "b", OrderStatus.PENDING, 200),
            order("o4", "c", OrderStatus.CANCELLED, 50),
        ),
    )

    @Test fun cuentaEntregada() = assertEquals(DeliveryState(Delivery.DELIVERED), state.deliveryOf(listOf("a")))

    @Test fun mesaDivididaQuedaPendienteDesdeLaOrdenMasVieja() =
        assertEquals(DeliveryState(Delivery.PENDING, 200), state.deliveryOf(listOf("a", "b")))

    @Test fun soloCanceladasNoCuentan() {
        val d = state.deliveryOf(listOf("c"))
        assertEquals(Delivery.NONE, d.delivery)
        assertNull(d.pendingSince)
    }
}
