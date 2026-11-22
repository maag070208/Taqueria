package com.axzy.taqueria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** QR de conexión cocina → mesera. */
class ConnectionQrTest {

    private fun valid(raw: String): ConnectionQr.Result.Valid {
        val r = ConnectionQr.parse(raw)
        assertTrue("Se esperaba válido: $r", r is ConnectionQr.Result.Valid)
        return r as ConnectionQr.Result.Valid
    }

    private fun invalid(raw: String): String {
        val r = ConnectionQr.parse(raw)
        assertTrue("Se esperaba inválido: $r", r is ConnectionQr.Result.Invalid)
        return (r as ConnectionQr.Result.Invalid).message
    }

    @Test fun codificaJsonConHostPuertoYVersion() {
        val raw = ConnectionQr.encode("192.168.1.20", 7777)
        assertTrue(raw.contains("\"v\":1"))
        assertTrue(raw.contains("\"host\":\"192.168.1.20\""))
        assertTrue(raw.contains("\"port\":7777"))
    }

    @Test fun jsonValidoDevuelveHostYPuerto() {
        val r = valid("""{"v":1,"host":"192.168.1.20","port":7777}""")
        assertEquals("192.168.1.20", r.host)
        assertEquals(7777, r.port)
    }

    @Test fun jsonSinPuertoUsa7777() {
        assertEquals(7777, valid("""{"v":1,"host":"10.0.2.15"}""").port)
    }

    @Test fun esquemaTaqueriaAceptaHostYPuerto() {
        val r = valid("taqueria://192.168.1.20:8080")
        assertEquals("192.168.1.20", r.host)
        assertEquals(8080, r.port)
    }

    @Test fun esquemaTaqueriaSinPuertoUsa7777() {
        assertEquals(7777, valid("taqueria://cocina.local").port)
    }

    @Test fun ignoraLlavesExtraDelJson() {
        val r = valid("""{"v":1,"host":"192.168.0.10","port":7777,"extra":"lo que sea"}""")
        assertEquals("192.168.0.10", r.host)
    }

    @Test fun rechazaQrDeActivacionDeLaCentral() {
        assertTrue(invalid("""{"v":1,"u":"https://central.axzy.dev/api/v1","c":"axc_abc","p":"TAQUERIA"}""")
            .contains("conexión"))
    }

    @Test fun rechazaVersionDistinta() {
        assertTrue(invalid("""{"v":2,"host":"10.0.0.2","port":7777}""").contains("Versión"))
    }

    @Test fun rechazaPuertoFueraDeRango() {
        assertTrue(invalid("""{"v":1,"host":"10.0.0.2","port":0}""").contains("Puerto"))
        assertTrue(invalid("""{"v":1,"host":"10.0.0.2","port":70000}""").contains("Puerto"))
    }

    @Test fun rechazaHostVacioOConEspacios() {
        assertTrue(invalid("""{"v":1,"host":"","port":7777}""").contains("dirección"))
        assertTrue(invalid("""{"v":1,"host":"10.0.0.2;rm -rf","port":7777}""").contains("dirección"))
    }

    @Test fun rechazaBasuraYFaltaDeDireccion() {
        assertTrue(invalid("hola mundo").contains("Tacomanda"))
        assertTrue(invalid("axc_abc123").contains("Tacomanda"))
        assertTrue(invalid("taqueria://").contains("dirección"))
        assertTrue(invalid("").contains("vacío"))
    }
}
