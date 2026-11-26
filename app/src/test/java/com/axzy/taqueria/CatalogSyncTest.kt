package com.axzy.taqueria

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Central de mentiras con las reglas de `routes/catalog.ts`: versiones, baseVersion, 409 y ETag. */
private class FakeCentral {
    val versions = mutableListOf<RemoteCatalog>()
    var calls = 0
    val current get() = versions.lastOrNull()

    fun saveFromConsole(data: Catalog) {
        versions += RemoteCatalog(versions.size + 1, "central", "yo", "", data)
    }

    fun call(method: String, headers: Map<String, String>, body: String?): CentralResponse {
        calls++
        val cur = current
        return when (method) {
            "GET" -> when {
                cur == null -> CentralResponse(404, """{"code":"NO_CATALOG"}""")
                headers["If-None-Match"] == "\"v${cur.version}\"" -> CentralResponse(304, "")
                else -> CentralResponse(200, AppJson.encodeToString(cur))
            }
            "PUT" -> {
                val save = AppJson.decodeFromString<CatalogSave>(body!!)
                if (save.baseVersion != (cur?.version ?: 0)) {
                    CentralResponse(409, AppJson.encodeToString(CatalogConflictReply("CATALOG_CONFLICT", cur)))
                } else if (cur != null && cur.data.fingerprint() == save.data.fingerprint()) {
                    CentralResponse(200, AppJson.encodeToString(CatalogSaveReply(false, cur)))
                } else {
                    val next = RemoteCatalog(versions.size + 1, "cocina", "Cocina", "", save.data)
                    versions += next
                    CentralResponse(200, AppJson.encodeToString(CatalogSaveReply(true, next)))
                }
            }
            else -> error(method)
        }
    }
}

class CatalogSyncTest {
    @get:Rule val tmp = TemporaryFolder()

    private val central = FakeCentral()
    private var mark: CatalogMark? = null
    private val aside = mutableListOf<Catalog>()
    private val creds = CentralCredentials("http://central", "CODIGO-1")
    private lateinit var store: OrderStore
    private lateinit var sync: CatalogSync

    private fun newPhone() {
        store = OrderStore(tmp.newFile("taqueria-${System.nanoTime()}.json").also { it.delete() })
        sync = CatalogSync(
            store, { creds }, { mark }, { mark = it }, { aside += it },
            call = { _, m, _, b, h -> central.call(m, h, b) },
        )
    }

    private fun product(name: String) = store.state.value.products.first { it.name == name }
    private fun priceInCentral(name: String) = central.current!!.data.products.first { it.name == name }.priceCents
    private fun sync() = runBlocking { sync.sync() }

    @Test fun `la Central sin menu adopta el de la cocina y despues no vuelve a subir lo mismo`() {
        newPhone()
        assertTrue(sync() is CatalogSync.Result.Uploaded)
        assertEquals(1, central.current!!.version)
        assertEquals(store.state.value.catalog(), central.current!!.data)
        // Sin cambios: pregunta con ETag y recibe 304.
        assertEquals(CatalogSync.Result.UpToDate, sync())
        assertEquals(1, central.versions.size)
    }

    @Test fun `un cambio del admin sube como version nueva`() {
        newPhone()
        sync()
        store.saveProduct(product("Asada").copy(priceCents = 3_100, variants = emptyList()))
        assertEquals(CatalogSync.Result.Uploaded(2), sync())
        assertEquals(3_100, priceInCentral("Asada"))
    }

    @Test fun `un cambio en la Central llega a la cocina y respeta sus agotados`() {
        newPhone()
        sync()
        store.saveProduct(product("Suadero").copy(active = false))
        sync() // el agotado sube (versión 2)
        val menu = central.current!!.data
        central.saveFromConsole(
            menu.copy(products = menu.products.map {
                when (it.name) {
                    "Asada" -> it.copy(priceCents = 4_500, variants = emptyList())
                    // La Central lo trae activo (alguien lo editó con una versión vieja); manda la cocina.
                    "Suadero" -> it.copy(active = true)
                    else -> it
                }
            }),
        )
        assertEquals(CatalogSync.Result.Applied(3), sync())
        assertEquals(4_500, product("Asada").priceCents)
        assertFalse(product("Suadero").active)
        // Como aquí quedó distinto (el agotado), se sube para que la Central lo vea.
        assertEquals(CatalogSync.Result.Uploaded(4), sync())
        assertFalse(central.current!!.data.products.first { it.name == "Suadero" }.active)
    }

    @Test fun `aplicar un menu nuevo no altera una cuenta abierta`() {
        newPhone()
        sync()
        val asada = product("Asada")
        store.createOrder(ClientMsg.CreateOrder(1, "k1", "3", null, listOf(LineRequest(asada.id, 2, variant = asada.defaultVariant))))
        val before = store.state.value.accounts to store.state.value.orders
        val menu = central.current!!.data
        central.saveFromConsole(menu.copy(products = menu.products.filter { it.name != "Asada" }))
        assertEquals(CatalogSync.Result.Applied(2), sync())
        assertTrue(store.state.value.products.none { it.name == "Asada" })
        assertEquals(before, store.state.value.accounts to store.state.value.orders)
    }

    @Test fun `cambios en los dos lados terminan en el aviso y no se pierde nada`() {
        newPhone()
        sync()
        val menu = central.current!!.data
        central.saveFromConsole(menu.copy(freeExtras = menu.freeExtras + "Limones"))
        store.saveFreeExtras(store.state.value.freeExtras + "Rábanos")
        assertEquals(CatalogSync.Result.Conflict, sync())
        val conflict = sync.conflict.value
        assertNotNull(conflict)
        assertEquals(2, conflict!!.remote.version)
        // Mientras nadie elige no se sube ni se baja nada.
        assertEquals(CatalogSync.Result.Conflict, sync())
        assertEquals(2, central.versions.size)
        assertTrue("Rábanos" in store.state.value.freeExtras)

        // Se usa el de la Central: el de aquí queda apartado en el teléfono.
        runBlocking { sync.resolve(useCentral = true) }
        assertNull(sync.conflict.value)
        assertTrue("Limones" in store.state.value.freeExtras)
        assertTrue("Rábanos" in aside.single().freeExtras)
        assertEquals(CatalogSync.Result.UpToDate, sync())
    }

    @Test fun `en el aviso se puede quedar el de la cocina y la Central guarda los dos`() {
        newPhone()
        sync()
        val menu = central.current!!.data
        central.saveFromConsole(menu.copy(freeExtras = menu.freeExtras + "Limones"))
        store.saveFreeExtras(store.state.value.freeExtras + "Rábanos")
        sync()
        assertEquals(CatalogSync.Result.Uploaded(3), runBlocking { sync.resolve(useCentral = false) })
        assertTrue("Rábanos" in central.current!!.data.freeExtras)
        assertTrue("Limones" in central.versions[1].data.freeExtras) // la versión de la Central sigue en el historial
    }

    @Test fun `un telefono nuevo toma el menu que ya tiene la Central`() {
        newPhone()
        sync()
        store.saveProduct(product("Asada").copy(priceCents = 9_900, variants = emptyList()))
        sync()
        // Otro teléfono de cocina (se rompió el primero): sin cuentas ni historial.
        mark = null
        newPhone()
        assertEquals(CatalogSync.Result.Applied(2), sync())
        assertEquals(9_900, product("Asada").priceCents)
    }

    @Test fun `la primera vez con menu distinto en los dos lados y ventas aqui pregunta`() {
        newPhone()
        central.saveFromConsole(Catalog(products = listOf(Product("p1", "Dragon", 17_500, "Rollos"))))
        val asada = product("Asada")
        store.createOrder(ClientMsg.CreateOrder(1, "k1", "3", null, listOf(LineRequest(asada.id, 1, variant = asada.defaultVariant))))
        assertEquals(CatalogSync.Result.Conflict, sync())
        assertTrue(store.state.value.products.any { it.name == "Asada" })
    }

    @Test fun `un menu roto de la Central no se aplica`() {
        newPhone()
        sync()
        central.saveFromConsole(Catalog(products = listOf(Product("x", "A", 1, "C"), Product("x", "B", 1, "C"))))
        assertTrue(sync() is CatalogSync.Result.Failed)
        assertTrue(store.state.value.products.any { it.name == "Asada" })
    }

    @Test fun `otro codigo de activacion no hereda la marca del anterior`() {
        newPhone()
        sync()
        mark = mark!!.copy(account = "otro")
        // La marca no es de esta cuenta: se trata como primera vez y, con el mismo menú, solo se marca.
        assertEquals(CatalogSync.Result.UpToDate, sync())
        assertEquals(1, central.versions.size)
    }
}
