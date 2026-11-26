package com.axzy.taqueria

import java.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException

/** Set de iconos como lo entrega la Central (`GET /icon-set`) y como la cocina se lo pasa a las meseras. */
@Serializable
data class IconSetDownload(
    val id: String,
    val name: String = "",
    val version: Int,
    /** PNG en base64. */
    val logo: String? = null,
    val appIcon: String? = null,
    val icons: List<IconDownload> = emptyList(),
)

@Serializable
data class IconDownload(val key: String, val group: String = "", val label: String = "", val png: String)

/** Lo que hay guardado en el teléfono. */
@Serializable
data class IconSetMeta(val id: String, val name: String, val version: Int, val keys: Set<String>, val hasLogo: Boolean, val hasAppIcon: Boolean)

/**
 * Iconos del giro guardados en el teléfono (`files/iconos/actual/<llave>.png`). La cocina los baja de la
 * Central cuando cambia la versión del set; las meseras los reciben de la cocina. Sin set (o sin internet
 * la primera vez) la app usa los iconos de taquería que trae el APK.
 *
 * Se reemplaza completo y de forma atómica: se escribe en una carpeta nueva y luego se cambia de nombre,
 * así un corte a la mitad nunca deja un set revuelto.
 */
class IconStore(private val dir: File) {
    private val tag = "IconStore"
    private val mutex = Mutex()
    private val current = File(dir, "actual")
    private val _meta = MutableStateFlow(readMeta())
    /** Set guardado (null = ninguno). Cambia cuando llega una versión nueva: la UI vuelve a dibujar. */
    val meta = _meta.asStateFlow()

    /** PNG de la llave en el set guardado, o null si el set no la trae. */
    fun file(key: String): File? = _meta.value?.takeIf { key in it.keys }?.let { File(current, "$key.png") }

    /**
     * Icono por nombre de producto sin llave explícita: "Dragón" → `rollo_dragon`, "Pepperoni" →
     * `pizza_pepperoni`. Se compara lo que va después del prefijo; gana el primero en orden.
     */
    fun byName(slug: String): File? =
        _meta.value?.keys?.sorted()?.firstOrNull { it.substringAfter('_') == slug && !it.startsWith("ing_") }?.let(::file)

    /** El "genérico" del set (rollo_generico, pizza_generica…), para productos sin ilustración. */
    fun generic(): File? = _meta.value?.keys?.firstOrNull { it.endsWith("_generico") || it.endsWith("_generica") }?.let(::file)

    fun logoFile(): File? = File(current, LOGO).takeIf { _meta.value?.hasLogo == true }
    fun appIconFile(): File? = File(current, APP_ICON).takeIf { _meta.value?.hasAppIcon == true }

    /** ¿Ya está guardada esa versión del set? */
    fun has(ref: IconSetRef?): Boolean = ref != null && _meta.value?.let { it.id == ref.id && it.version == ref.version } == true

    /** Guarda el set completo; reemplaza el anterior. */
    suspend fun install(set: IconSetDownload) = mutex.withLock {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val tmp = File(dir, "nuevo-${System.nanoTime()}").apply { mkdirs() }
            val keys = mutableSetOf<String>()
            for (icon in set.icons) {
                if (!KEY.matches(icon.key)) continue
                File(tmp, "${icon.key}.png").writeBytes(Base64.getMimeDecoder().decode(icon.png))
                keys += icon.key
            }
            set.logo?.let { File(tmp, LOGO).writeBytes(Base64.getMimeDecoder().decode(it)) }
            set.appIcon?.let { File(tmp, APP_ICON).writeBytes(Base64.getMimeDecoder().decode(it)) }
            // El JSON completo: la cocina se lo pasa tal cual a las meseras.
            File(tmp, SET_JSON).writeText(AppJson.encodeToString(set))
            val meta = IconSetMeta(set.id, set.name, set.version, keys, set.logo != null, set.appIcon != null)
            File(tmp, META).writeText(AppJson.encodeToString(meta))
            val old = File(dir, "viejo-${System.nanoTime()}")
            if (current.exists() && !current.renameTo(old)) throw IOException("No se pudo apartar el set anterior")
            if (!tmp.renameTo(current)) throw IOException("No se pudo guardar el set nuevo")
            old.deleteRecursively()
            _meta.value = meta
        }
    }

    /** Quita el set guardado: el giro ya no tiene iconos propios. */
    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { current.deleteRecursively() }
        _meta.value = null
    }

    /** El set tal como se guardó, para pasárselo a una mesera. */
    fun download(): IconSetDownload? =
        runCatching { AppJson.decodeFromString<IconSetDownload>(File(current, SET_JSON).readText()) }.getOrNull()

    /**
     * Teléfono de cocina: deja el set al día con lo que dice la licencia. Sin set en el giro lo quita;
     * con la misma versión no hace nada. Devuelve el error para el log, o null.
     */
    suspend fun syncFromCentral(creds: CentralCredentials?, ref: IconSetRef?): String? {
        if (ref == null) {
            if (_meta.value != null) clear()
            return null
        }
        if (has(ref) || creds == null) return null
        return try {
            val r = withContext(Dispatchers.IO) { centralCall(creds, "GET", "icon-set", timeoutMs = 30_000) }
            when (r.code) {
                200 -> { install(AppJson.decodeFromString<IconSetDownload>(r.body)); null }
                404 -> { clear(); null }
                else -> "La Central respondió ${r.code} al bajar los iconos"
            }
        } catch (e: IOException) {
            "Sin conexión para bajar los iconos: ${e.message}"
        } catch (e: Exception) {
            Log.w(tag, "Set de iconos ilegible", e)
            "El set de iconos no se pudo leer"
        }
    }

    private fun readMeta(): IconSetMeta? =
        runCatching { AppJson.decodeFromString<IconSetMeta>(File(current, META).readText()) }.getOrNull()

    private companion object {
        val KEY = Regex("^[a-z0-9_]{1,60}$")
        const val META = "meta.json"
        const val SET_JSON = "set.json"
        const val LOGO = "_logo.png"
        const val APP_ICON = "_app.png"
    }
}
