package com.pooriapm.momentum.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** Access-token expiry, epoch seconds. */
    val expiresAt: Long,
    val userId: String,
    val email: String,
    /** Last time the session was used or refreshed, epoch seconds; drives the sliding 180-day limit. */
    val lastActiveAt: Long = System.currentTimeMillis() / 1000,
)

/**
 * Keeps the session on the device, encrypted with an AES-GCM key that never leaves the Android
 * Keystore. A copied app-data backup is useless without that hardware-backed key.
 *
 * Policy: a session stays signed in for 180 days after its last use, and every use or token
 * refresh slides that window forward. Only inactivity, a server-side revocation, or an explicit
 * sign-out ends it; network problems never do.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("momentum.secure", Context.MODE_PRIVATE)

    init {
        // Earlier builds kept the session in plain text; drop it rather than migrate secrets.
        context.getSharedPreferences("momentum.session", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun load(): Session? {
        val stored = prefs.getString(KEY_SESSION, null) ?: return null
        val session = runCatching { json.decodeFromString(Session.serializer(), decrypt(stored)) }.getOrNull()
        if (session == null || isExpiredByInactivity(session)) {
            clear()
            return null
        }
        return session
    }

    fun save(session: Session) {
        prefs.edit().putString(KEY_SESSION, encrypt(json.encodeToString(Session.serializer(), session))).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    fun isExpiredByInactivity(session: Session, now: Long = System.currentTimeMillis() / 1000): Boolean =
        now - session.lastActiveAt > INACTIVITY_LIMIT_SECONDS

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
        }
        return String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    }

    companion object {
        const val INACTIVITY_LIMIT_SECONDS = 180L * 24 * 60 * 60
        private const val KEY_SESSION = "session"
        private const val KEY_ALIAS = "momentum.session.key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}
