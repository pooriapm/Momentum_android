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

/** One logged period: start date and, once known, end date (ISO yyyy-mm-dd). */
@Serializable
data class CycleEntry(val start: String, val end: String? = null)

/** What the user agreed to, and the dates they logged. */
@Serializable
data class CycleData(val consentVersion: Int, val consentedAt: String, val entries: List<CycleEntry> = emptyList())

/**
 * Optional cycle log. It stays on this phone only: encrypted with its own Android Keystore key,
 * stored per account (another account on the same phone cannot read it), never sent to the server,
 * analytics, notifications or the widget, and never used by the plan. App backup is off
 * (allowBackup=false), so uninstalling the app removes it. Deleting removes everything at once.
 */
class CycleStore(context: Context) {
    private val prefs = context.getSharedPreferences("momentum.cycle", Context.MODE_PRIVATE)

    fun load(userId: String): CycleData? {
        val sealed = prefs.getString(userId, null) ?: return null
        return runCatching { json.decodeFromString(CycleData.serializer(), decrypt(sealed)) }.getOrNull()
    }

    fun save(userId: String, data: CycleData) {
        prefs.edit().putString(userId, encrypt(json.encodeToString(CycleData.serializer(), data))).commit()
    }

    fun delete(userId: String) {
        prefs.edit().remove(userId).commit()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
        }
        return String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES))
    }

    companion object {
        const val CONSENT_VERSION = 1
        private const val KEY_ALIAS = "momentum.cycle.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}
