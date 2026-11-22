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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.UUID

enum class Role { KITCHEN, WAITER }

class App : Application() {
    private val prefs by lazy { getSharedPreferences("taqueria", MODE_PRIVATE) }

    val store by lazy { OrderStore(File(filesDir, "taqueria.json")) }
    /** "1.1.0 (4)": nombre y número de la versión instalada. */
    val versionLabel by lazy {
        val info = packageManager.getPackageInfo(packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    }
    val license by lazy { LicenseManager(this, getString(R.string.license_api_url), versionLabel) }
    private val salesPrefs by lazy { getSharedPreferences("sales_sync", MODE_PRIVATE) }
    val salesSync by lazy {
        SalesSync(
            store, license::credentials, versionLabel,
            getSent = { salesPrefs.getInt("sent", 0) },
            putSent = { salesPrefs.edit().putInt("sent", it).apply() },
        )
    }
    val server by lazy { KitchenServer(this, store, isLocked = { license.locked }) }
    val client by lazy { WaiterClient(this) }
    val draft = WaiterDraft()
    val adminPin by lazy {
        AdminPin(
            get = { prefs.getString(it, null) },
            put = { values -> prefs.edit().apply { values.forEach { (k, v) -> putString(k, v) } }.apply() },
        )
    }

    var role by mutableStateOf<Role?>(null)
        private set
    /** Sesión de admin en cocina. No se guarda: al reiniciar o salir de la app vuelve a taquero. */
    var admin by mutableStateOf(false)
        private set
    val staff get() = when (role) {
        Role.KITCHEN -> if (admin) Staff.ADMIN else Staff.TAQUERO
        Role.WAITER -> Staff.MESERO
        null -> null
    }
    var manualHost by mutableStateOf("")
        private set
    /** Evita tocar `server`/`store` en el teléfono de mesera: solo la cocina guarda datos. */
    private var serverStarted = false

    override fun onCreate() {
        super.onCreate()
        manualHost = prefs.getString("manualHost", "").orEmpty()
        applyRole(prefs.getString("role", null)?.let { runCatching { Role.valueOf(it) }.getOrNull() })
    }

    fun applyRole(next: Role?) {
        prefs.edit().putString("role", next?.name).apply()
        role = next
        admin = false
        if (next == Role.KITCHEN) {
            license.start()
            server.start()
            serverStarted = true
            salesSync.start()
        } else {
            // En mesera no se instancia el servidor (ni su OrderStore): no se guardan datos de negocio.
            if (serverStarted) { server.stop(); serverStarted = false; salesSync.stop() }
            license.stop()
        }
        if (next == Role.WAITER) client.start(manualHost) else client.stop()
    }

    /** Reinicia servidor/cliente, p. ej. después de conceder el permiso de red local. */
    fun restartNetwork() {
        if (serverStarted) { server.stop(); serverStarted = false }
        applyRole(role)
    }

    /** Entra como admin si el PIN es correcto. */
    fun enterAdmin(pin: String): AdminPin.Result {
        if (role != Role.KITCHEN) return AdminPin.Result.Wrong(0)
        return adminPin.check(pin).also { if (it == AdminPin.Result.Ok) admin = true }
    }

    /** Crea el PIN la primera vez (y entra como admin) o lo cambia estando como admin. */
    fun setAdminPin(pin: String) {
        check(role == Role.KITCHEN && (admin || !adminPin.isSet)) { "Solo el admin cambia el PIN" }
        adminPin.set(pin)
        admin = true
    }

    fun lockAdmin() { admin = false }

    fun updateManualHost(host: String) {
        manualHost = host.trim()
        prefs.edit().putString("manualHost", manualHost).apply()
        if (role == Role.WAITER) client.start(manualHost)
    }
}

val Context.app get() = applicationContext as App

/**
 * Renglón en captura: N piezas de un producto para una persona, con su presentación,
 * carnes del mixto y preparación. Lo que cambie en él se ve al instante en pantalla.
 */
class DraftLine(
    val productId: String,
    val person: Int,
    variant: String,
    mix: List<String>,
    mods: Set<String>,
    qty: Int,
    /** Solo en categorías por monto. */
    val amountCents: Long? = null,
    note: String = "",
) {
    val id = UUID.randomUUID().toString()
    var qty by mutableIntStateOf(qty)
        internal set
    var variant by mutableStateOf(variant)
        internal set
    var mix by mutableStateOf(mix)
        internal set
    var mods by mutableStateOf(mods)
        internal set
    var note by mutableStateOf(note)
        internal set

    fun request() = LineRequest(productId, qty, mods.toList(), note.trim(), variant, mix, person, amountCents)
}

/**
 * Orden en captura de la mesera. Sobrevive a rotaciones porque vive en [App].
 * Flujo: tocar la carne suma +1 al renglón abierto de esa carne (o lo crea con la
 * preparación predeterminada); "otra de…" abre un renglón nuevo para otra preparación.
 */
class WaiterDraft {
    var table by mutableStateOf("")
        private set
    /** Cuenta a la que se agrega; nulo = cuenta nueva. */
    var accountId by mutableStateOf<String?>(null)
        private set
    val lines = mutableStateListOf<DraftLine>()
    /** Personas en la mesa (tarjetas en captura). */
    var persons by mutableIntStateOf(1)
        private set
    /** Persona que se está capturando (la del stepper); se conserva al cambiar de categoría. */
    var person by mutableIntStateOf(1)
    /** Extras sin costo por persona. */
    val extras = mutableStateMapOf<Int, Set<String>>()
    /** Renglón desplegado para configurarlo; nulo = todos plegados. */
    var openLineId by mutableStateOf<String?>(null)
    /** Orden ya enviada que se está editando; nulo = orden nueva. */
    var editingOrderId by mutableStateOf<String?>(null)
        private set
    /** A qué renglón suman los toques de cada producto y persona. */
    private val tapTarget = HashMap<Pair<String, Int>, String>()

    /** Cambia con cada edición: un reintento idéntico no duplica, una orden distinta sí se crea. */
    var clientKey = newKey()
        private set

    val isEmpty get() = lines.isEmpty() && extras.values.all { it.isEmpty() }
    val count get() = lines.sumOf { it.qty }

    fun start(table: String, accountId: String?) {
        lines.clear()
        extras.clear()
        tapTarget.clear()
        persons = 1
        person = 1
        openLineId = null
        this.table = table
        this.accountId = accountId
        editingOrderId = null
        touch()
    }

    /** Carga una orden enviada para modificarla. */
    fun startEdit(order: Order) {
        start(order.table, order.accountId)
        editingOrderId = order.id
        order.lines.forEach { lines.add(DraftLine(it.productId, it.person, it.variant, it.mix, it.mods.toSet(), it.qty, it.priceCents, it.note)) }
        order.extras.forEach { extras[it.person] = it.items.toSet() }
        persons = order.persons.maxOrNull() ?: 1
    }

    /** ¿El borrador difiere de lo que tiene la orden? (sin importar el orden de los renglones). */
    fun differsFrom(order: Order): Boolean {
        fun norm(l: List<LineRequest>) = l.map { it.copy(qty = 0, mods = it.mods.sorted(), note = it.note.trim(), amountCents = null) to it.qty }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val sent = order.lines.map { LineRequest(it.productId, it.qty, it.mods, it.note, it.variant, it.mix, it.person) }
        val extrasNow = extrasRequest().associate { it.person to it.items.toSet() }
        return norm(lines()) != norm(sent) || extrasNow != order.extras.associate { it.person to it.items.toSet() }
    }

    fun linesOf(person: Int, productIds: Set<String>) = lines.filter { it.person == person && it.productId in productIds }
    fun qtyOf(person: Int, productId: String) = lines.filter { it.person == person && it.productId == productId }.sumOf { it.qty }

    /** Tocar la carne: +1 al renglón abierto (o al último que se usó) de esa carne; si no hay, uno nuevo. */
    fun tap(product: Product, person: Int, defaults: Set<String>, defaultMix: List<String>) {
        val open = lines.firstOrNull { it.id == openLineId && it.productId == product.id && it.person == person }
        val target = open ?: tapTarget[product.id to person]?.let { id -> lines.firstOrNull { it.id == id } }
        if (target != null) {
            target.qty = (target.qty + 1).coerceAtMost(99)
            openLineId = target.id
            touch()
        } else {
            newLine(product, person, defaults, defaultMix)
        }
    }

    /** Renglón nuevo de la misma carne (otra preparación), abierto para configurarlo. */
    fun newLine(product: Product, person: Int, defaults: Set<String>, defaultMix: List<String>, after: DraftLine? = null) {
        val line = DraftLine(product.id, person, product.defaultVariant, if (product.mix) defaultMix else emptyList(), defaults, 1)
        val at = after?.let { lines.indexOf(it) } ?: lines.indexOfLast { it.person == person && it.productId == product.id }
        if (at < 0) lines.add(line) else lines.add(at + 1, line)
        openLineId = line.id
        tapTarget[product.id to person] = line.id
        touch()
    }

    /** Orden por monto (orden de $100 de asada). */
    fun addAmount(product: Product, person: Int, amountCents: Long) {
        lines.add(DraftLine(product.id, person, "", emptyList(), emptySet(), 1, amountCents))
        touch()
    }

    fun open(line: DraftLine?) {
        openLineId = line?.id
        if (line != null) tapTarget[line.productId to line.person] = line.id
    }

    /** Bajar de 1 quita el renglón. */
    fun setQty(line: DraftLine, qty: Int) {
        if (qty <= 0) {
            lines.remove(line)
            if (openLineId == line.id) openLineId = null
        } else {
            line.qty = qty.coerceAtMost(99)
        }
        touch()
    }

    /** Quita el renglón (deslizar); devuelve dónde estaba para poder deshacer. */
    fun remove(line: DraftLine): Int {
        val at = lines.indexOf(line)
        setQty(line, 0)
        return at
    }

    /** Deshacer: regresa el renglón quitado a su lugar. */
    fun restore(line: DraftLine, at: Int) {
        if (line in lines) return
        lines.add(at.coerceIn(0, lines.size), line)
        touch()
    }

    fun setMods(line: DraftLine, mods: Set<String>) { line.mods = mods; touch() }
    fun setVariant(line: DraftLine, variant: String) { line.variant = variant; touch() }
    fun setMix(line: DraftLine, mix: List<String>) { line.mix = mix; touch() }
    fun setNote(line: DraftLine, note: String) { line.note = note.take(100); touch() }

    fun toggleExtra(person: Int, item: String) {
        val now = extras[person].orEmpty()
        extras[person] = if (item in now) now - item else now + item
        touch()
    }

    /** Agrega una persona y la deja seleccionada. */
    fun addPerson() {
        if (persons < MAX_PERSONS) persons++
        person = persons
    }

    fun lines() = lines.map { it.request() }
    fun extrasRequest() = extras.filterValues { it.isNotEmpty() }.map { (p, items) -> PersonExtras(p, items.toList()) }.sortedBy { it.person }

    /** Quita del pedido lo que cocina desactivó o borró. */
    fun retainAvailable(activeIds: Set<String>) {
        // Al editar se conserva lo ya pedido aunque se haya agotado después.
        if (editingOrderId != null) return
        lines.removeAll { it.productId !in activeIds }
    }

    fun clear() {
        start("", null)
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
