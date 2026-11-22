package com.axzy.taqueria.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.axzy.taqueria.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.axzy.taqueria.Role
import kotlinx.coroutines.delay

object Palette {
    val bg = Color(0xFFF4F6F5)
    val card = Color.White
    val line = Color(0xFFE6EAE8)
    val soft = Color(0xFFE8F2EE)
    val primary = Color(0xFF3F8F6F)
    val primaryDark = Color(0xFF2F7358)
    val text = Color(0xFF1E2422)
    val muted = Color(0xFF8A938F)
    val ok = Color(0xFF3F8F6F)
    val warn = Color(0xFFF0A202)
    val late = Color(0xFFE5533D)
}

/** Medidas únicas de toda la app: mismo espacio y mismas esquinas en todas partes. */
object Ui {
    val gap = 12.dp
    val pad = 16.dp
    val shape = RoundedCornerShape(16.dp)
    val smallShape = RoundedCornerShape(12.dp)
    val control = 52.dp
}

@Composable
fun TaqueriaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.primary,
            onPrimary = Color.White,
            background = Palette.bg,
            surface = Palette.card,
            surfaceVariant = Palette.soft,
            surfaceContainerHigh = Palette.card,
            onBackground = Palette.text,
            onSurface = Palette.text,
            onSurfaceVariant = Palette.muted,
            outline = Palette.line,
            secondaryContainer = Palette.soft,
            onSecondaryContainer = Palette.primaryDark,
            error = Palette.late,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = Palette.bg, contentColor = Palette.text, content = content)
    }
}

/** Hora actual que se refresca cada segundo, para los cronómetros. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000 - System.currentTimeMillis() % 1_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Columnas que caben en el ancho disponible, para que la cuadrícula se adapte sin descuadrarse. */
fun columnsFor(width: Dp, min: Dp, range: IntRange) = ((width + Ui.gap) / (min + Ui.gap)).toInt().coerceIn(range)

/**
 * Fila de cuadrícula: todas las celdas del mismo ancho y de la misma altura
 * (la de la más alta); las celdas faltantes quedan vacías para no romper columnas.
 */
@Composable
fun <T> GridRow(items: List<T>, columns: Int, cell: @Composable (T) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Ui.gap)) {
        items.forEach { Box(Modifier.weight(1f).fillMaxHeight()) { cell(it) } }
        repeat(columns - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
fun <T> Grid(items: List<T>, columns: Int, cell: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Ui.gap)) {
        items.chunked(columns).forEach { GridRow(it, columns, cell) }
    }
}

fun <T> LazyListScope.gridRows(items: List<T>, columns: Int, key: (T) -> Any, cell: @Composable (T) -> Unit) {
    items(items.chunked(columns), key = { key(it.first()) }) { GridRow(it, columns, cell) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

/**
 * Texto centrado de verdad dentro de su caja: sin el relleno de fuente de Android y con la
 * altura de línea recortada arriba y abajo. Sin esto los emojis y símbolos (←, −, +) quedan
 * corridos hacia abajo o a un lado en botones y círculos.
 */
val centeredText = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** Ícono o símbolo de una sola línea, centrado en su caja. */
@Composable
fun Glyph(text: String, size: TextUnit, modifier: Modifier = Modifier, color: Color = Color.Unspecified, weight: FontWeight? = null) {
    Text(
        text, modifier = modifier, fontSize = size, lineHeight = size, color = color, fontWeight = weight,
        textAlign = TextAlign.Center, maxLines = 1, style = centeredText,
    )
}

/** Ícono de acción de la app (res/drawable/ic_*, generados por design/iconos/generar_ui.py), pintado del color dado. */
@Composable
fun UiIcon(@DrawableRes icon: Int, size: Dp = 22.dp, tint: Color = Palette.text, modifier: Modifier = Modifier) {
    Image(painterResource(icon), contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = modifier.size(size))
}

/** Botón cuadrado de barra superior. */
@Composable
fun SquareButton(@DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier, description: String? = null) {
    Box(
        modifier.size(44.dp).background(Palette.card, Ui.smallShape).border(1.dp, Palette.line, Ui.smallShape)
            .clickable(onClick = onClick, onClickLabel = description),
        contentAlignment = Alignment.Center,
    ) { UiIcon(icon) }
}

@Composable
fun TopBar(
    title: String, subtitle: String? = null, subtitleColor: Color = Palette.muted,
    onBack: (() -> Unit)? = null, onMenu: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Ui.pad, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) SquareButton(R.drawable.ic_back, onClick = onBack, modifier = Modifier.padding(end = 4.dp), description = "Regresar")
        if (onMenu != null) SquareButton(R.drawable.ic_menu, onClick = onMenu, modifier = Modifier.padding(end = 4.dp), description = "Abrir menú")
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle, color = subtitleColor, fontSize = 13.sp, maxLines = 1)
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** Ilustración del set (res/drawable-nodpi/ico_*) dentro de un círculo verde claro. */
@Composable
fun Illustration(@DrawableRes art: Int, size: Dp) {
    Box(Modifier.size(size).background(Palette.soft, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
        Image(painterResource(art), contentDescription = null, modifier = Modifier.size(size * 0.78f))
    }
}

@Composable
fun RoleScreen(onPick: (Role) -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(Ui.pad),
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.ico_taco_generico), contentDescription = null, modifier = Modifier.size(72.dp))
        Text("Tacomanda", fontSize = 34.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
        Text("¿Qué hará este teléfono?", color = Palette.muted, fontSize = 17.sp, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
        GridRow(listOf(Role.KITCHEN, Role.WAITER), 2) { role ->
            val (icon, title, body) = when (role) {
                Role.KITCHEN -> Triple(R.drawable.ico_rol_taquero, "Cocina", "El taquero ve las órdenes. El admin entra con PIN a ventas y menú.")
                Role.WAITER -> Triple(R.drawable.ico_rol_mesero, "Mesero", "Toma las órdenes de las mesas.")
            }
            Column(
                Modifier.fillMaxWidth().aspectRatio(0.8f).background(Palette.card, Ui.shape)
                    .border(1.dp, Palette.line, Ui.shape).clickable { onPick(role) }.padding(Ui.pad),
            ) {
                Illustration(icon, 64.dp)
                Spacer(Modifier.weight(1f))
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(body, color = Palette.muted, fontSize = 14.sp, minLines = 3, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Text(
            "Cocina y mesero deben estar en el mismo WiFi o en el hotspot del teléfono de cocina. No se necesita internet.",
            color = Palette.muted, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )
    }
}
