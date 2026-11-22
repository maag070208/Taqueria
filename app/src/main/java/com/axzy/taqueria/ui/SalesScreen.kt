package com.axzy.taqueria.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.R
import com.axzy.taqueria.HistoryEntry
import com.axzy.taqueria.OrderStore
import com.axzy.taqueria.PayMethod
import com.axzy.taqueria.PaymentRecord
import com.axzy.taqueria.SalesPeriod
import com.axzy.taqueria.clockText
import com.axzy.taqueria.dayLabel
import com.axzy.taqueria.money
import com.axzy.taqueria.range
import com.axzy.taqueria.salesReport
import com.axzy.taqueria.shift
import com.axzy.taqueria.shortDayLabel
import com.axzy.taqueria.tableLabel
import com.axzy.taqueria.title
import com.axzy.taqueria.toCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Ventas del teléfono de cocina: resumen, por día, productos e historial de cobros, con exportación a CSV. */
@Composable
fun SalesScreen(store: OrderStore, onMenu: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by store.state.collectAsState()
    val today = LocalDate.now()
    var periodIndex by rememberSaveable { mutableStateOf(SalesPeriod.DAY.ordinal) }
    var anchorDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val period = SalesPeriod.entries[periodIndex]
    val anchor = LocalDate.ofEpochDay(anchorDay)
    val range = period.range(anchor)
    // No se reportan días que aún no llegan.
    val to = minOf(range.endInclusive, today)

    // Cada cambio de estado (cobro nuevo, cuenta archivada) recalcula; el historial ya está en memoria.
    var entries by remember { mutableStateOf<List<HistoryEntry>?>(null) }
    LaunchedEffect(state) { entries = withContext(Dispatchers.IO) { store.salesEntries() } }
    val report = remember(entries, range.start, to) { entries?.let { salesReport(it, range.start, to) } }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = report?.toCsv() ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                        ?: error("No se pudo abrir el archivo")
                }.exceptionOrNull()
            }
            Toast.makeText(context, error?.message ?: "Reporte guardado", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(title = "Ventas", subtitle = "Lo vendido y lo cobrado en este teléfono", onMenu = onMenu) {
            SquareButton(R.drawable.ic_download, description = "Exportar", onClick = {
                val name = if (range.start == to) "ventas-${range.start}.csv" else "ventas-${range.start}_a_$to.csv"
                if (report != null) export.launch(name)
            })
        }
        Segmented(
            SalesPeriod.entries.map { it.label }, periodIndex,
            { periodIndex = it; anchorDay = today.toEpochDay() },
            Modifier.padding(horizontal = Ui.pad),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Ui.pad, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SquareButton(R.drawable.ic_chevron_left, description = "Periodo anterior", onClick = { anchorDay = period.shift(anchor, -1).toEpochDay() })
            Text(
                (if (today in range.start..range.endInclusive && period == SalesPeriod.DAY) "Hoy · " else "") + period.title(anchor),
                fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // Hacia adelante solo hasta el periodo actual.
            if (range.endInclusive < today) {
                SquareButton(R.drawable.ic_chevron_right, description = "Periodo siguiente", onClick = { anchorDay = period.shift(anchor, 1).toEpochDay() })
            } else {
                Spacer(Modifier.size(44.dp))
            }
        }

        if (report == null) {
            Text("Cargando…", color = Palette.muted, modifier = Modifier.padding(Ui.pad))
            return@Column
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = Ui.pad, end = Ui.pad, bottom = Ui.pad),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") {
                val tiles = buildList {
                    add("Vendido" to money(report.soldCents))
                    add("Cobrado" to money(report.collectedCents))
                    add("Efectivo" to money(report.collectedBy(PayMethod.CASH)))
                    add("Transferencia" to money(report.collectedBy(PayMethod.TRANSFER)))
                    add("Cuentas" to report.accounts.toString())
                    add("Ticket promedio" to money(report.averageTicketCents))
                    if (report.refundsCents > 0) add("Devoluciones" to money(report.refundsCents))
                }
                BoxWithConstraints {
                    Grid(tiles, columnsFor(maxWidth, 150.dp, 2..4)) { (label, value) -> SummaryTile(label, value) }
                }
            }

            if (report.days.size > 1) {
                item(key = "h-days") { SectionTitle("Por día", Modifier.padding(top = 8.dp)) }
                items(report.days.reversed(), key = { "d-${it.date}" }) { d ->
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(dayLabel(d.date), fontWeight = FontWeight.Bold)
                                Text("${d.accounts} ${if (d.accounts == 1) "cuenta" else "cuentas"} · vendido ${money(d.soldCents)}", color = Palette.muted, fontSize = 13.sp)
                            }
                            Text(money(d.collectedCents), fontSize = 17.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            item(key = "h-products") { SectionTitle("Productos", Modifier.padding(top = 8.dp)) }
            if (report.products.isEmpty()) {
                item(key = "no-products") { Text("Sin ventas en este periodo", color = Palette.muted) }
            }
            items(report.products) { p ->
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.qty}", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Palette.primaryDark, modifier = Modifier.padding(end = 12.dp))
                        Text(p.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(money(p.totalCents), fontWeight = FontWeight.Bold)
                    }
                }
            }

            item(key = "h-payments") { SectionTitle("Cobros", Modifier.padding(top = 8.dp)) }
            if (report.payments.isEmpty()) {
                item(key = "no-payments") { Text("Sin cobros en este periodo", color = Palette.muted) }
            }
            items(report.payments, key = { "c-${it.payment.id}" }) { r -> PaymentRow(r, showDate = period != SalesPeriod.DAY) }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String) {
    Column(Modifier.fillMaxSize().background(Palette.card, Ui.shape).border(1.dp, Palette.line, Ui.shape).padding(14.dp)) {
        Text(label, color = Palette.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.card, Ui.smallShape).border(1.dp, Palette.line, Ui.smallShape).padding(12.dp)) {
        content()
    }
}

@Composable
private fun PaymentRow(r: PaymentRecord, showDate: Boolean) {
    val p = r.payment
    val date = Instant.ofEpochMilli(p.at).atZone(ZoneId.systemDefault()).toLocalDate()
    val change = p.receivedCents?.let { it - p.amountCents }?.takeIf { it > 0 }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${tableLabel(r.table)} · Cuenta ${r.accountNumber}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        (if (showDate) "${shortDayLabel(date)} " else "") + clockText(p.at),
                        p.method.label,
                        p.receivedCents?.let { "recibió ${money(it)}" },
                        change?.let { "cambio ${money(it)}" },
                    ).joinToString(" · "),
                    color = Palette.muted, fontSize = 13.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    money(p.amountCents), fontSize = 17.sp, fontWeight = FontWeight.Black,
                    color = if (p.voided) Palette.muted else Palette.text,
                    textDecoration = if (p.voided) TextDecoration.LineThrough else null,
                )
                when {
                    p.voided -> StatusChip("Anulado", Palette.muted)
                    p.amountCents < 0 -> StatusChip("Devolución", Palette.warn)
                }
            }
        }
    }
}
