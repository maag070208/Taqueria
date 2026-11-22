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

    /** Producto por nombre dentro de una categoría (Asada está en Tacos, Quesadillas…). */
    private fun product(name: String, category: String = "Tacos") =
        store.state.value.products.first { it.name == name && it.category == category }
    private val Product.price get() = priceOf(defaultVariant)!!
    private fun order(table: String, account: String?, vararg lines: LineRequest, key: String = "k${req}", extras: List<PersonExtras> = emptyList()) =
        store.createOrder(ClientMsg.CreateOrder(++req, key, table, account, lines.toList(), extras))
    private fun pay(account: String, cents: Long, method: PayMethod = PayMethod.CASH) =
        store.addPayment(ClientMsg.AddPayment(++req, "p$req", account, cents, method))
    private fun account(id: String) = store.state.value.accounts.first { it.id == id }

    @Test fun `cinco de asada en dos preparaciones dentro de una sola orden`() {
        val asada = product("Asada").id
        val o = order("4", null,
            LineRequest(asada, 3, listOf("Con todo")),
            LineRequest(asada, 2, listOf("Con todo", "Sin salsa", "Sin mayonesa")))
        assertEquals(1, store.state.value.orders.size)
        assertEquals(listOf(3 to listOf("Con todo"), 2 to listOf("Con todo", "Sin salsa", "Sin mayonesa")), o.lines.map { it.qty to it.mods })
        assertEquals(listOf("Taco Asada", "Taco Asada"), o.lines.map { it.name })
        assertEquals(5 * 3500L, o.totalCents)
    }

    @Test fun `renglones identicos se unen y las combinables se aceptan`() {
        val adobada = product("Adobada").id
        val o = order("7", null,
            LineRequest(adobada, 1, listOf("Sin cebolla", "Sin salsa")),
            LineRequest(adobada, 1, listOf("Sin salsa", "Sin cebolla")),
            LineRequest(adobada, 2, listOf("Pura carne")))
        assertEquals(listOf(2, 2), o.lines.map { it.qty })
        assertEquals(listOf("Sin salsa", "Sin cebolla"), o.lines[0].mods)
    }

    @Test fun `opciones exclusivas contradictorias o ajenas se rechazan`() {
        val asada = product("Asada").id
        assertThrows(ValidationException::class.java) { order("1", null, LineRequest(asada, 1, listOf("Con todo", "Puro verde"))) }
        assertThrows(ValidationException::class.java) { order("1", null, LineRequest(asada, 1, listOf("Sin mayonesa", "Con mayonesa"))) }
        assertThrows(ValidationException::class.java) { order("1", null, LineRequest(asada, 1, listOf("Sin hielo"))) }
        assertThrows(ValidationException::class.java) { order("1", null, LineRequest(asada, 0)) }
        assertTrue(store.state.value.orders.isEmpty())
    }

    @Test fun `cada tortilla con su precio y la de siempre si no se elige`() {
        val asada = product("Asada")
        val o = order("1", null, LineRequest(asada.id, 2), LineRequest(asada.id, 1, variant = "Harina"))
        assertEquals(listOf("Maíz" to 3500L, "Harina" to 10000L), o.lines.map { it.variant to it.priceCents })
        assertEquals("Taco Asada · Harina", o.lines[1].label)
        // En pantalla la de siempre no se repite; la otra sí se marca.
        assertEquals(listOf("Taco Asada", "Taco Asada · Harina"), o.lines.map { store.state.value.labelOf(it) })
        // La tripa no se vende en harina.
        assertThrows(ValidationException::class.java) { order("1", null, LineRequest(product("Tripa").id, 1, variant = "Harina")) }
    }

    @Test fun `el mixto lleva de 2 a 5 carnes de su categoria`() {
        val mixto = product("Mixto", "Quesadillas")
        val o = order("2", null, LineRequest(mixto.id, 1, mix = listOf("Adobada", "Asada"), mods = listOf("Con todo")))
        // En el orden del menú, sin importar cómo se tocaron.
        assertEquals("Quesadilla Mixto (Asada + Adobada)", o.lines.single().label)
        assertEquals(9000L, o.lines.single().priceCents)
        assertThrows(ValidationException::class.java) { order("2", null, LineRequest(mixto.id, 1, mix = listOf("Asada"))) }
        // Tripa solo existe en Tacos.
        assertThrows(ValidationException::class.java) { order("2", null, LineRequest(mixto.id, 1, mix = listOf("Asada", "Tripa"))) }
        assertThrows(ValidationException::class.java) { order("2", null, LineRequest(product("Asada").id, 1, mix = listOf("Asada", "Adobada"))) }
    }

    @Test fun `orden por monto cobra lo que se pidio`() {
        val asada = product("Asada", "Órdenes")
        val o = order("Llevar", null, LineRequest(asada.id, 1, amountCents = 10_000), LineRequest(asada.id, 1, amountCents = 5_000))
        assertEquals(listOf("Orden de Asada" to 10_000L, "Orden de Asada" to 5_000L), o.lines.map { it.name to it.priceCents })
        assertEquals(15_000L, o.totalCents)
        assertThrows(ValidationException::class.java) { order("Llevar", null, LineRequest(asada.id, 1)) }
        assertThrows(ValidationException::class.java) { order("Llevar", null, LineRequest(asada.id, 1, amountCents = 50)) }
    }

    @Test fun `personas y extras gratis viajan con la orden`() {
        val asada = product("Asada").id
        val o = order("3", null,
            LineRequest(asada, 2, person = 2), LineRequest(asada, 1, person = 1),
            extras = listOf(PersonExtras(2, listOf("Tostadas", "Chiles")), PersonExtras(1, emptyList())))
        assertEquals(listOf(1, 2), o.lines.map { it.person })
        assertEquals(listOf(PersonExtras(2, listOf("Chiles", "Tostadas"))), o.extras)
        assertEquals(asadaTotal(3), o.totalCents)
        assertThrows(ValidationException::class.java) {
            order("3", null, LineRequest(asada, 1), extras = listOf(PersonExtras(1, listOf("Limones"))))
        }
    }

    private fun asadaTotal(qty: Int) = product("Asada").price * qty

    @Test fun `los atajos de Va marcan sus ajustes y se pueden mover`() {
        val s = store.state.value
        val asada = product("Asada")
        val start = s.defaultMods(asada)
        assertEquals(setOf("Con todo"), start)
        val verde = s.toggleMod(asada, start, "Puro verde")
        assertEquals(setOf("Puro verde", "Sin salsa", "Sin cebolla", "Sin cilantro"), verde)
        // Se quita un ajuste a mano y el resumen lo dice.
        val conCilantro = s.toggleMod(asada, verde, "Sin cilantro")
        assertEquals("Puro verde, con cilantro", s.shortPrep(asada, conCilantro.toList()))
        // Otro atajo reemplaza lo que marcaba el anterior; Sin mayonesa quita Con mayonesa.
        val salsa = s.toggleMod(asada, s.toggleMod(asada, conCilantro, "Con mayonesa"), "Pura salsa")
        assertEquals(setOf("Pura salsa", "Sin cebolla", "Sin cilantro", "Sin aguacate", "Sin mayonesa"), salsa)
        // Volver a "Con todo" limpia; tocarlo otra vez no lo deja vacío.
        val todo = s.toggleMod(asada, salsa, "Con todo")
        assertEquals(setOf("Con todo"), todo)
        assertEquals(todo, s.toggleMod(asada, todo, "Con todo"))
    }

    @Test fun `el taquero edita atajos, mesas y extras`() {
        val va = store.state.value.modifierGroups.first { it.name == "Va" }
        store.saveModifierGroup(va.id, va.name, va.options, true, "Con todo", va.implies + ("Puro verde" to listOf("Sin salsa", "No existe")))
        assertEquals(listOf("Sin salsa"), store.state.value.modifierGroups.first { it.id == va.id }.implies["Puro verde"])
        store.setTableCount(20)
        assertEquals(20, store.state.value.tableCount)
        assertThrows(ValidationException::class.java) { store.setTableCount(0) }
        store.saveFreeExtras(listOf(" Limones ", "Chiles", "Chiles", ""))
        assertEquals(listOf("Limones", "Chiles"), store.state.value.freeExtras)
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
        val refresco = product("Refresco", "Bebidas")
        val c1 = order("4", null, LineRequest(asada.id, 2)).accountId
        val c2 = order("4", null, LineRequest(refresco.id, 1)).accountId
        order("4", c1, LineRequest(asada.id, 1))
        val s = store.state.value
        assertEquals(listOf(1, 2), s.accounts.map { it.number })
        assertEquals(asada.price * 3, s.totalOf(c1))
        assertEquals(refresco.price, s.totalOf(c2))
    }

    @Test fun `pago parcial en efectivo y transferencia no cambia la preparacion`() {
        val asada = product("Asada")
        val o = order("4", null, LineRequest(asada.id, 3))
        val total = asada.price * 3
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
        pay(c1, asada.price)
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
        val pid = pay(o.accountId, asada.price)
        store.voidPayment(o.accountId, pid)
        val p = account(o.accountId).payments.single()
        assertTrue(p.voided)
        assertEquals(PayStatus.UNPAID, store.state.value.payStatus(account(o.accountId)))
    }

    @Test fun `cuenta cerrada pasa al historial despues del periodo de deshacer`() {
        val asada = product("Asada")
        val o = order("8", null, LineRequest(asada.id, 1))
        pay(o.accountId, asada.price)
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

    @Test fun `editar 4 de asada a 2 asada 1 adobada 1 suadero conserva la version anterior`() {
        val asada = product("Asada").id
        val o = order("4", null, LineRequest(asada, 4))
        val edited = store.editOrder(o.id, listOf(
            LineRequest(asada, 2), LineRequest(product("Adobada").id, 1), LineRequest(product("Suadero").id, 1),
        ), listOf(PersonExtras(1, listOf("Chiles"))))
        assertEquals(listOf("Taco Asada" to 2, "Taco Adobada" to 1, "Taco Suadero" to 1), edited.lines.map { it.name to it.qty })
        assertEquals(listOf(listOf(4)), edited.revisions.map { rev -> rev.map { it.qty } })
        assertEquals(listOf("Chiles"), edited.extrasOf(1))
        assertNotNull(edited.editedAt)
        assertEquals(1, store.state.value.orders.size)
        assertEquals(o.number, edited.number)
    }

    @Test fun `no se edita una orden entregada y se respeta lo agotado ya pedido`() {
        val asada = product("Asada")
        val o = order("4", null, LineRequest(asada.id, 2))
        // Se agota después de pedirlo: la orden se puede seguir editando sin perder ese renglón.
        store.saveProduct(asada.copy(active = false))
        val edited = store.editOrder(o.id, listOf(LineRequest(asada.id, 1, listOf("Sin salsa"))))
        assertEquals(listOf("Sin salsa"), edited.lines.single().mods)
        store.setOrderStatus(o.id, OrderStatus.DELIVERED)
        assertThrows(ValidationException::class.java) { store.editOrder(o.id, listOf(LineRequest(asada.id, 3))) }
    }

    @Test fun `cobrar en efectivo guarda con cuanto pagaron y entrega la orden`() {
        val asada = product("Asada")
        val o = order("5", null, LineRequest(asada.id, 4))
        val total = asada.price * 4
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
        order("3", null, LineRequest(asada, 2, listOf("Sin salsa"), variant = "Harina"))
        val reopened = OrderStore(tmp.root.resolve("db.json"), now = { clock })
        val line = reopened.state.value.orders.single().lines.single()
        assertEquals(listOf("Sin salsa"), line.mods)
        assertEquals("Harina", line.variant)
    }

    @Test fun `restaurar el menu cambia productos y conserva las ordenes`() {
        val asada = product("Asada")
        store.saveProduct(asada.copy(name = "Asada vieja", variants = emptyList(), priceCents = 2000))
        val o = order("2", null, LineRequest(asada.id, 2))
        store.setTableCount(8)
        store.resetCatalog()
        val s = store.state.value
        assertEquals(OrderStore.seed().products.map { it.category to it.name }, s.products.map { it.category to it.name })
        assertEquals(listOf("Taco Asada vieja" to 2000L), s.orders.single { it.id == o.id }.lines.map { it.name to it.priceCents })
        assertEquals(1, s.accounts.size)
        assertEquals(8, s.tableCount)
    }
}
