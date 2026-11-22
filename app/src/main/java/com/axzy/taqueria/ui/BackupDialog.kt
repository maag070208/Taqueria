package com.axzy.taqueria.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.AppState
import com.axzy.taqueria.BackupSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val whenFmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.forLanguageTag("es-MX"))

private fun whenText(iso: String) =
    runCatching { whenFmt.format(OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())) }.getOrDefault(iso)

private fun AppState.summary(): String {
    val open = openAccounts().count { it.cancelledAt == null }
    return "${if (open == 1) "1 cuenta abierta" else "$open cuentas abiertas"} · ${products.size} productos"
}

/** Admin: recuperar en este teléfono el último respaldo de la taquería en la Central. */
@Composable
fun BackupDialog(backup: BackupSync, current: AppState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lastBackup by backup.lastBackupAt.collectAsState()
    var result by remember { mutableStateOf<BackupSync.Restore?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { result = backup.fetch() }

    val all = (result as? BackupSync.Restore.Found)?.backups.orEmpty()
    // Por defecto el de este mismo teléfono; si no tiene, el más reciente.
    val found = all.firstOrNull { it.deviceId == chosen } ?: all.firstOrNull { it.fromThisDevice } ?: all.firstOrNull()
    AlertDialog(
        onDismissRequest = { if (!restoring) onDismiss() },
        title = { Text("Respaldo en la Central") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    lastBackup?.let { "Este teléfono se respaldó a las ${DateTimeFormatter.ofPattern("HH:mm").format(java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))}." }
                        ?: "Este teléfono se respalda solo cada minuto que hay cambios y hay internet.",
                    fontSize = 14.sp, color = Palette.muted,
                )
                when (val r = result) {
                    null -> CircularProgressIndicator()
                    BackupSync.Restore.None -> Text("Esta taquería todavía no tiene respaldo.")
                    BackupSync.Restore.NotActivated -> Text("Activa la licencia de este teléfono para usar el respaldo.")
                    is BackupSync.Restore.Failed -> Text(r.message, color = Palette.late)
                    is BackupSync.Restore.Found -> {
                        val b = found!!
                        if (r.backups.size > 1) {
                            Text("Hay respaldos de ${r.backups.size} cocinas. Elige cuál:", fontSize = 14.sp)
                            r.backups.forEach { o ->
                                Row(Modifier.fillMaxWidth().clickable { chosen = o.deviceId }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = o === b, onClick = { chosen = o.deviceId })
                                    Text("«${o.deviceName}»${if (o.fromThisDevice) " (este teléfono)" else ""} · ${whenText(o.savedAt)}", fontSize = 14.sp)
                                }
                            }
                        }
                        Text("Último respaldo: ${whenText(b.savedAt)}, de «${b.deviceName}»${if (b.fromThisDevice) " (este teléfono)" else ""}.")
                        Text("Trae: ${b.state.summary()}.")
                        Text("Este teléfono tiene: ${current.summary()}.")
                        Text(
                            "Recuperar reemplaza las cuentas abiertas y el menú de este teléfono por los del respaldo. Úsalo si el teléfono de cocina se perdió o se reinició.",
                            fontSize = 14.sp, color = Palette.late,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (found != null) {
                TextButton(enabled = !restoring, onClick = {
                    restoring = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { backup.restore(found) }.isSuccess }
                        Toast.makeText(context, if (ok) "Respaldo recuperado" else "No se pudo recuperar el respaldo", Toast.LENGTH_SHORT).show()
                        restoring = false
                        if (ok) onDismiss()
                    }
                }) { Text(if (restoring) "Recuperando…" else "Recuperar", color = Palette.late) }
            }
        },
        dismissButton = { TextButton(enabled = !restoring, onClick = onDismiss) { Text("Cerrar") } },
    )
}
