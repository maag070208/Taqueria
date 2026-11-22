package com.axzy.taqueria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Varias cocinas: cada pantalla ve todo (espejo) o solo lo de su estación. */
class StationTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun product(id: String, name: String, category: String, mix: Boolean = false) = Product(id, name, 3000, category, mix = mix)
    private fun line(p: Product, qty: Int, mix: List<String> = emptyList()) = OrderLine(p.id, p.name, 3000, qty, mix = mix)

    private val tacoAsada = product("t-asada", "Asada", "Tacos")
    private val tacoAdobada = product("t-adobada", "Adobada", "Tacos")
    private val quesoAsada = product("q-asada", "Asada", "Quesatacos")
    private val mixto = product("t-mixto", "Mixto", "Tacos", mix = true)
    private val refresco = product("refresco", "Refresco", "Bebidas")

    private val asada = Station("s1", "Asada", products = listOf("Asada"))
    private val adobada = Station("s2", "Adobada", products = listOf("Adobada"))
    private val bebidas = Station("s3", "Bebidas", categories = listOf("Bebidas"))

    private val state = AppState(
        products = listOf(tacoAsada, tacoAdobada, quesoAsada, mixto, refresco),
        stations = listOf(asada, adobada, bebidas),
    )

    private val order = Order(
        "o1", 1, "a1", "5", 1,
        listOf(
            line(tacoAsada, 3), line(tacoAdobada, 2), line(quesoAsada, 1),
            line(mixto, 1, mix = listOf("Asada", "Adobada")), line(refresco, 2),
        ),
        OrderStatus.PENDING, 0, clientKey = "k",
    )

    @Test fun `en todas se ve el ticket completo`() {
        val all = state.ordersForStation(null, listOf(order)).single()
        assertEquals(5, all.order.lines.size)
        assertEquals(0, all.elsewhere)
    }

    @Test fun `el taquero de asada ve la asada de todas las categorias y el mixto`() {
        val mine = state.ordersForStation("s1", listOf(order)).single()
        assertEquals(listOf("t-asada", "q-asada", "t-mixto"), mine.order.lines.map { it.productId })
        assertEquals(2, mine.elsewhere)
    }

    @Test fun `el de adobada trabaja la misma orden en paralelo`() {
        val mine = state.ordersForStation("s2", listOf(order)).single()
        assertEquals(listOf("t-adobada", "t-mixto"), mine.order.lines.map { it.productId })
    }

    @Test fun `por categoria y sin nada para la estacion la orden no aparece`() {
        assertEquals(listOf("refresco"), state.ordersForStation("s3", listOf(order)).single().order.lines.map { it.productId })
        val soloAsada = order.copy(lines = listOf(line(tacoAsada, 1)))
        assertEquals(emptyList<StationOrder>(), state.ordersForStation("s3", listOf(soloAsada)))
    }

    @Test fun `una estacion borrada vuelve a mostrar todo`() {
        assertEquals(5, state.ordersForStation("ya-no-existe", listOf(order)).single().order.lines.size)
    }

    @Test fun `el admin crea, edita y borra estaciones con validacion`() {
        val store = OrderStore(File(tmp.root, "t.json"))
        store.saveStation(Station("", "Asada", products = listOf("Asada")))
        val id = store.state.value.stations.single().id
        assertThrows(ValidationException::class.java) { store.saveStation(Station("", "asada", products = listOf("Adobada"))) }
        assertThrows(ValidationException::class.java) { store.saveStation(Station("", "Vacía")) }
        store.saveStation(Station(id, "Plancha", products = listOf("Asada", "Adobada")))
        assertEquals(listOf("Plancha"), store.state.value.stations.map { it.name })
        store.setKitchenName("Cocina 2")
        assertEquals("Cocina 2", store.state.value.kitchenName)
        store.deleteStation(id)
        assertEquals(emptyList<Station>(), store.state.value.stations)
    }
}
