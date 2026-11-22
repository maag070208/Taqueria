package com.axzy.taqueria.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.ConnectionQr
import com.axzy.taqueria.KitchenZone
import com.axzy.taqueria.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * QR que muestra el teléfono de cocina para que la mesera se conecte sin
 * escribir la IP ni depender de mDNS.
 *
 * Con la zona activa el QR lleva también la red (nombre y contraseña) y la
 * mesera se une sola. Sin zona, la cocina y las meseras deben estar en el
 * mismo WiFi y el QR solo lleva la dirección.
 */
@Composable
fun ConnectionQrDialog(
    ips: List<String>,
    port: Int,
    zone: KitchenZone.State,
    onStartZone: () -> Unit,
    onStopZone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember(ips) { ips.filter { it.isNotBlank() }.distinct() }
    var selected by remember(options) { mutableStateOf(options.firstOrNull().orEmpty()) }
    val on = zone as? KitchenZone.State.On
    val host = on?.host ?: selected
    val payload = remember(host, port, on) {
        when {
            on != null && on.host == null -> ""
            host.isBlank() -> ""
            else -> ConnectionQr.encode(host, port, on?.wifi)
        }
    }
    val bitmap = remember(payload) { if (payload.isBlank()) null else qrBitmap(payload, 640) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (on != null) "Zona de cocina activa" else "Conectar meseras") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    bitmap != null -> Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR de conexión de la cocina",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .background(androidx.compose.ui.graphics.Color.White).padding(8.dp),
                    )
                    zone == KitchenZone.State.Starting || (on != null && on.host == null) ->
                        Text("Abriendo la zona…", color = Palette.muted, textAlign = TextAlign.Center)
                    else -> Text("Este teléfono no está en ningún WiFi. Activa la zona para que las meseras se conecten.", textAlign = TextAlign.Center)
                }
                Text(
                    if (on != null) "La mesera escanea este QR (menú → Conexión con cocina → Escanear) y entra sola a la red de cocina."
                    else "En la mesera: menú → Conexión con cocina → Escanear QR.",
                    color = Palette.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                if (on != null) {
                    Text("Red «${on.wifi.ssid}»", fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Text("Contraseña: ${on.wifi.pass}", color = Palette.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Text(
                        "La zona no tiene internet y se apaga si cierras la app. Si la vuelves a activar, cambia la contraseña: las meseras escanean otra vez.",
                        color = Palette.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onStopZone) { Text("Apagar zona", color = Palette.late) }
                } else {
                    if (selected.isNotBlank()) Text("Cocina: $selected:$port", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (options.size > 1) {
                        Text("Redes de este teléfono", color = Palette.muted, fontSize = 13.sp)
                        Grid(options, 2) { ip -> OptionChip(ip, ip == selected) { selected = ip } }
                    }
                    if (zone is KitchenZone.State.Failed) {
                        Text(zone.message, color = Palette.late, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    ActionButton(
                        if (zone == KitchenZone.State.Starting) "Abriendo zona…" else "Activar zona",
                        Palette.primary, androidx.compose.ui.graphics.Color.White,
                        Modifier.fillMaxWidth(),
                        enabled = zone != KitchenZone.State.Starting,
                        icon = R.drawable.ic_wifi,
                        onClick = onStartZone,
                    )
                    Text(
                        "¿No hay WiFi en el local? La cocina abre su propia red y el QR lleva la contraseña.",
                        color = Palette.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

/** Dibuja el texto como QR (ZXing core ya viene incluido; sin Google Play Services). */
private fun qrBitmap(text: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"),
    )
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    bitmap
}.getOrNull()
