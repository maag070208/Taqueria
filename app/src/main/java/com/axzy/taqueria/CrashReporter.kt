package com.axzy.taqueria

import android.os.Build
import android.util.Log
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Una falla de la app, como la recibe la Central (`POST /app-errors`). */
@Serializable
data class AppErrorReport(
    val id: String,
    /** "cocina" o "mesero". */
    val source: String,
    val appVersion: String,
    val model: String,
    val occurredAt: String,
    val message: String,
    val stack: String,
)

@Serializable
private data class AppErrorUpload(val errors: List<AppErrorReport>)

/**
 * Guarda en el teléfono cada cierre inesperado de la app para avisarle al
 * proveedor. La cocina las sube a la Central; la mesera se las pasa a la cocina.
 * Un archivo por falla en [dir]; se borra cuando ya llegó a su destino.
 */
class CrashReporter(
    private val dir: File,
    private val appVersion: String,
    private val source: () -> String,
    private val model: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
) {
    private val tag = "CrashReporter"

    /** Atrapa los cierres inesperados; después deja que Android cierre la app como siempre. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(error: Throwable, now: Long = System.currentTimeMillis()) {
        add(listOf(report(error, now)))
    }

    fun report(error: Throwable, now: Long = System.currentTimeMillis()) = AppErrorReport(
        id = UUID.randomUUID().toString(),
        source = source(),
        appVersion = appVersion,
        model = model.take(80),
        occurredAt = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString(),
        message = "${error::class.java.simpleName}: ${error.message.orEmpty()}".take(500),
        stack = error.stackTraceToString().take(16_000),
    )

    /** Guarda fallas (propias o las que manda una mesera). Se conservan las 50 más recientes. */
    @Synchronized
    fun add(reports: List<AppErrorReport>) {
        dir.mkdirs()
        reports.forEach { fileOf(it.id).writeText(AppJson.encodeToString(it)) }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_KEPT)?.forEach { it.delete() }
    }

    @Synchronized
    fun pending(): List<AppErrorReport> = dir.listFiles()?.sortedBy { it.lastModified() }?.mapNotNull { f ->
        runCatching { AppJson.decodeFromString<AppErrorReport>(f.readText()) }.getOrElse { f.delete(); null }
    }.orEmpty()

    @Synchronized
    fun remove(ids: Collection<String>) {
        ids.forEach { fileOf(it).delete() }
    }

    /** El id lo puede mandar una mesera: solo letras, números y guiones en el nombre del archivo. */
    private fun fileOf(id: String) = File(dir, "${id.filter { it.isLetterOrDigit() || it == '-' }.take(64).ifEmpty { "sin-id" }}.json")

    /** Cocina: sube lo pendiente a la Central. Devuelve el error o null. */
    fun upload(creds: CentralCredentials): String? {
        val batch = pending().take(20)
        if (batch.isEmpty()) return null
        return try {
            val r = centralCall(creds, "POST", "app-errors", AppJson.encodeToString(AppErrorUpload(batch)))
            if (r.code in 200..299) { remove(batch.map { it.id }); null } else "La Central respondió ${r.code}"
        } catch (e: IOException) {
            Log.i(tag, "Sin conexión para reportar fallas: ${e.message}")
            "Sin conexión"
        }
    }

    private companion object {
        const val MAX_KEPT = 50
    }
}
