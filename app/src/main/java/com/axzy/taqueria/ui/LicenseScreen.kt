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
import com.axzy.taqueria.R
import com.axzy.taqueria.LicenseManager
import com.axzy.taqueria.LicenseState
import com.axzy.taqueria.LockReason
import com.axzy.taqueria.QrPayload
import com.axzy.taqueria.QrResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import com.axzy.taqueria.Giro
import com.axzy.taqueria.app

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
        is LicenseState.Active -> {
            val icons = LocalContext.current.app.icons
            val meta by icons.meta.collectAsState()
            val ref = license.giro.iconSet
            var skipped by remember { mutableStateOf(false) }
            // Solo la primera vez (o al cambiar de giro): una versión nueva del mismo set baja sin interrumpir.
            if (ref != null && meta?.id != ref.id && !skipped) {
                PreparingScreen(s.tenantName, license.giro, onSkip = { skipped = true })
            } else {
                content(
                    if (s.expiresSoon) "Conecta este teléfono a internet antes del ${dateText(s.validUntilMs)} para que la app siga funcionando." else null,
                )
            }
        }
    }
}

/** Recién activada en un giro con iconos propios: espera a que bajen (con salida por si no hay internet). */
@Composable
private fun PreparingScreen(tenant: String, giro: Giro, onSkip: () -> Unit) {
    var waited by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            waited++
        }
    }
    Column(
        Modifier.fillMaxSize().background(Palette.bg).safeDrawingPadding().padding(Ui.pad),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("● Licencia activada", color = Palette.ok, fontSize = 14.sp)
        Text(tenant, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text("${giro.name} · ${giro.terms.kitchenRole}, ${giro.terms.waiterRole.lowercase()}", color = Palette.muted, fontSize = 15.sp)
        Column(
            Modifier.fillMaxWidth().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape).padding(Ui.pad),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Preparando la app", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Palette.primary, strokeWidth = 3.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Iconos de ${giro.name.lowercase()}", fontWeight = FontWeight.Medium)
                    Text("Bajando de la Central…", color = Palette.muted, fontSize = 13.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = Palette.ok, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Palabras del giro", fontWeight = FontWeight.Medium)
                    Text("${giro.terms.kitchenRole} · ${giro.terms.waiterRole} · ${giro.terms.item}", color = Palette.muted, fontSize = 13.sp)
                }
            }
        }
        // Sin internet no se queda atorada: después de unos segundos se puede seguir con los iconos de la app.
        if (waited >= 8) {
            Text("¿Sin internet? Puedes seguir; los iconos bajan solos en cuanto haya conexión.", color = Palette.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            ActionButton("Seguir sin esperar", Palette.card, Palette.primary, Modifier.fillMaxWidth(), onClick = onSkip)
        }
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
        Illustration(R.drawable.ico_rol_admin, 88.dp)
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
        Illustration(R.drawable.ico_candado, 88.dp)
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
