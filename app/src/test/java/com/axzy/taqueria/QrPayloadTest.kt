package com.axzy.taqueria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parser del QR de activación de Axzy Central (contrato §6.1). */
class QrPayloadTest {
    private fun valid(raw: String): QrPayload {
        val result = QrPayload.parse(raw)
        assertTrue("Se esperaba un QR válido: $result", result is QrResult.Valid)
        return (result as QrResult.Valid).payload
    }

    private fun invalid(raw: String): String {
        val result = QrPayload.parse(raw)
        assertTrue("Se esperaba un QR inválido: $result", result is QrResult.Invalid)
        return (result as QrResult.Invalid).message
    }

    @Test
    fun jsonDelContratoConServidorYProducto() {
        val qr = valid("""{"v":1,"u":"https://central-api.axzy.dev/api/v1","c":"axc_abc123","p":"TAQUERIA"}""")
        assertEquals("axc_abc123", qr.code)
        assertEquals("https://central-api.axzy.dev/api/v1", qr.apiUrl)
        assertEquals("TAQUERIA", qr.product)
    }

    @Test
    fun jsonSinUrlUsaElServidorCompilado() {
        val qr = valid("""{"v":1,"c":"axc_abc123","p":"TAQUERIA"}""")
        assertEquals("axc_abc123", qr.code)
        assertNull(qr.apiUrl)
    }

    @Test
    fun jsonSinProductoEsValido() {
        val qr = valid("""{"v":1,"u":"http://10.0.2.2:4100/api/v1","c":"axc_abc123"}""")
        assertEquals("axc_abc123", qr.code)
        assertEquals("http://10.0.2.2:4100/api/v1", qr.apiUrl)
        assertNull(qr.product)
    }

    @Test
    fun jsonConLlavesExtraLasIgnora() {
        val qr = valid("""{"v":1,"c":"axc_abc123","p":"TAQUERIA","extra":"lo que sea"}""")
        assertEquals("axc_abc123", qr.code)
    }

    @Test
    fun quitaLaDiagonalFinalDeLaUrl() {
        assertEquals("https://central-api.axzy.dev/api/v1", valid("""{"v":1,"u":"https://central-api.axzy.dev/api/v1/","c":"axc_1"}""").apiUrl)
    }

    @Test
    fun productoAjenoDaMensajeClaro() {
        val message = invalid("""{"v":1,"u":"https://central-api.axzy.dev/api/v1","c":"axc_abc123","p":"PANADERIA"}""")
        assertTrue(message, message.contains("PANADERIA"))
        assertTrue(message, message.contains("producto", ignoreCase = true))
    }

    @Test
    fun versionDistintaSeRechaza() {
        val message = invalid("""{"v":2,"c":"axc_abc123"}""")
        assertTrue(message, message.contains("versión", ignoreCase = true))
    }

    @Test
    fun versionFaltanteSeRechaza() {
        val message = invalid("""{"c":"axc_abc123"}""")
        assertTrue(message, message.contains("versión", ignoreCase = true))
    }

    @Test
    fun urlInvalidaSeRechaza() {
        assertTrue(invalid("""{"v":1,"u":"ftp://central-api.axzy.dev/api/v1","c":"axc_abc123"}""").contains("inválida"))
        assertTrue(invalid("""{"v":1,"u":"no es una url","c":"axc_abc123"}""").contains("inválida"))
        assertTrue(invalid("""{"v":1,"u":"https://","c":"axc_abc123"}""").contains("inválida"))
    }

    @Test
    fun codigoSueltoAxcSeAcepta() {
        val qr = valid("axc_9f8e7d6c5b4a")
        assertEquals("axc_9f8e7d6c5b4a", qr.code)
        assertNull(qr.apiUrl)
        assertNull(qr.product)
    }

    @Test
    fun codigoSueltoRecortaEspacios() {
        assertEquals("axc_abc", valid("  axc_abc  ").code)
    }

    @Test
    fun basuraSeRechaza() {
        assertTrue(invalid("hola mundo").contains("no es de Axzy Central"))
        assertTrue(invalid("""{"esto":"no es un qr"}""").contains("no es de Axzy Central"))
        assertTrue(invalid("{").contains("no es de Axzy Central"))
        assertTrue(invalid("https://central-api.axzy.dev/api/v1").contains("no es de Axzy Central"))
        assertTrue(invalid("axc_").contains("no es de Axzy Central"))
        assertTrue(invalid("").contains("vacío"))
    }

    @Test
    fun codigoVacioEnJsonSeRechaza() {
        assertTrue(invalid("""{"v":1,"u":"https://central-api.axzy.dev/api/v1","c":"  "}""").contains("código"))
    }
}
