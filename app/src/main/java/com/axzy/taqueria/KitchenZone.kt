package com.axzy.taqueria

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * "Zona" de la cocina: el teléfono de cocina abre su propia red WiFi (hotspot
 * local de Android, sin internet) para que las meseras se conecten con el QR,
 * sin depender del WiFi del local.
 *
 * Android elige el nombre y la contraseña (cambian cada vez que se activa) y se
 * los da a la app; por eso van dentro del QR. Del hotspot normal del teléfono
 * Android no deja leer la contraseña, por eso no sirve para el QR.
 *
 * La zona dura mientras la app esté abierta; si se cierra la app, Android la apaga.
 */
class KitchenZone(context: Context) {
    sealed interface State {
        data object Off : State
        data object Starting : State
        /** [host] es la IP de la cocina dentro de la zona (null si Android aún no la asigna). */
        data class On(val wifi: KitchenWifi, val host: String?) : State
        data class Failed(val message: String) : State
    }

    private val tag = "KitchenZone"
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _state = MutableStateFlow<State>(State.Off)
    val state = _state.asStateFlow()

    /** Pide los permisos antes (ver [KitchenZone.permissions]); sin ellos falla con un mensaje claro. */
    fun start() {
        if (reservation != null || _state.value == State.Starting) return
        val manager = wifi ?: run { _state.value = State.Failed("Este teléfono no tiene WiFi."); return }
        _state.value = State.Starting
        val before = KitchenServer.localIps().toSet()
        try {
            manager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = r
                    val creds = credentialsOf(r)
                    if (creds == null) {
                        r.close()
                        reservation = null
                        _state.value = State.Failed("Android no dio los datos de la red. Intenta otra vez.")
                        return
                    }
                    _state.value = State.On(creds, null)
                    // La IP de la cocina en la zona aparece unos instantes después.
                    scope.launch {
                        repeat(20) {
                            val host = zoneHost(before, KitchenServer.localIps())
                            if (host != null) {
                                if (reservation === r) _state.value = State.On(creds, host)
                                return@launch
                            }
                            delay(250)
                        }
                    }
                }

                override fun onStopped() {
                    reservation = null
                    _state.value = State.Off
                }

                override fun onFailed(reason: Int) {
                    reservation = null
                    _state.value = State.Failed(failureMessage(reason))
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            Log.w(tag, "Sin permiso para la zona", e)
            _state.value = State.Failed(
                if (Build.VERSION.SDK_INT < 33) "Activa la ubicación del teléfono y da permiso a Komandera para abrir la zona."
                else "Da permiso a Komandera de dispositivos cercanos para abrir la zona.",
            )
        } catch (e: IllegalStateException) {
            // Android ya tiene una zona de esta app (p. ej. tras girar la pantalla).
            Log.w(tag, "Zona ya activa", e)
            if (reservation == null) _state.value = State.Failed("Android ya tiene una zona abierta. Apágala y vuelve a intentar.")
        }
    }

    fun stop() {
        reservation?.close()
        reservation = null
        _state.value = State.Off
    }

    @Suppress("DEPRECATION")
    private fun credentialsOf(r: WifiManager.LocalOnlyHotspotReservation): KitchenWifi? {
        val (ssid, pass) = if (Build.VERSION.SDK_INT >= 30) {
            val c = r.softApConfiguration
            val name = if (Build.VERSION.SDK_INT >= 33) c.wifiSsid?.toString() else c.ssid
            name to c.passphrase
        } else {
            r.wifiConfiguration?.let { it.SSID to it.preSharedKey } ?: (null to null)
        }
        val cleanSsid = ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() } ?: return null
        val cleanPass = pass?.removeSurrounding("\"")?.takeIf { it.length in 8..63 } ?: return null
        return KitchenWifi(cleanSsid, cleanPass)
    }

    companion object {
        /** Permisos que pide Android para abrir la zona. */
        val permissions: Array<String> =
            if (Build.VERSION.SDK_INT >= 33) arrayOf(android.Manifest.permission.NEARBY_WIFI_DEVICES)
            else arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)

        /**
         * IP de la cocina dentro de la zona: la dirección nueva que apareció al abrirla.
         * Si hubiera varias, se prefiere la que termina en .1 (la puerta de la red).
         */
        fun zoneHost(before: Set<String>, after: List<String>): String? {
            val fresh = after.filter { it !in before }
            return fresh.firstOrNull { it.endsWith(".1") } ?: fresh.firstOrNull()
        }

        fun failureMessage(reason: Int): String = when (reason) {
            WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE ->
                "Apaga el hotspot normal del teléfono (o la conexión compartida) y vuelve a intentar."
            WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED ->
                "Este teléfono no permite abrir una zona (lo bloquea el operador o el administrador)."
            WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL ->
                "No hay canal WiFi libre. Intenta de nuevo en un momento."
            else -> "No se pudo abrir la zona. Revisa que el WiFi esté encendido e intenta otra vez."
        }
    }
}
