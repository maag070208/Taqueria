package com.axzy.taqueria.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.axzy.taqueria.App
import com.axzy.taqueria.ConnectionQr
import com.axzy.taqueria.DEFAULT_PORT
import com.axzy.taqueria.Link
import com.axzy.taqueria.R
import com.axzy.taqueria.Staff
import com.axzy.taqueria.WaiterWifi

/**
 * Pantalla de cocina extra: se conecta a la cocina principal (con el mismo QR
 * que las meseras) y muestra sus órdenes, todas o solo las de su estación.
 * No guarda datos ni cobra: todo vive en la cocina principal.
 */
@Composable
fun ScreenScreen(app: App, onExit: () -> Unit) {
    val context = LocalContext.current
    val state by app.client.state.collectAsState()
    val link by app.client.link.collectAsState()
    val zone by app.waiterWifi.state.collectAsState()
    val update by app.client.latestApp.collectAsState()
    var scanning by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var pickStation by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    // Como la cocina principal, la pantalla nunca se apaga.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    if (scanning) {
        QrScannerScreen(
            onDetected = { raw ->
                scanning = false
                when (val r = ConnectionQr.parse(raw)) {
                    is ConnectionQr.Result.Valid -> app.connectToKitchen(r.host, r.port, r.wifi)
                    is ConnectionQr.Result.Invalid -> Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                }
            },
            onCancel = { scanning = false },
            title = "Escanear QR de la cocina principal",
            hint = "En la cocina principal: menú → Conectar meseras y pantallas.",
            fallbackLabel = "Cancelar",
        )
        return
    }

    val drawer = rememberDrawerControl()
    val groups = listOf(
        listOf(
            stationViewItem(state, app.stationView) { pickStation = true },
            DrawerItem(R.drawable.ic_qr, "Conectar con la cocina (QR)") { scanning = true },
            DrawerItem(R.drawable.ic_wifi, "Conexión con la cocina") { settings = true },
        ),
        listOf(DrawerItem(R.drawable.ic_swap, "Cambiar rol del teléfono") { confirmExit = true }),
    )
    val giro = currentGiro()
    val kitchen = state.kitchenName.ifBlank { "la ${giro.terms.kitchen.lowercase()}" }
    val (color, text) = when {
        link is Link.Connected -> Palette.ok to "● Pantalla conectada a $kitchen"
        link is Link.Rejected -> Palette.late to "● ${(link as Link.Rejected).message}"
        zone is WaiterWifi.State.Joining -> Palette.warn to "● Entrando a la red de cocina…"
        zone is WaiterWifi.State.Failed -> Palette.late to "● ${(zone as WaiterWifi.State.Failed).message}"
        link is Link.Connecting -> Palette.warn to "● Conectando…"
        else -> Palette.late to "● Buscando la cocina principal… (escanea su QR)"
    }
    AppDrawer(drawer, Staff.TAQUERO, "Pantalla de ${giro.terms.kitchen.lowercase()}", groups) {
        OrdersView(
            state = state,
            stationId = app.stationView,
            subtitle = text,
            subtitleColor = color,
            notice = null,
            update = update,
            onMenu = drawer::open,
            offset = app.client.clockOffset,
        )
    }

    if (pickStation) {
        StationPickerDialog(state, app.stationView, onPick = { app.updateStationView(it); pickStation = false }, onDismiss = { pickStation = false })
    }
    if (settings) {
        SettingsDialog(
            current = app.manualHost,
            onDismiss = { settings = false },
            onSave = { t ->
                settings = false
                val host = t.substringBeforeLast(":", t).trim()
                if (host.isEmpty()) app.updateManualHost("") else app.connectToKitchen(host, t.substringAfterLast(":", "").toIntOrNull() ?: DEFAULT_PORT, null)
            },
            onScanQr = { settings = false; scanning = true },
        )
    }
    if (confirmExit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { androidx.compose.material3.Text("¿Cambiar el rol de este teléfono?") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmExit = false; onExit() }) { androidx.compose.material3.Text("Cambiar") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmExit = false }) { androidx.compose.material3.Text("Cancelar") } },
        )
    }
}
