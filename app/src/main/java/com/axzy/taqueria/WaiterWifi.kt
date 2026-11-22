package com.axzy.taqueria

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La mesera se une a la zona de la cocina con el nombre y la contraseña del QR.
 *
 * Android 10+ muestra una vez "¿Conectar a …?" y, al aceptar, la app usa esa
 * red para hablar con la cocina (el resto del teléfono sigue con sus datos).
 * La red se recuerda: al abrir la app otra vez se vuelve a pedir y Android ya
 * no pregunta. Si la red desaparece (la cocina apagó la zona) se suelta sola.
 */
class WaiterWifi(
    context: Context,
    private val onNetworkChanged: () -> Unit,
    /** No se pudo entrar: se olvida la red (al reactivar la zona cambia la contraseña de todos modos). */
    private val onGaveUp: () -> Unit = {},
) {
    sealed interface State {
        data object Idle : State
        data class Joining(val ssid: String) : State
        data class Joined(val ssid: String) : State
        data class Failed(val ssid: String, val message: String) : State
    }

    private val tag = "WaiterWifi"
    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state = _state.asStateFlow()

    fun join(wifi: KitchenWifi) {
        leave()
        _state.value = State.Joining(wifi.ssid)
        if (Build.VERSION.SDK_INT < 29) return joinLegacy(wifi)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // La zona no tiene internet; sin esto Android no la ofrecería.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(WifiNetworkSpecifier.Builder().setSsid(wifi.ssid).setWpa2Passphrase(wifi.pass).build())
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Todo lo que abra la app (el socket con cocina) sale por la zona.
                cm.bindProcessToNetwork(network)
                _state.value = State.Joined(wifi.ssid)
                onNetworkChanged()
            }

            override fun onLost(network: Network) {
                cm.bindProcessToNetwork(null)
                _state.value = State.Joining(wifi.ssid)
                onNetworkChanged()
            }

            override fun onUnavailable() {
                callback = null
                _state.value = State.Failed(wifi.ssid, "No se encontró la zona de cocina · escanea su QR otra vez")
                onGaveUp()
            }
        }
        try {
            cm.requestNetwork(request, cb)
            callback = cb
        } catch (e: Exception) {
            Log.w(tag, "No se pudo pedir la red", e)
            _state.value = State.Failed(wifi.ssid, "Android no dejó conectar a la red de cocina.")
        }
    }

    /** Suelta la red de la zona y vuelve a la red normal del teléfono. */
    fun leave() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        cm.bindProcessToNetwork(null)
        _state.value = State.Idle
    }

    /** Android 8 y 9: se agrega la red al teléfono y se conecta. */
    @Suppress("DEPRECATION")
    private fun joinLegacy(wifi: KitchenWifi) {
        val wm = appContext.getSystemService(WifiManager::class.java)
        val config = WifiConfiguration().apply {
            SSID = "\"${wifi.ssid}\""
            preSharedKey = "\"${wifi.pass}\""
        }
        val id = wm?.addNetwork(config) ?: -1
        if (id == -1 || !wm!!.enableNetwork(id, true)) {
            _state.value = State.Failed(wifi.ssid, "No se pudo agregar la red de cocina. Conéctate a «${wifi.ssid}» en Ajustes de WiFi.")
            return
        }
        _state.value = State.Joined(wifi.ssid)
        onNetworkChanged()
    }
}
