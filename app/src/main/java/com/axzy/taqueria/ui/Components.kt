package com.axzy.taqueria.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animate
import com.axzy.taqueria.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------- Tiempos de espera ----------------

/** Umbrales de espera en minutos: verde, amarillo, rojo y rojo con pulso. */
const val WARN_MIN = 5
const val LATE_MIN = 10
const val CRITICAL_MIN = 15

fun waitColor(elapsedMs: Long): Color {
    val min = elapsedMs / 60_000
    return when {
        min >= LATE_MIN -> Palette.late
        min >= WARN_MIN -> Palette.warn
        else -> Palette.ok
    }
}

fun isCritical(elapsedMs: Long) = elapsedMs / 60_000 >= CRITICAL_MIN

// ---------------- Botones ----------------

@Composable
fun ActionButton(
    label: String, container: Color, content: Color, modifier: Modifier = Modifier,
    enabled: Boolean = true, @DrawableRes icon: Int? = null, onClick: () -> Unit,
) {
    val bg = if (enabled) container else Palette.line
    Box(
        modifier.height(Ui.control).background(bg, Ui.smallShape)
            .border(1.dp, if (bg == Palette.card) Palette.line else bg, Ui.smallShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val fg = if (enabled) content else Palette.muted
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) UiIcon(icon, 20.dp, fg, Modifier.padding(end = 8.dp))
            Text(label, color = fg, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Opción seleccionable de altura fija; dentro de [Grid] todas quedan del mismo ancho. */
@Composable
fun OptionChip(label: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(44.dp)
            .background(if (selected) Palette.primary else if (enabled) Palette.card else Palette.bg, Ui.smallShape)
            .border(1.dp, if (selected) Palette.primary else Palette.line, Ui.smallShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            color = if (selected) Color.White else if (enabled) Palette.text else Palette.muted.copy(alpha = 0.5f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** − cantidad + con botones grandes y cuadrados. */
@Composable
fun Stepper(value: Int, small: Boolean = false, onChange: (Int) -> Unit) {
    val size = if (small) 36.dp else 44.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 8.dp)) {
        StepButton("−", size) { onChange(value - 1) }
        Glyph("$value", if (small) 18.sp else 22.sp, Modifier.width(if (small) 28.dp else 36.dp), weight = FontWeight.Black)
        StepButton("+", size) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).background(Palette.soft, Ui.smallShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(label, 22.sp, color = Palette.primaryDark, weight = FontWeight.Black) }
}

@Composable
fun StatusChip(text: String, color: Color, filled: Boolean = false) {
    Box(
        Modifier.background(if (filled) color else color.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(text, color = if (filled) Color.White else color, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
}

/** Selector de pestañas del mismo ancho. */
@Composable
fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(48.dp).background(Palette.line, Ui.smallShape).padding(4.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).fillMaxSize()
                    .background(if (on) Palette.card else Color.Transparent, RoundedCornerShape(9.dp))
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(label, fontWeight = FontWeight.Bold, color = if (on) Palette.text else Palette.muted, maxLines = 1) }
        }
    }
}

// ---------------- Deslizar para actuar ----------------

/**
 * Tarjeta que ejecuta una acción al deslizarla más de un tercio de su ancho.
 * Mientras se desliza se ve qué hará; si no llega al umbral regresa sin hacer nada.
 * El arrastre solo empieza en horizontal, así no choca con el scroll vertical.
 */
@Composable
fun SwipeCard(
    leftLabel: String, onSwipeLeft: (() -> Unit)?,
    rightLabel: String, onSwipeRight: (() -> Unit)?,
    shape: Shape = Ui.shape,
    content: @Composable () -> Unit,
) {
    var dx by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    val threshold = width / 3f
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
        if (dx != 0f) {
            val right = dx > 0
            val armed = abs(dx) >= threshold
            Box(
                Modifier.matchParentSize()
                    .background((if (right) Palette.ok else Palette.late).copy(alpha = if (armed) 1f else 0.55f), shape)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UiIcon(if (right) R.drawable.ic_check else R.drawable.ic_close, 20.dp, Color.White, Modifier.padding(end = 8.dp))
                    Text(if (right) rightLabel else leftLabel, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(dx.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val min = if (onSwipeLeft != null) -width.toFloat() else 0f
                        val max = if (onSwipeRight != null) width.toFloat() else 0f
                        dx = (dx + delta).coerceIn(min, max)
                    },
                    onDragStopped = {
                        when {
                            dx >= threshold -> onSwipeRight?.invoke()
                            dx <= -threshold -> onSwipeLeft?.invoke()
                        }
                        animate(dx, 0f) { v, _ -> dx = v }
                    },
                ),
        ) { content() }
    }
}

// ---------------- Deshacer ----------------

class UndoItem(val message: String, val undo: suspend () -> Unit) {
    val id = System.nanoTime()
}

class UndoState {
    var item by mutableStateOf<UndoItem?>(null)
    fun show(message: String, undo: suspend () -> Unit) { item = UndoItem(message, undo) }
}

/** Barra temporal con "Deshacer" (6 s). */
@Composable
fun UndoBar(state: UndoState, onUndo: (UndoItem) -> Unit) {
    val item = state.item ?: return
    LaunchedEffect(item.id) {
        delay(6_000)
        if (state.item?.id == item.id) state.item = null
    }
    Row(
        Modifier.padding(horizontal = Ui.pad, vertical = 8.dp).fillMaxWidth().height(Ui.control)
            .background(Palette.text, Ui.smallShape).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.message, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.width(120.dp).fillMaxHeight().clickable { state.item = null; onUndo(item) },
            contentAlignment = Alignment.Center,
        ) { Text("Deshacer", color = Color(0xFF8FD8B8), fontWeight = FontWeight.Black) }
    }
}

/** Aviso de versión nueva: al tocarlo abre el enlace de descarga que publicó el proveedor. */
@Composable
fun UpdateBanner(update: com.axzy.taqueria.LatestApp, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val url = update.url
    val text = buildString {
        append("Hay versión nueva: ${update.versionName}")
        append(if (url != null) " · Toca para descargarla" else " · Pídela a tu proveedor")
        update.notes?.let { append("\n$it") }
    }
    Text(
        text, color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.fillMaxWidth().background(Palette.primary.copy(alpha = 0.14f), Ui.smallShape)
            .then(
                if (url == null) Modifier else Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
            .padding(12.dp),
    )
}
