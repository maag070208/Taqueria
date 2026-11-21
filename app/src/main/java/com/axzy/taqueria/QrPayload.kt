package com.axzy.taqueria

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * Datos del QR de activación que genera Axzy Central (contrato §6.1):
 *
 * `{"v":1,"u":"https://central-api.axzy.dev/api/v1","c":"axc_…","p":"TAQUERIA"}`
 *
 * `u` (la dirección del servidor) es opcional: si falta se usa la compilada en
 * la app. `p` también es opcional, pero si viene tiene que ser el producto de
 * esta app: un QR de otro producto se rechaza con un mensaje claro.
 *
 * Este archivo es Kotlin puro (sin Android) para poder probarlo en la JVM.
 */
data class QrPayload(
    val code: String,
    /** URL base del API que trajo el QR, sin `/` final; null si el QR no la trae. */
    val apiUrl: String?,
    /** Clave del producto que trajo el QR; null si no viene. */
    val product: String?,
) {
    companion object {
        const val VERSION = 1
        const val PRODUCT = "TAQUERIA"

        /** Los códigos de activación de Axzy Central empiezan así. */
        const val CODE_PREFIX = "axc_"

        private const val MAX_CODE = 200
        private const val MAX_URL = 500

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Lee el texto crudo de un QR. Acepta el JSON del contrato o un código
         * suelto (`axc_…`) por si el respaldo se imprime solo con el código.
         */
        fun parse(raw: String): QrResult {
            val text = raw.trim()
            if (text.isEmpty()) return QrResult.Invalid("El QR está vacío.")
            if (text.startsWith("{")) return parseJson(text)
            if (isBareCode(text)) return QrResult.Valid(QrPayload(text, apiUrl = null, product = null))
            return QrResult.Invalid(
                "Este QR no es de Axzy Central. Escanea el QR que muestra la Central o escribe el código a mano.",
            )
        }

        private fun parseJson(text: String): QrResult {
            val dto = try {
                json.decodeFromString<QrJson>(text)
            } catch (e: Exception) {
                return QrResult.Invalid(
                    "Este QR no es de Axzy Central. Escanea el QR que muestra la Central o escribe el código a mano.",
                )
            }
            val version = dto.v
            if (version == null) {
                // Sin versión y sin código no es un QR nuestro; con código, es un QR incompleto.
                return if (dto.c.isNullOrBlank()) {
                    QrResult.Invalid(
                        "Este QR no es de Axzy Central. Escanea el QR que muestra la Central o escribe el código a mano.",
                    )
                } else {
                    QrResult.Invalid("A este QR le falta la versión. Pide un QR nuevo a tu proveedor.")
                }
            }
            if (version != VERSION) {
                return QrResult.Invalid("Este QR es de otra versión. Actualiza la app de la taquería a la última versión.")
            }
            val code = dto.c?.trim().orEmpty()
            if (code.isEmpty() || code.length > MAX_CODE) {
                return QrResult.Invalid("El QR no trae un código de activación válido.")
            }
            val product = dto.p?.trim()?.takeIf { it.isNotEmpty() }
            if (product != null && !product.equals(PRODUCT, ignoreCase = true)) {
                return QrResult.Invalid(
                    "Este QR es para el producto \"$product\", no para Taquería. Pide a tu proveedor el QR correcto.",
                )
            }
            val rawUrl = dto.u?.trim()?.takeIf { it.isNotEmpty() }
            val apiUrl = if (rawUrl == null) null else normalizeUrl(rawUrl)
                ?: return QrResult.Invalid(
                    "El QR trae una dirección de servidor inválida. Pide un QR nuevo a tu proveedor.",
                )
            return QrResult.Valid(QrPayload(code, apiUrl, product))
        }

        private fun isBareCode(text: String): Boolean =
            text.startsWith(CODE_PREFIX) &&
                text.length > CODE_PREFIX.length &&
                text.length <= MAX_CODE &&
                text.all { it.isLetterOrDigit() || it == '_' || it == '-' }

        /** Solo http(s) con host; devuelve la URL sin `/` final. */
        private fun normalizeUrl(raw: String): String? {
            if (raw.length > MAX_URL) return null
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            if (uri.host.isNullOrBlank()) return null
            return raw.trimEnd('/')
        }

        @Serializable
        private data class QrJson(
            val v: Int? = null,
            val u: String? = null,
            val c: String? = null,
            val p: String? = null,
        )
    }
}

/** Resultado de [QrPayload.parse]: el QR es válido o hay un mensaje para mostrar. */
sealed interface QrResult {
    data class Valid(val payload: QrPayload) : QrResult
    data class Invalid(val message: String) : QrResult
}
