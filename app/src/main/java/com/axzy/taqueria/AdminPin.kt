package com.axzy.taqueria

import androidx.annotation.DrawableRes
import java.security.MessageDigest
import java.security.SecureRandom

/** Quién usa el teléfono. Cocina arranca como taquero; admin se desbloquea con PIN en ese mismo teléfono. */
enum class Staff(val label: String, @DrawableRes val art: Int) {
    ADMIN("Admin", R.drawable.ico_rol_admin),
    TAQUERO("Taquero", R.drawable.ico_rol_taquero),
    MESERO("Mesero", R.drawable.ico_rol_mesero),
}

/**
 * PIN de 4 dígitos del admin, guardado como hash con sal (nunca en claro).
 * Tras [MAX_TRIES] errores seguidos se bloquea [LOCK_MS] para que no se pueda adivinar probando.
 */
class AdminPin(
    private val get: (String) -> String?,
    private val put: (Map<String, String?>) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    sealed interface Result {
        data object Ok : Result
        data class Wrong(val triesLeft: Int) : Result
        data class Locked(val secondsLeft: Int) : Result
    }

    val isSet get() = get(HASH) != null

    fun set(pin: String) {
        require(isValid(pin)) { "El PIN debe tener 4 dígitos" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        put(mapOf(SALT to salt, HASH to hash(salt, pin), FAILS to null, LOCK_UNTIL to null))
    }

    fun check(pin: String): Result {
        val lockUntil = get(LOCK_UNTIL)?.toLongOrNull() ?: 0
        if (now() < lockUntil) return Result.Locked(((lockUntil - now() + 999) / 1000).toInt())
        val salt = get(SALT) ?: return Result.Wrong(MAX_TRIES)
        val expected = get(HASH) ?: return Result.Wrong(MAX_TRIES)
        if (MessageDigest.isEqual(hash(salt, pin).toByteArray(), expected.toByteArray())) {
            put(mapOf(FAILS to null, LOCK_UNTIL to null))
            return Result.Ok
        }
        val fails = (get(FAILS)?.toIntOrNull() ?: 0) + 1
        return if (fails >= MAX_TRIES) {
            put(mapOf(FAILS to null, LOCK_UNTIL to (now() + LOCK_MS).toString()))
            Result.Locked((LOCK_MS / 1000).toInt())
        } else {
            put(mapOf(FAILS to fails.toString()))
            Result.Wrong(MAX_TRIES - fails)
        }
    }

    companion object {
        const val MAX_TRIES = 5
        const val LOCK_MS = 30_000L
        private const val SALT = "adminPinSalt"
        private const val HASH = "adminPinHash"
        private const val FAILS = "adminPinFails"
        private const val LOCK_UNTIL = "adminPinLockUntil"

        fun isValid(pin: String) = pin.length == 4 && pin.all { it in '0'..'9' }

        private fun hash(salt: String, pin: String) =
            MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).toHex()

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
