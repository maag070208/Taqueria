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
import java.io.File
import java.net.InetSocketAddress

/** Respaldo de cocina contra una Central falsa que guarda lo último que recibe. */
class BackupSyncTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var http: HttpServer
    private lateinit var creds: CentralCredentials
    private var stored: String? = null
    private var puts = 0

    @Before fun setUp() {
        http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/api/v1/backup") { ex ->
            val reply = when (ex.requestMethod) {
                "PUT" -> {
                    puts++
                    val up = AppJson.decodeFromString<BackupUpload>(ex.requestBody.readBytes().toString(Charsets.UTF_8))
                    stored = AppJson.encodeToString(BackupDownload(up.savedAt, "Tablet cocina", false, up.appVersion, up.state))
                    200 to """{"saved":true}"""
                }
                else -> stored?.let { 200 to it } ?: (404 to """{"code":"NO_BACKUP"}""")
            }
            val bytes = reply.second.toByteArray()
            ex.sendResponseHeaders(reply.first, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        http.start()
        creds = CentralCredentials("http://127.0.0.1:${http.address.port}/api/v1", "axc_prueba")
    }

    @After fun tearDown() = http.stop(0)

    private fun store(name: String) = OrderStore(File(tmp.newFolder(name), "taqueria.json"))

    @Test fun `sube solo cuando el estado cambia`() = runBlocking {
        val kitchen = store("cocina")
        val sync = BackupSync(kitchen, { creds }, "1.2.0 (5)")
        assertNull(sync.backup())
        assertNull(sync.backup())
        assertEquals(1, puts)

        kitchen.setTableCount(20)
        assertNull(sync.backup())
        assertEquals(2, puts)
    }

    @Test fun `sin licencia no sube nada`() = runBlocking {
        assertNull(BackupSync(store("cocina"), { null }, "1.2.0 (5)").backup())
        assertEquals(0, puts)
        assertEquals(BackupSync.Restore.NotActivated, BackupSync(store("otra"), { null }, "x").fetch())
    }

    @Test fun `un telefono nuevo recupera las cuentas abiertas y aparta lo que tenia`() = runBlocking {
        val newPhone = store("nuevo")
        assertEquals(BackupSync.Restore.None, BackupSync(newPhone, { creds }, "x").fetch())

        val kitchen = store("cocina")
        val asada = kitchen.state.value.products.first { it.active }
        kitchen.createOrder(ClientMsg.CreateOrder(0, "k1", "4", null, listOf(LineRequest(asada.id, 3))))
        BackupSync(kitchen, { creds }, "1.2.0 (5)").backup()

        val sync = BackupSync(newPhone, { creds }, "1.2.0 (5)")
        val found = sync.fetch() as BackupSync.Restore.Found
        assertEquals("Tablet cocina", found.backup.deviceName)
        sync.restore(found.backup)

        val s = newPhone.state.value
        assertEquals(listOf("4"), s.openAccounts().map { it.table })
        assertEquals(3, s.orders.single().lines.single().qty)
        // Lo de antes queda en un archivo aparte, y recuperar no provoca otra subida.
        assertTrue(File(tmp.root, "nuevo").list()!!.any { it.startsWith("taqueria.json.antes-de-respaldo-") })
        val before = puts
        assertNull(sync.backup())
        assertEquals(before, puts)
    }
}
