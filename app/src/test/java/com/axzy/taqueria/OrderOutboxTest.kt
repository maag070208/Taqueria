package com.axzy.taqueria

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class OrderOutboxTest {
    @get:Rule val tmp = TemporaryFolder()

    private var saved: String? = null
    private fun outbox() = OrderOutbox(load = { saved }, save = { saved = it })

    private fun queued(key: String, table: String = "3", lines: List<LineRequest>) =
        QueuedOrder(key, table, null, lines, queuedAt = 0)

    @Test fun `la cola sobrevive a cerrar la app`() {
        outbox().add(queued("k1", lines = listOf(LineRequest("asada", 2))))
        val reopened = outbox()
        assertEquals(listOf("k1"), reopened.items.value.map { it.key })
    }

    @Test fun `al volver la conexion se manda una sola vez aunque se reintente`() = runBlocking {
        val store = OrderStore(File(tmp.root, "state.json"))
        val asada = store.state.value.products.first { it.active }
        val box = outbox()
        box.add(queued("k1", lines = listOf(LineRequest(asada.id, 2))))

        // Sin conexión: se queda en la cola.
        box.flush { throw IOException("Sin conexión con cocina") }
        assertEquals(1, box.items.value.size)

        // Cocina la recibió pero la respuesta se perdió; luego se reintenta.
        val send: suspend (QueuedOrder) -> Order = { q ->
            store.createOrder(ClientMsg.CreateOrder(0, q.key, q.table, q.accountId, q.lines, q.extras))
        }
        send(box.items.value.single())
        box.flush(send)

        assertEquals(emptyList<QueuedOrder>(), box.items.value)
        assertEquals(1, store.state.value.orders.size)
    }

    @Test fun `si cocina la rechaza queda marcada y las demas siguen`() = runBlocking {
        val box = outbox()
        box.add(queued("mala", lines = listOf(LineRequest("no-existe", 1))))
        box.add(queued("buena", "5", listOf(LineRequest("asada", 1))))
        val sent = mutableListOf<String>()
        box.flush { q ->
            if (q.key == "mala") throw ValidationException("Producto no disponible")
            sent += q.key
            Order("o1", 7, "a1", q.table, 1, emptyList(), OrderStatus.PENDING, 0, clientKey = q.key)
        }
        assertEquals(listOf("buena"), sent)
        val left = box.items.value.single()
        assertEquals("mala", left.key)
        assertEquals("Producto no disponible", left.error)

        // Una rechazada ya no se reintenta sola.
        box.flush { error("no debería mandarse") }
        assertEquals(1, box.items.value.size)
        box.remove("mala")
        assertNull(box.items.value.firstOrNull())
    }
}
