package com.axzy.taqueria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.LicenseManager
import com.axzy.taqueria.LicenseState
import com.axzy.taqueria.LockReason
import com.axzy.taqueria.QrPayload
import com.axzy.taqueria.QrResult
import kotlinx.coroutines.launch

/**
 * Lo que ve el teléfono de cocina antes de operar: activar con el código que da
 * la plataforma, o el bloqueo si la cuenta está suspendida, revocada o lleva
 * demasiado tiempo sin confirmarse.
 */
@Composable
fun LicenseGate(license: LicenseManager, onExit: () -> Unit, content: @Composable (notice: String?) -> Unit) {
    when (val s = license.state) {
        LicenseState.NotActivated -> ActivationScreen(license, onExit)
        is LicenseState.Locked -> LockedScreen(license, s, onExit)
        is LicenseState.Active -> content(
            if (s.expiresSoon) "Conecta este teléfono a internet antes del ${dateText(s.validUntilMs)} para que la app siga funcionando." else null,
        )
    }
}

@Composable
private fun ActivationScreen(license: LicenseManager, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }

    if (scanning) {
        QrScannerScreen(
            onDetected = { raw ->
                scanning = false
                when (val result = QrPayload.parse(raw)) {
                    is QrResult.Valid -> {
                        code = result.payload.code
                        error = null
                        scope.launch { error = license.activate(result.payload.code, result.payload.apiUrl) }
                    }
                    is QrResult.Invalid -> error = result.message
                }
            },
            onCancel = { scanning = false },
        )
        return
    }

    Centered {
        Text("🔑", fontSize = 56.sp)
        Text("Activa esta cocina", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
        Text(
            "En Axzy Central abre el detalle de la taquería y toca «Generar QR de activación». Escanéalo con este teléfono: el QR ya trae el servidor. Solo esta vez necesitas internet; después la app funciona sin él.",
            color = Palette.muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
        )
        ActionButton(
            "Escanear QR de la Central", Palette.primary, Color.White, Modifier.fillMaxWidth(),
            enabled = !license.checking,
        ) { error = null; scanning = true }
        if (license.checking) {
            Text("Conectando con la Central…", color = Palette.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        HorizontalDivider(color = Palette.line, modifier = Modifier.padding(vertical = 4.dp))
        Text("o escribe el código a mano", color = Palette.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        OutlinedTextField(
            code, { code = it.trim(); error = null },
            label = { Text("Código de activación") }, singleLine = true, shape = Ui.smallShape,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        ActionButton(
            if (license.checking) "Activando…" else "Activar", Palette.primary, Color.White,
            Modifier.fillMaxWidth(), enabled = code.isNotBlank() && !license.checking,
        ) { scope.launch { error = license.activate(code) } }
        TextButton(onClick = onExit) { Text("Cambiar rol de este teléfono") }
    }
}

@Composable
private fun LockedScreen(license: LicenseManager, state: LicenseState.Locked, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val who = state.tenantName.ifBlank { "esta taquería" }
    val (title, body) = when (state.reason) {
        LockReason.SUSPENDED -> "Cuenta suspendida" to "La cuenta de $who está suspendida. Comunícate con tu proveedor; en cuanto la reactive, toca Reintentar con internet."
        LockReason.REVOKED -> "Teléfono no autorizado" to "El código de este teléfono ya no es válido. Pide uno nuevo a tu proveedor."
        LockReason.EXPIRED -> "Falta confirmar la cuenta" to "Hace mucho que este teléfono no se conecta. Conéctalo a internet (WiFi con internet o datos) y toca Reintentar."
    }
    Centered {
        Text("🔒", fontSize = 56.sp)
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Text(body, color = Palette.muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        Text("Las órdenes y el menú están guardados; no se pierde nada.", color = Palette.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        error?.let { Text(it, color = Palette.late, fontSize = 14.sp, textAlign = TextAlign.Center) }
        if (state.reason != LockReason.REVOKED) {
            ActionButton(
                if (license.checking) "Consultando…" else "Reintentar", Palette.primary, Color.White,
                Modifier.fillMaxWidth(), enabled = !license.checking,
            ) { scope.launch { error = license.refresh() } }
        } else {
            ActionButton("Escribir otro código", Palette.primary, Color.White, Modifier.fillMaxWidth()) { license.forget() }
        }
        TextButton(onClick = onExit) { Text("Cambiar rol de este teléfono") }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Ui.pad),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(Ui.gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

private fun dateText(ms: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM", java.util.Locale.forLanguageTag("es-MX"))
        .format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()))
