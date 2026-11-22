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

    /** Tacos y bebidas tienen ilustración por producto; lo demás usa la de su categoría. */
    fun productKeys(p: Product, c: Category): List<String> {
        val n = slug(p.name).let { alias[it] ?: it }
        val own = when (slug(c.name)) {
            "tacos" -> "taco_$n"
            "bebidas" -> "beb_$n"
            else -> "esp_$n"
        }
        return listOf(own) + categoryKeys(c)
    }

    @SuppressLint("DiscouragedApi")
    fun resolve(context: Context, keys: List<String>): Int? = keys.firstNotNullOfOrNull { key ->
        cache.getOrPut(key) { context.resources.getIdentifier("ico_$key", "drawable", context.packageName) }.takeIf { it != 0 }
    }
}

/** Ilustración del platillo; si el producto no tiene una propia, la de su categoría o el taco genérico. */
@Composable
fun DishIcon(keys: List<String>, size: Dp, modifier: Modifier = Modifier) {
    val id = DishIcons.resolve(LocalContext.current, keys) ?: R.drawable.ico_taco_generico
    Image(painterResource(id), contentDescription = null, modifier = modifier.size(size))
}
