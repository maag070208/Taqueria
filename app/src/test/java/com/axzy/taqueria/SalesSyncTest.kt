package com.axzy.taqueria

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetSocketAddress
import java.time.ZoneId

class SalesSyncTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cdmx = ZoneId.of("America/Mexico_City")

    // 2026-11-21 21:30 en CDMX.
    private val opened = java.time.OffsetDateTime.parse("2026-11-21T21:30-06:00").toInstant().toEpochMilli()

    private fun line(id: String, name: String, cents: Long, qty: Int, variant: String = "", person: Int = 1) =
        OrderLine(id, name, cents, qty, variant = variant, person = person)

    private fun order(id: String, lines: List<OrderLine>, status: OrderStatus = OrderStatus.DELIVERED, delivered: Long? = opened + 300_000, byPayment: String? = null) =
        Order(id, 1, "a1", "3", 1, lines, status, opened, deliveredAt = delivered, clientKey = id, deliveredByPayment = byPayment)

    @Test fun `la cuenta se reporta sin lo cancelado, en hora local y con tiempo de cocina`() {
        val entry = HistoryEntry(
            Account(
                "a1", "3", 1, opened,
                payments = listOf(
                    Payment("p1", 20_000, PayMethod.CASH, opened + 900_000, "k1"),
                    Payment("p2", 5_000, PayMethod.TRANSFER, opened + 900_000, "k2"),
                    Payment("p3", 99_900, PayMethod.CASH, opened + 900_000, "k3", voided = true),
                ),
                closedAt = opened + 1_000_000,
            ),
            listOf(
                order("o1", listOf(line("asada", "Taco Asada", 4_000, 3, "Harina"), line("pastor", "Taco Pastor", 2_500, 4, person = 2))),
                // Entregada al cobrar: no cuenta para el tiempo de cocina.
                order("o2", listOf(line("refresco", "Refresco", 3_000, 1)), byPayment = "p1"),
                order("o3", listOf(line("asada", "Taco Asada", 4_000, 10)), status = OrderStatus.CANCELLED),
            ),
        )
        val t = saleTicket(entry, mapOf("asada" to "Tacos", "pastor" to "Tacos")::get, cdmx)
        assertEquals("2026-11-21T21:30-06:00", t.openedAt)
        assertEquals(-360, t.utcOffsetMin)
        assertEquals(false, t.cancelled)
        assertEquals(12_000L + 10_000 + 3_000, t.totalCents)
        assertEquals(20_000L, t.cashCents)
        assertEquals(5_000L, t.transferCents)
        assertEquals(2, t.persons)
        assertEquals(300, t.prepSeconds)
        assertEquals(listOf("Taco Asada", "Taco Pastor", "Refresco"), t.lines.map { it.name })
        assertEquals("Harina", t.lines[0].variant)
        assertEquals("Tacos", t.lines[0].category)
        assertNull(t.lines[2].category)
    }

    @Test fun `cuenta cancelada`() {
        val entry = HistoryEntry(
            Account("a1", "3", 1, opened, cancelledAt = opened + 60_000),
            listOf(order("o1", listOf(line("asada", "Taco Asada", 4_000, 2)), status = OrderStatus.CANCELLED, delivered = null)),
        )
        val t = saleTicket(entry, { null }, cdmx)
        assertTrue(t.cancelled)
        assertEquals(0L, t.totalCents)
        assertNull(t.prepSeconds)
        assertEquals(0, t.lines.size)
    }

    // ---------------- Subida contra una Central de mentira ----------------

    private lateinit var http: HttpServer
    private val bodies = mutableListOf<String>()
    private val auths = mutableListOf<String?>()
    private var reply = 200

    @Before fun startCentral() {
        http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/api/v1/sales") { ex ->
            synchronized(bodies) {
                bodies += ex.requestBody.readBytes().decodeToString()
                auths += ex.requestHeaders.getFirst("Authorization")
            }
            val out = """{"received":1}""".toByteArray()
            ex.sendResponseHeaders(reply, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        http.start()
    }

    @After fun stopCentral() = http.stop(0)

    private val base get() = "http://127.0.0.1:${http.address.port}/api/v1"

    /** Una tienda con [n] cuentas ya archivadas. */
    private fun storeWith(n: Int): OrderStore {
        var clock = 1_000_000L
        val store = OrderStore(tmp.newFile().also { it.delete() }, now = { clock })
        val asada = store.state.value.products.first { it.name == "Asada" && it.category == "Tacos" }
        repeat(n) { i ->
            val account = store.createOrder(ClientMsg.CreateOrder(i * 10L + 1, "k$i", "${i + 1}", null, listOf(LineRequest(asada.id, 2)))).accountId
            store.addPayment(ClientMsg.AddPayment(i * 10L + 2, "p$i", account, store.state.value.totalOf(account), PayMethod.CASH, deliver = true))
        }
        clock += 24 * 60 * 60_000L
        store.archiveClosed()
        assertEquals(n, store.archived().size)
        return store
    }

    private fun sync(store: OrderStore, creds: CentralCredentials?, sent: IntArray) =
        SalesSync(store, { creds }, "1.1.0 (4)", getSent = { sent[0] }, putSent = { sent[0] = it })

    @Test fun `sube en lotes, recuerda lo subido y no repite`() = runBlocking {
        val store = storeWith(25)
        val sent = intArrayOf(0)
        val s = sync(store, CentralCredentials(base, "axc_prueba"), sent)

        assertEquals(SalesSync.Result.Sent(25), s.sync())
        assertEquals(25, sent[0])
        assertEquals(2, bodies.size)
        assertEquals("Bearer axc_prueba", auths[0])
        val first = AppJson.decodeFromString<SalesUpload>(bodies[0])
        assertEquals("1.1.0 (4)", first.appVersion)
        assertEquals(20, first.tickets.size)
        assertEquals(5, AppJson.decodeFromString<SalesUpload>(bodies[1]).tickets.size)

        assertEquals(SalesSync.Result.NothingNew, s.sync())
        assertEquals(2, bodies.size)
    }

    @Test fun `si la Central falla no avanza y reintenta despues`() = runBlocking {
        val store = storeWith(3)
        val sent = intArrayOf(0)
        val s = sync(store, CentralCredentials(base, "axc_prueba"), sent)
        reply = 500
        assertTrue(s.sync() is SalesSync.Result.Failed)
        assertEquals(0, sent[0])
        reply = 200
        assertEquals(SalesSync.Result.Sent(3), s.sync())
        assertEquals(3, sent[0])
    }

    @Test fun `sin activar no manda nada y si se borro el historial empieza de nuevo`() = runBlocking {
        val store = storeWith(2)
        assertEquals(SalesSync.Result.NotActivated, sync(store, null, intArrayOf(0)).sync())
        assertEquals(0, bodies.size)

        val sent = intArrayOf(50)
        assertEquals(SalesSync.Result.Sent(2), sync(store, CentralCredentials(base, "axc_prueba"), sent).sync())
        assertEquals(2, sent[0])
    }
}
