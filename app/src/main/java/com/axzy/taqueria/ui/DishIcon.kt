package com.axzy.taqueria.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.axzy.taqueria.Category
import com.axzy.taqueria.Product
import com.axzy.taqueria.R
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.axzy.taqueria.app
import java.io.File
import java.text.Normalizer

/**
 * Íconos ilustrados del menú (res/drawable-nodpi/ico_*.png, fuente en design/iconos).
 * Se buscan por nombre para que un ícono nuevo (ej. taco_pastor) funcione en cuanto se
 * agrega el archivo, sin tocar código; si no hay ilustración se usa el taco genérico.
 */
object DishIcons {
    /** Nombres del menú que tienen ilustración con otro nombre. */
    private val alias = mapOf("mixto" to "campechano", "agua_de_sabor" to "agua_fresca")
    private val cache = HashMap<String, Int>()

    fun slug(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            .replace(Regex("[^a-z0-9]+"), "_").trim('_')

    fun categoryKeys(c: Category) = listOf("cat_${slug(c.name)}", "esp_${slug(c.singular)}")

    /** Marca de "busca por nombre en el set del giro" dentro de la lista de llaves. */
    const val BY_NAME = "nombre:"

    /**
     * Orden de búsqueda: la llave que eligió el admin (`p.icon`), luego por nombre en el set del giro
     * ("Dragón" → `rollo_dragon`), y al final las ilustraciones de taquería de siempre.
     */
    fun productKeys(p: Product, c: Category): List<String> {
        val n = slug(p.name).let { alias[it] ?: it }
        val own = when (slug(c.name)) {
            "tacos" -> "taco_$n"
            "bebidas" -> "beb_$n"
            else -> "esp_$n"
        }
        return listOfNotNull(p.icon?.takeIf { it.isNotBlank() }, "$BY_NAME$n", own) + categoryKeys(c)
    }

    @SuppressLint("DiscouragedApi")
    fun resolve(context: Context, keys: List<String>): Int? = keys.firstNotNullOfOrNull { key ->
        cache.getOrPut(key) { context.resources.getIdentifier("ico_$key", "drawable", context.packageName) }.takeIf { it != 0 }
    }
}

/** PNG del set ya leídos, por ruta y versión del set (un icono pesa ~20 kB). */
private val bitmaps = object : LruCache<String, ImageBitmap>(160) {}

private fun bitmapOf(file: File, version: Int): ImageBitmap? =
    bitmaps.get("$version:${file.path}") ?: BitmapFactory.decodeFile(file.path)?.asImageBitmap()?.also { bitmaps.put("$version:${file.path}", it) }

/**
 * Ilustración del platillo. Primero el set del giro que bajó la Central (llave explícita o por nombre),
 * luego las ilustraciones de taquería del APK, y al final el genérico del giro o el taco genérico.
 */
@Composable
fun DishIcon(keys: List<String>, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = context.app.icons
    val meta by store.meta.collectAsState()
    val version = meta?.version ?: 0
    val fromSet = remember(keys, meta) {
        if (meta == null) null
        else keys.firstNotNullOfOrNull { k ->
            if (k.startsWith(DishIcons.BY_NAME)) store.byName(k.removePrefix(DishIcons.BY_NAME)) else store.file(k)
        }
    }
    val bitmap = fromSet?.let { bitmapOf(it, version) }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modifier.size(size))
        return
    }
    val res = DishIcons.resolve(context, keys.filterNot { it.startsWith(DishIcons.BY_NAME) })
    val generic = if (res == null && meta != null) store.generic()?.let { bitmapOf(it, version) } else null
    if (generic != null) Image(generic, contentDescription = null, modifier = modifier.size(size))
    else Image(painterResource(res ?: R.drawable.ico_taco_generico), contentDescription = null, modifier = modifier.size(size))
}
