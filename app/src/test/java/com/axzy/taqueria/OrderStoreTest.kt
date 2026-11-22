package com.axzy.taqueria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OrderStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private var clock = 1_000_000L
    private lateinit var store: OrderStore
    private var req = 0L

    @Before fun setUp() {
        store = OrderStore(tmp.newFile("db.json").also { it.delete() }, now = { clock })
    }

    private fun product(name: String) = store.state.value.products.first { it.name == name }
    private fun order(table: String, account: String?, vararg lines: LineRequest, key: String = "k${req}") =
        store.createOrder(ClientMsg.CreateOrder(++req, key, table, account, lines.toList()))
    private fun pay(account: String, cents: Long, method: PayMethod = PayMethod.CASH) =
        store.addPayment(ClientMsg.AddPayment(++req, "p$req", account, cents, method))
    private fun account(id: String) = store.state.value.accounts.first { it.id == id }

    @Test fun `cuatro tacos de asada se dividen en dos grupos dentro de una sola orden`() {
        val asada = product("Asada").id
        val o = order("4", null,
            LineRequest(asada, 2, listOf("Con cebolla")),
            LineRequest(asada, 2, listOf("Sin cebolla")))
        assertEquals(1, store.state.value.orders.size)
        assertEquals(listOf(2 to listOf("Con cebolla"), 2 to listOf("Sin cebolla")), o.lines.map { it.qty to it.mods })
        assertEquals(4, o.lines.sumOf { it.qty })
    }

    @Test fun `grupos identicos se unen y las modificaciones combinables se aceptan`() {
        val adobada = product("Adobada").id
        val o = order("7", null,
            LineRequest(adobada, 1, listOf("Sin salsa", "Cebolla aparte")),
            LineRequest(adobada, 1, listOf("Cebolla aparte", "Sin salsa")),
            LineRequest(adobada, 2, listOf("Bien dorado", "Con queso")))
        assertEquals(listOf(2, 2), o.lines.map { it.qty })
        assertEquals(listOf("Sin salsa", "Cebolla aparte"), o.lines[0].mods)
    }

    @Test fun `opciones exclusivas contradictorias o ajenas se rechazan`() {
        val asada = product("Asada").id
        assertThrows(ValidationException::class.java) { order("1", null, LineRequest(asada, 1, listOf("Con salsa", "Sin salsa"))) }
        assertThrows(ValidationException::class.java) { order("1", null, LineRequest(asada, 1, listOf("Sin hielo"))) }
        assertThrows(ValidationException::class.java) { order("1", null, LineRequest(asada, 0)) }
        assertTrue(store.state.value.orders.isEmpty())
    }

    @Test fun `reintento con la misma llave no duplica`() {
        val asada = product("Asada").id
        val a = order("2", null, LineRequest(asada, 1), key = "same")
        val b = order("2", null, LineRequest(asada, 1), key = "same")
        assertEquals(a.id, b.id)
        assertEquals(1, store.state.value.orders.size)
        assertEquals(1, store.state.value.accounts.size)
    }

    @Test fun `cuentas separadas en la misma mesa con saldos independientes`() {
        val asada = product("Asada")
        val refresco = product("Refresco")
        val c1 = order("4", null, LineRequest(asada.id, 2)).accountId
        val c2 = order("4", null, LineRequest(refresco.id, 1)).accountId
        order("4", c1, LineRequest(asada.id, 1))
        val s = store.state.value
        assertEquals(listOf(1, 2), s.accounts.map { it.number })
        assertEquals(asada.priceCents * 3, s.totalOf(c1))
        assertEquals(refresco.priceCents, s.totalOf(c2))
    }

    @Test fun `pago parcial en efectivo y transferencia no cambia la preparacion`() {
        val asada = product("Asada")
        val o = order("4", null, LineRequest(asada.id, 3))
        val total = asada.priceCents * 3
        pay(o.accountId, 2000)
        assertEquals(PayStatus.PARTIALLY_PAID, store.state.value.payStatus(account(o.accountId)))
        assertThrows(ValidationException::class.java) { pay(o.accountId, total) }
        pay(o.accountId, total - 2000, PayMethod.TRANSFER)
        val s = store.state.value
        assertEquals(PayStatus.PAID, s.payStatus(account(o.accountId)))
        assertEquals(OrderStatus.PENDING, s.orders.single().status)
        assertNull("pagada pero en cocina: sigue abierta", account(o.accountId).closedAt)
        store.setOrderStatus(o.id, OrderStatus.DELIVERED)
        assertNotNull(account(o.accountId).closedAt)
    }

    @Test fun `pagar una cuenta no paga las demas de la mesa`() {
        val asada = product("Asada")
        val c1 = order("4", null, LineRequest(asada.id, 1)).accountId
        val c2 = order("4", null, LineRequest(asada.id, 1)).accountId
        pay(c1, asada.priceCents)
        val s = store.state.value
        assertEquals(PayStatus.PAID, s.payStatus(account(c1)))
        assertEquals(PayStatus.UNPAID, s.payStatus(account(c2)))
    }

    @Test fun `cancelar cuenta se puede deshacer solo dentro del periodo`() {
        val asada = product("Asada").id
        val o = order("5", null, LineRequest(asada, 2))
        store.cancelAccount(o.accountId)
        assertEquals(OrderStatus.CANCELLED, store.state.value.orders.single().status)
        store.restoreAccount(o.accountId)
        assertEquals(OrderStatus.PENDING, store.state.value.orders.single().status)
        store.cancelAccount(o.accountId)
        clock += UNDO_WINDOW_MS + 1
        assertThrows(ValidationException::class.java) { store.restoreAccount(o.accountId) }
    }

    @Test fun `deshacer pago lo anula sin borrarlo`() {
        val asada = product("Asada")
        val o = order("6", null, LineRequest(asada.id, 1))
        val pid = pay(o.accountId, asada.priceCents)
        store.voidPayment(o.accountId, pid)
        val p = account(o.accountId).payments.single()
        assertTrue(p.voided)
        assertEquals(PayStatus.UNPAID, store.state.value.payStatus(account(o.accountId)))
    }

    @Test fun `cuenta cerrada pasa al historial despues del periodo de deshacer`() {
        val asada = product("Asada")
        val o = order("8", null, LineRequest(asada.id, 1))
        pay(o.accountId, asada.priceCents)
        store.setOrderStatus(o.id, OrderStatus.DELIVERED)
        store.archiveClosed()
        assertEquals(1, store.state.value.accounts.size)
        clock += UNDO_WINDOW_MS + 1
        store.archiveClosed()
        assertTrue(store.state.value.accounts.isEmpty())
        val history = tmp.root.resolve("historial.jsonl").readLines()
        assertEquals(1, history.size)
        val entry = AppJson.decodeFromString<HistoryEntry>(history.single())
        assertEquals(o.id, entry.orders.single().id)
        assertFalse(entry.account.payments.isEmpty())
    }

    @Test fun `editar 4 de asada a 2 asada 1 adobada 1 chorizo conserva la version anterior`() {
        val asada = product("Asada").id
        val o = order("4", null, LineRequest(asada, 4))
        val edited = store.editOrder(o.id, listOf(
            LineRequest(asada, 2), LineRequest(product("Adobada").id, 1), LineRequest(product("Suadero").id, 1),
        ))
        assertEquals(listOf("Asada" to 2, "Adobada" to 1, "Suadero" to 1), edited.lines.map { it.name to it.qty })
        assertEquals(listOf(listOf(4)), edited.revisions.map { rev -> rev.map { it.qty } })
        assertNotNull(edited.editedAt)
        assertEquals(1, store.state.value.orders.size)
        assertEquals(o.number, edited.number)
    }

    @Test fun `no se edita una orden entregada y se respeta lo agotado ya pedido`() {
        val asada = product("Asada")
        val o = order("4", null, LineRequest(asada.id, 2))
        // Se agota después de pedirlo: la orden se puede seguir editando sin perder ese grupo.
        store.saveProduct(asada.id, asada.name, asada.priceCents, asada.category, false, asada.emoji, asada.modifierGroupIds)
        val edited = store.editOrder(o.id, listOf(LineRequest(asada.id, 1, listOf("Sin salsa"))))
        assertEquals(listOf("Sin salsa"), edited.lines.single().mods)
        store.setOrderStatus(o.id, OrderStatus.DELIVERED)
        assertThrows(ValidationException::class.java) { store.editOrder(o.id, listOf(LineRequest(asada.id, 3))) }
    }

    @Test fun `cobrar en efectivo guarda con cuanto pagaron y entrega la orden`() {
        val asada = product("Asada")
        val o = order("5", null, LineRequest(asada.id, 4))
        val total = asada.priceCents * 4
        assertThrows(ValidationException::class.java) {
            store.addPayment(ClientMsg.AddPayment(++req, "x", o.accountId, total, PayMethod.CASH, receivedCents = total - 1))
        }
        val pid = store.addPayment(ClientMsg.AddPayment(++req, "y", o.accountId, total, PayMethod.CASH, receivedCents = 20_000, deliver = true))
        val s = store.state.value
        assertEquals(20_000L, account(o.accountId).payments.single().receivedCents)
        assertEquals(OrderStatus.DELIVERED, s.orders.single().status)
        assertNotNull("pagada y entregada: se cierra", account(o.accountId).closedAt)
        // Deshacer el pago regresa la orden a cocina.
        store.voidPayment(o.accountId, pid)
        assertEquals(OrderStatus.PENDING, store.state.value.orders.single().status)
        assertNull(account(o.accountId).closedAt)
    }

    @Test fun `los datos sobreviven a reiniciar`() {
        val asada = product("Asada").id
        order("3", null, LineRequest(asada, 2, listOf("Sin salsa")))
        val reopened = OrderStore(tmp.root.resolve("db.json"), now = { clock })
        assertEquals(listOf("Sin salsa"), reopened.state.value.orders.single().lines.single().mods)
    }
}
