package ru.e1173.nookast.core.session

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONException
import org.json.JSONObject
import ru.e1173.nookast.auth.AuthSession

/** Outcome of reading the encrypted session from disk. */
sealed interface SessionRead {
    data class Present(val session: AuthSession) : SessionRead

    /** Nothing is stored. */
    data object Absent : SessionRead

    /**
     * The stored session can never be decrypted again (tampered ciphertext, key invalidated or
     * lost, malformed payload). It has been deleted.
     */
    data class Corrupt(val cause: Throwable) : SessionRead

    /**
     * A temporary keystore failure. The ciphertext is kept and the read may succeed later; callers
     * must not treat this as a sign-out.
     */
    data class Unavailable(val cause: Throwable) : SessionRead
}

/**
 * Persists the API session encrypted with an AES-256-GCM key held in AndroidKeyStore.
 *
 * Every call does keystore work; use [SessionRepository], which decrypts once per process and
 * caches the result, instead of calling this class from hot paths.
 */
class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    // commit() result is required to detect a failed write, which the KTX edit {} hides.
    @SuppressLint("UseKtx")
    fun save(session: AuthSession) {
        val plaintext = JSONObject()
            .put("server_url", session.serverUrl)
            .put("user_id", session.userId)
            .put("api_key", session.apiKey)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plaintext)

        val stored = preferences.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        if (!stored) {
            throw IOException("Encrypted session could not be committed")
        }
    }

    fun read(): SessionRead {
        val encryptedValue = preferences.getString(KEY_CIPHERTEXT, null) ?: return SessionRead.Absent
        val ivValue = preferences.getString(KEY_IV, null) ?: return SessionRead.Absent

        return try {
            val key = existingKey() ?: return corrupt(IllegalStateException("Session key is missing"))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivValue, Base64.NO_WRAP)),
            )
            val plaintext = cipher.doFinal(Base64.decode(encryptedValue, Base64.NO_WRAP))
            val json = JSONObject(plaintext.toString(Charsets.UTF_8))
            SessionRead.Present(
                AuthSession(
                    serverUrl = json.getString("server_url"),
                    userId = json.getInt("user_id"),
                    apiKey = json.getString("api_key"),
                ),
            )
        } catch (error: Exception) {
            if (isPermanent(error)) corrupt(error) else SessionRead.Unavailable(error)
        }
    }

    @SuppressLint("UseKtx")
    fun clear(): Boolean = preferences.edit().clear().commit()

    private fun corrupt(cause: Throwable): SessionRead {
        clear()
        return SessionRead.Corrupt(cause)
    }

    private fun existingKey(): SecretKey? = synchronized(keyLock) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun getOrCreateKey(): SecretKey = synchronized(keyLock) {
        existingKey()?.let { return it }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    internal companion object {
        const val PREFERENCES_NAME = "encrypted_session"
        const val KEY_CIPHERTEXT = "session_ciphertext"
        const val KEY_IV = "session_iv"
        const val KEY_ALIAS = "pinepods_session_key_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128

        // Key creation and lookup must not race between the UI, the playback service and workers.
        private val keyLock = Any()

        /**
         * Only failures that can never succeed on retry delete the session. Everything else
         * (KeyStoreException, ProviderException, binder errors) is treated as transient.
         */
        fun isPermanent(error: Throwable): Boolean = when (error) {
            is AEADBadTagException,
            is KeyPermanentlyInvalidatedException,
            is UnrecoverableKeyException,
            is JSONException,
            is IllegalArgumentException,
            -> true
            else -> false
        }
    }
}
