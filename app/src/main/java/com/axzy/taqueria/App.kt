package com.axzy.taqueria

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.UUID

enum class Role { KITCHEN, WAITER }

class App : Application() {
    private val prefs by lazy { getSharedPreferences("taqueria", MODE_PRIVATE) }

    val store by lazy { OrderStore(File(filesDir, "taqueria.json")) }
    val license by lazy { LicenseManager(this, BuildConfig.LICENSE_API_URL) }
    val server by lazy { KitchenServer(this, store, isLocked = { license.locked }) }
    val client by lazy { WaiterClient(this) }
    val draft = WaiterDraft()

    var role by mutableStateOf<Role?>(null)
        private set
    var manualHost by mutableStateOf("")
        private set

    override fun onCreate() {
        super.onCreate()
        manualHost = prefs.getString("manualHost", "").orEmpty()
        applyRole(prefs.getString("role", null)?.let { runCatching { Role.valueOf(it) }.getOrNull() })
    }

    fun applyRole(next: Role?) {
        prefs.edit().putString("role", next?.name).apply()
        role = next
        if (next == Role.KITCHEN) { license.start(); server.start() } else { server.stop(); license.stop() }
        if (next == Role.WAITER) client.start(manualHost) else client.stop()
    }

    /** Reinicia servidor/cliente, p. ej. después de conceder el permiso de red local. */
    fun restartNetwork() {
        server.stop()
        applyRole(role)
    }

    fun updateManualHost(host: String) {
        manualHost = host.trim()
        prefs.edit().putString("manualHost", manualHost).apply()
        if (role == Role.WAITER) client.start(manualHost)
    }
}

val Context.app get() = applicationContext as App

/** Grupo de preparación en captura: N piezas de un producto con sus modificaciones. */
class DraftGroup(val productId: String, qty: Int, mods: Set<String> = emptySet(), note: String = "") {
    var qty by mutableIntStateOf(qty)
        internal set
    var mods by mutableStateOf(mods)
        internal set
    var note by mutableStateOf(note)
        internal set
    val isPlain get() = mods.isEmpty() && note.isBlank()
}

/**
 * Orden en captura de la mesera. Sobrevive a rotaciones porque vive en [App].
 * La cantidad de un producto es siempre la suma de sus grupos: no hay un total
 * aparte que pueda descuadrarse.
 */
class WaiterDraft {
    var table by mutableStateOf("")
        private set
    /** Cuenta a la que se agrega; nulo = cuenta nueva. */
    var accountId by mutableStateOf<String?>(null)
        private set
    val groups = mutableStateListOf<DraftGroup>()
    /** Orden ya enviada que se está editando; nulo = orden nueva. */
    var editingOrderId by mutableStateOf<String?>(null)
        private set

    /** Cambia con cada edición: un reintento idéntico no duplica, una orden distinta sí se crea. */
    var clientKey = newKey()
        private set

    val isEmpty get() = groups.isEmpty()

    fun start(table: String, accountId: String?) {
        groups.clear()
        this.table = table
        this.accountId = accountId
        editingOrderId = null
        touch()
    }

    /** Carga una orden enviada para modificarla (cantidades, grupos o productos nuevos). */
    fun startEdit(order: Order) {
        start(order.table, order.accountId)
        editingOrderId = order.id
        order.lines.forEach { groups.add(DraftGroup(it.productId, it.qty, it.mods.toSet(), it.note)) }
    }

    /** ¿El borrador difiere de lo que tiene la orden? (sin importar el orden de los grupos). */
    fun differsFrom(order: Order): Boolean {
        fun norm(l: List<LineRequest>) = l.map { Triple(it.productId, it.mods.sorted(), it.note.trim()) to it.qty }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        return norm(lines()) != norm(order.lines.map { LineRequest(it.productId, it.qty, it.mods, it.note) })
    }

    fun qtyOf(productId: String) = groups.filter { it.productId == productId }.sumOf { it.qty }
    fun groupsOf(productId: String) = groups.filter { it.productId == productId }
    fun productIds() = groups.map { it.productId }.distinct()

    /** Un toque = +1 al grupo sin modificaciones del producto (el caso más común). */
    fun add(productId: String) {
        val plain = groups.lastOrNull { it.productId == productId && it.isPlain }
        if (plain != null) plain.qty = (plain.qty + 1).coerceAtMost(99) else insert(DraftGroup(productId, 1))
        touch()
    }

    fun removeOne(productId: String) {
        val g = groups.lastOrNull { it.productId == productId } ?: return
        setQty(g, g.qty - 1)
    }

    /** Sin grupos en cero: bajar de 1 quita el grupo. */
    fun setQty(g: DraftGroup, qty: Int) {
        if (qty <= 0) groups.remove(g) else g.qty = qty.coerceAtMost(99)
        touch()
    }

    /** Parte a la mitad el grupo más grande (4 → 2 + 2, 5 → 3 + 2); el total no cambia. */
    fun split(productId: String): Boolean {
        val donor = groupsOf(productId).maxByOrNull { it.qty }?.takeIf { it.qty > 1 } ?: return false
        val moved = donor.qty / 2
        donor.qty -= moved
        insert(DraftGroup(productId, moved, donor.mods, donor.note))
        touch()
        return true
    }

    /** En un tipo exclusivo (Salsa: con/sin/aparte) elegir una opción quita las otras. */
    fun toggleMod(g: DraftGroup, option: String, group: ModifierGroup) {
        g.mods = when {
            option in g.mods -> g.mods - option
            group.exclusive -> g.mods - group.options.toSet() + option
            else -> g.mods + option
        }
        touch()
    }

    fun setNote(g: DraftGroup, note: String) {
        g.note = note.take(100)
        touch()
    }

    fun lines() = groups.map { LineRequest(it.productId, it.qty, it.mods.toList(), it.note.trim()) }

    /** Quita del pedido lo que cocina desactivó o borró. */
    fun retainAvailable(activeIds: Set<String>) {
        // Al editar se conserva lo ya pedido aunque se haya agotado después.
        if (editingOrderId != null) return
        groups.removeAll { it.productId !in activeIds }
    }

    fun clear() {
        table = ""
        accountId = null
        editingOrderId = null
        groups.clear()
        touch()
    }

    /** Nuevo grupo junto a los del mismo producto, para que el resumen quede ordenado. */
    private fun insert(g: DraftGroup) {
        val last = groups.indexOfLast { it.productId == g.productId }
        if (last < 0) groups.add(g) else groups.add(last + 1, g)
    }

    private fun touch() { clientKey = newKey() }
    private fun newKey() = UUID.randomUUID().toString()
}

object Alerts {
    private val main = Handler(Looper.getMainLooper())

    fun beep(times: Int = 2) {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }.getOrNull() ?: return
        repeat(times) { i -> main.postDelayed({ tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 200) }, i * 350L) }
        main.postDelayed({ tone.release() }, times * 350L + 300)
    }

    fun vibrate(context: Context, ms: Long = 400) {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
