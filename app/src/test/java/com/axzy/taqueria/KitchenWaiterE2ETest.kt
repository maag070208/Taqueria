package com.axzy.taqueria

import android.content.ContextWrapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

/**
 * De punta a punta: el servidor real de cocina con su OrderStore en disco y meseros reales
 * conectados por TCP en localhost, hablando el mismo protocolo que en el local.
 * Solo se omite el anuncio por mDNS (sin Android, getSystemService devuelve null y se salta)
 * y el mesero se conecta con IP manual, como cuando se escanea el QR de conexión.
 */
class KitchenWaiterE2ETest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: File
    private lateinit var store: OrderStore
    private lateinit var kitchen: KitchenServer
    private var locked = false
    private val waiters = mutableListOf<WaiterClient>()
    private val context = ContextWrapper(null)

    @Before fun setUp() {
        db = File(tmp.newFolder("cocina"), "taqueria.json")
        store = OrderStore(db)
        kitchen = startKitchen(store)
    }

    @After fun tearDown() {
        waiters.forEach { it.stop() }
        kitchen.stop()
    }

    // ---------------- Arranque ----------------

    private var waiterLimit: Int? = null
    private val waiterErrors = mutableListOf<AppErrorReport>()

    private fun startKitchen(store: OrderStore, port: Int = 0) =
        KitchenServer(
            context, store, isLocked = { locked }, port = port,
            waiterLimit = { waiterLimit }, latestApp = { LatestApp(9, "9.0.0", "https://example.com/apk") },
            onWaiterErrors = { synchronized(waiterErrors) { waiterErrors += it } },
        ).also { it.start() }

    private fun port(server: KitchenServer = kitchen): Int = runBlocking {
        withTimeout(5_000) { server.localPort.first { it != null }!! }
    }

    /** Mesero conectado y con el menú de cocina ya recibido. */
    private fun waiter(port: Int = port(), id: String = key(), crashes: CrashReporter? = null): WaiterClient =
        WaiterClient(context, id, crashes).also { w ->
            waiters += w
            w.start("127.0.0.1:$port")
            await(w) { it.products.isNotEmpty() }
            awaitLink(w) { it is Link.Connected }
        }

    private fun awaitLink(w: WaiterClient, timeoutMs: Long = 5_000, check: (Link) -> Boolean): Link = runBlocking {
        withTimeout(timeoutMs) { w.link.first(check) }
    }

    /** Espera a que el estado que ve el mesero cumpla la condición (llega por broadcast). */
    private fun await(w: WaiterClient, timeoutMs: Long = 5_000, check: (AppState) -> Boolean): AppState = runBlocking {
        withTimeout(timeoutMs) { w.state.first(check) }
    }

    private fun AppState.product(name: String, category: String = "Tacos") =
        products.first { it.name == name && it.category == category }

    private fun key() = UUID.randomUUID().toString()

    // ---------------- Escenarios ----------------

    @Test fun `el plan limita las meseras a la vez y el mismo telefono no cuenta doble`() {
        waiterLimit = 2
        // Mesera A con una conexión que se quedó colgada (se fue la red sin cerrar el socket).
        val stale = java.net.Socket("127.0.0.1", port())
        stale.getOutputStream().write((AppJson.encodeToString<ClientMsg>(ClientMsg.Hello(1, "mesera-a")) + "\n").toByteArray())
        stale.getOutputStream().flush()
        waiter(id = "mesera-b")
        // La tercera se rechaza con el motivo, sin poder mandar órdenes.
        val c = WaiterClient(context, "mesera-c").also { waiters += it; it.start("127.0.0.1:${port()}") }
        val rejected = awaitLink(c) { it is Link.Rejected } as Link.Rejected
        assertTrue(rejected.message, rejected.message.contains("2 meseras"))
        assertThrows(java.io.IOException::class.java) {
            runBlocking { c.createOrder(key(), "1", null, listOf(LineRequest(store.state.value.products.first().id, 1))) }
        }
        c.stop()
        // A vuelve desde el mismo teléfono: suelta la conexión colgada y entra sin ocupar otro lugar.
        val again = waiter(id = "mesera-a")
        assertTrue(again.link.value is Link.Connected)
        stale.close()
    }

    @Test fun `la mesera le pasa sus fallas a cocina y ve el aviso de version nueva`() {
        val crashes = CrashReporter(tmp.newFolder("fallas-mesera"), "1.2.0 (5)", source = { "mesero" }, model = "Prueba")
        crashes.record(IllegalStateException("boom"))
        val w = waiter(crashes = crashes)
        runBlocking { withTimeout(5_000) { while (crashes.pending().isNotEmpty()) delay(50) } }
        assertEquals(listOf("IllegalStateException: boom"), synchronized(waiterErrors) { waiterErrors.map { it.message } })
        assertEquals("mesero", waiterErrors.single().source)
        assertEquals("9.0.0", w.latestApp.value?.versionName)
    }

    @Test fun `el mesero se conecta y recibe el menu de cocina`() {
        val w = waiter()
        assertTrue(w.link.value is Link.Connected)
        val menu = w.state.value
        assertEquals(store.state.value.products.map { it.id }, menu.products.map { it.id })
        assertTrue(menu.menuCategories().any { it.name == "Tacos" })
        assertEquals(1, kitchen.clientCount.value)
    }

    @Test fun `orden por persona llega a cocina y a todos los meseros`() {
        val a = waiter()
        val b = waiter()
        val menu = a.state.value
        val asada = menu.product("Asada")
        val adobada = menu.product("Adobada")

        val order = runBlocking {
            a.createOrder(
                key(), "5", null,
                listOf(
                    LineRequest(asada.id, 3, person = 1),
                    LineRequest(asada.id, 1, variant = "Harina", person = 1),
                    LineRequest(adobada.id, 2, person = 2),
                ),
                extras = listOf(PersonExtras(2, listOf("Cebollitas"))),
            )
        }

        // Cocina lo guardó tal cual, con el precio de cada tortilla.
        val saved = store.state.value.orders.single()
        assertEquals(order.id, saved.id)
        assertEquals(listOf(1, 2), saved.persons)
        assertEquals(3 * 3500L + 10000L + 2 * 3000L, saved.totalCents)
        assertEquals(listOf("Cebollitas"), saved.extrasOf(2))

        // El otro mesero lo ve sin pedirlo, y en pantalla la tortilla de siempre no se repite.
        val seen = await(b) { s -> s.orders.any { it.id == order.id } }
        val labels = seen.orders.single().lines.map { seen.labelOf(it) }
        assertEquals(listOf("Taco Asada", "Taco Asada · Harina", "Taco Adobada"), labels)
        assertEquals(1, seen.openAccounts().count { it.table == "5" })
    }

    @Test fun `reintentar con la misma clave no duplica la orden`() {
        val w = waiter()
        val asada = w.state.value.product("Asada")
        val k = key()
        val first = runBlocking { w.createOrder(k, "3", null, listOf(LineRequest(asada.id, 2))) }
        val retry = runBlocking { w.createOrder(k, "3", null, listOf(LineRequest(asada.id, 2))) }
        assertEquals(first.id, retry.id)
        assertEquals(1, store.state.value.orders.size)
    }

    @Test fun `agregar a una cuenta abierta y editar una orden en cocina`() {
        val a = waiter()
        val b = waiter()
        val menu = a.state.value
        val asada = menu.product("Asada")
        val first = runBlocking { a.createOrder(key(), "8", null, listOf(LineRequest(asada.id, 2))) }

        // Segunda orden a la misma cuenta: la mesa sigue con una sola cuenta.
        runBlocking { a.createOrder(key(), "8", first.accountId, listOf(LineRequest(menu.product("Suadero").id, 1))) }
        val s1 = await(b) { it.orders.size == 2 }
        assertEquals(1, s1.openAccounts().count { it.table == "8" })

        // Editar: cambia la cantidad y cocina recuerda cómo estaba.
        val edited = runBlocking { a.editOrder(first.id, listOf(LineRequest(asada.id, 4))) }
        assertEquals(4, edited.lines.single().qty)
        assertNotNull(edited.editedAt)
        assertEquals(2, edited.revisions.last().single().qty)
        await(b) { s -> s.orders.first { it.id == first.id }.lines.single().qty == 4 }
    }

    @Test fun `cobrar y entregar cierra la cuenta y deshacer el pago la regresa a cocina`() {
        val w = waiter()
        val asada = w.state.value.product("Asada")
        val order = runBlocking { w.createOrder(key(), "2", null, listOf(LineRequest(asada.id, 2))) }
        val total = 2 * 3500L

        // Más que el saldo: cocina lo rechaza y el mesero recibe el motivo.
        val tooMuch = assertThrows(ValidationException::class.java) {
            runBlocking { w.addPayment(key(), order.accountId, total + 100, PayMethod.CASH, null, deliver = true) }
        }
        assertTrue(tooMuch.message!!.contains("excede"))

        val paymentId = runBlocking { w.addPayment(key(), order.accountId, total, PayMethod.CASH, receivedCents = 10_000, deliver = true) }
        val paid = await(w) { s -> s.orders.single().status == OrderStatus.DELIVERED }
        val acc = paid.accounts.single { it.id == order.accountId }
        assertEquals(0L, paid.balanceOf(acc))
        assertEquals(PayStatus.PAID, paid.payStatus(acc))
        assertTrue(paid.openAccounts().none { it.id == order.accountId })

        runBlocking { w.voidPayment(order.accountId, paymentId) }
        val undone = await(w) { s -> s.orders.single().status == OrderStatus.PENDING }
        val reopened = undone.accounts.single { it.id == order.accountId }
        assertEquals(total, undone.balanceOf(reopened))
        assertTrue(undone.openAccounts().any { it.id == order.accountId })
    }

    @Test fun `cancelar la cuenta saca la orden de cocina y deshacer la regresa`() {
        val w = waiter()
        val asada = w.state.value.product("Asada")
        val order = runBlocking { w.createOrder(key(), "9", null, listOf(LineRequest(asada.id, 1))) }

        runBlocking { w.cancelAccount(order.accountId) }
        val cancelled = await(w) { s -> s.orders.single().status == OrderStatus.CANCELLED }
        assertNotNull(cancelled.accounts.single().cancelledAt)

        runBlocking { w.restoreAccount(order.accountId) }
        val restored = await(w) { s -> s.orders.single().status == OrderStatus.PENDING }
        assertNull(restored.accounts.single().cancelledAt)
    }

    @Test fun `cocina sin licencia activa rechaza las ordenes sin guardar nada`() {
        val w = waiter()
        val asada = w.state.value.product("Asada")
        locked = true
        val e = assertThrows(ValidationException::class.java) {
            runBlocking { w.createOrder(key(), "1", null, listOf(LineRequest(asada.id, 1))) }
        }
        assertEquals(KitchenServer.LOCKED_MESSAGE, e.message)
        assertTrue(store.state.value.orders.isEmpty())

        // En cuanto se reactiva, la misma mesera puede enviar.
        locked = false
        runBlocking { w.createOrder(key(), "1", null, listOf(LineRequest(asada.id, 1))) }
        assertEquals(1, store.state.value.orders.size)
    }

    @Test fun `ordenes invalidas regresan el motivo al mesero`() {
        val w = waiter()
        val menu = w.state.value
        val asada = menu.product("Asada")
        fun rejected(vararg lines: LineRequest) = assertThrows(ValidationException::class.java) {
            runBlocking { w.createOrder(key(), "4", null, lines.toList()) }
        }
        rejected(LineRequest(asada.id, 1, person = MAX_PERSONS + 1))
        rejected(LineRequest(menu.product("Tripa").id, 1, variant = "Harina"))
        rejected(LineRequest("no-existe", 1))
        rejected(LineRequest(asada.id, 0))
        assertTrue(store.state.value.orders.isEmpty())
        // La conexión sigue sana después de los rechazos.
        assertTrue(w.link.value is Link.Connected)
    }

    @Test fun `si cocina se reinicia el mesero se reconecta y no se pierde nada`() {
        val p = port()
        val w = waiter(p)
        val asada = w.state.value.product("Asada")
        val order = runBlocking { w.createOrder(key(), "6", null, listOf(LineRequest(asada.id, 3))) }
        // La respuesta llega antes que el estado completo; se espera a que el mesero lo tenga.
        await(w) { s -> s.orders.any { it.id == order.id } }

        // Se apaga la cocina: el mesero se entera y no puede enviar.
        kitchen.stop()
        runBlocking { withTimeout(5_000) { while (w.link.value is Link.Connected) delay(50) } }

        // Mientras tanto el mesero conserva lo último que vio (para seguir mostrando las cuentas).
        assertTrue(w.state.value.orders.any { it.id == order.id })

        // Vuelve en el mismo puerto leyendo del mismo archivo: lo guardado sigue ahí.
        store = OrderStore(db)
        assertEquals(3, store.state.value.orders.single { it.id == order.id }.lines.single().qty)
        kitchen = startKitchen(store, port = p)
        runBlocking { withTimeout(10_000) { w.link.first { it is Link.Connected } } }

        // Y se sigue trabajando sobre la misma cuenta; el mesero recibe el estado nuevo.
        runBlocking { w.createOrder(key(), "6", order.accountId, listOf(LineRequest(asada.id, 1))) }
        assertEquals(2, store.state.value.ordersOf(order.accountId).size)
        await(w) { s -> s.ordersOf(order.accountId).size == 2 }
    }
}
