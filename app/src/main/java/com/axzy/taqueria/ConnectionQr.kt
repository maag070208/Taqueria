package com.axzy.taqueria

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject

/**
 * QR que muestra la cocina (app maestra) para que la mesera se conecte sin
 * buscar por mDNS ni escribir la IP a mano.
 *
 * Formato (JSON): `{"v":1,"host":"192.168.1.20","port":7777}`. Con la zona de la
 * cocina activa trae también la red: `"ssid":"…","pass":"…"`, y la mesera se une sola.
 * También se acepta el esquema compacto `taqueria://192.168.1.20:7777`.
 */
@Serializable
data class ConnectionQrPayload(
    val v: Int = 1,
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val ssid: String? = null,
    val pass: String? = null,
)

/** Red WiFi a la que hay que unirse para llegar a la cocina (la zona que abre la cocina). */
data class KitchenWifi(val ssid: String, val pass: String)

object ConnectionQr {
    sealed interface Result {
        data class Valid(val host: String, val port: Int, val wifi: KitchenWifi? = null) : Result
        data class Invalid(val message: String) : Result
    }

    private val hostRegex = Regex("^[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?$")

    /** Texto que se dibuja dentro del QR de la cocina. */
    fun encode(host: String, port: Int = DEFAULT_PORT, wifi: KitchenWifi? = null): String {
        val clean = host.trim()
        require(clean.isNotEmpty()) { "Host requerido" }
        return AppJson.encodeToString(ConnectionQrPayload(v = 1, host = clean, port = port, ssid = wifi?.ssid, pass = wifi?.pass))
    }

    fun parse(raw: String): Result {
        val text = raw.trim()
        if (text.isEmpty()) return Result.Invalid("El QR está vacío")
        val candidate = when {
            text.startsWith("{") -> text
            text.startsWith("taqueria://", ignoreCase = true) ->
                text.substringAfter("://").trim().ifEmpty { return Result.Invalid("El QR no trae una dirección") }
            else -> return Result.Invalid("Este QR no es de conexión de Komandera")
        }
        if (candidate.startsWith("{")) {
            // Un QR de activación de Axzy Central trae u/c/p: no es de conexión.
            val obj = try {
                AppJson.parseToJsonElement(candidate).jsonObject
            } catch (e: Exception) {
                return Result.Invalid("QR de conexión inválido")
            }
            if (obj.containsKey("u") || obj.containsKey("c") || obj.containsKey("p")) {
                return Result.Invalid("Este QR no es de conexión de Komandera")
            }
            val payload = try {
                AppJson.decodeFromString<ConnectionQrPayload>(candidate)
            } catch (e: Exception) {
                return Result.Invalid("QR de conexión inválido")
            }
            if (payload.v != 1) return Result.Invalid("Versión de QR no compatible")
            val ssid = payload.ssid?.takeIf { it.isNotEmpty() }
            val wifi = when {
                ssid == null -> null
                ssid.length > 32 || (payload.pass?.length ?: 0) !in 8..63 -> return Result.Invalid("La red del QR no es válida")
                else -> KitchenWifi(ssid, payload.pass!!)
            }
            return when (val r = validate(payload.host, payload.port)) {
                is Result.Valid -> r.copy(wifi = wifi)
                is Result.Invalid -> r
            }
        }
        // taqueria://host:puerto (puerto opcional)
        val host = candidate.substringBeforeLast(":", candidate)
        val portText = candidate.substringAfterLast(":", "")
        if (host.isBlank()) return Result.Invalid("El QR no trae la dirección de cocina")
        val port = if (portText.isBlank()) DEFAULT_PORT else portText.toIntOrNull()
            ?: return Result.Invalid("Puerto inválido")
        return validate(host, port)
    }

    private fun validate(host: String, port: Int): Result {
        val clean = host.trim()
        if (clean.length > 100 || !hostRegex.matches(clean)) {
            return Result.Invalid("La dirección de cocina no es válida")
        }
        if (port !in 1..65535) return Result.Invalid("Puerto inválido (debe ser 1–65535)")
        return Result.Valid(clean, port)
    }
}
