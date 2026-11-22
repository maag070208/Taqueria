package com.axzy.taqueria

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Respuesta de la Central: código HTTP y cuerpo (vacío si no trae). */
data class CentralResponse(val code: Int, val body: String)

/**
 * Llamada a la Central con el código del dispositivo. Bloqueante: llamarla fuera
 * del hilo principal. Lanza [IOException] si no hay conexión.
 */
fun centralCall(creds: CentralCredentials, method: String, path: String, body: String? = null, timeoutMs: Int = 15_000): CentralResponse {
    val conn = URL("${creds.apiBase.trimEnd('/')}/${path.trimStart('/')}").openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("Authorization", "Bearer ${creds.token}")
        conn.setRequestProperty("Accept", "application/json")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return CentralResponse(code, stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty())
    } finally {
        conn.disconnect()
    }
}
