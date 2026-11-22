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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * QR que muestra el teléfono de cocina para que la mesera se conecte sin
 * escribir la IP ni depender de mDNS. El QR lleva host y puerto.
 */
@Composable
fun ConnectionQrDialog(ips: List<String>, port: Int, onDismiss: () -> Unit) {
    val options = remember(ips) { ips.filter { it.isNotBlank() }.distinct() }
    var selected by remember { mutableStateOf(options.firstOrNull().orEmpty()) }
    val payload = remember(selected, port) { if (selected.isBlank()) "" else ConnectionQr.encode(selected, port) }
    val bitmap = remember(payload) { if (payload.isBlank()) null else qrBitmap(payload, 640) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conectar la mesera") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR de conexión de la cocina",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .background(androidx.compose.ui.graphics.Color.White).padding(8.dp),
                    )
                } else {
                    Text("Conecta esta cocina al WiFi o activa el hotspot para mostrar el QR.", textAlign = TextAlign.Center)
                }
                Text(
                    "En la mesera: ⚙ → Conexión → «Escanear QR de cocina».",
                    color = Palette.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                if (selected.isNotBlank()) {
                    Text("Cocina: $selected:$port", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                if (options.size > 1) {
                    Text("Redes de este teléfono", color = Palette.muted, fontSize = 13.sp)
                    Grid(options, 2) { ip -> OptionChip(ip, ip == selected) { selected = ip } }
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
