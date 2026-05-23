package com.minfinrobot.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * EncryptedSharedPreferences: AES256-SIV для ключей, AES256-GCM для значений.
 * Мастер-ключ — Android Keystore.
 *
 * Хранит:
 *  - sandbox/production токены (отдельно)
 *  - флаг текущего режима
 *  - URL последней обработанной публикации (для дедупликации)
 */
class SecureSettingsStore(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "minfin_robot_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var isSandbox: Boolean
        get() = prefs.getBoolean(KEY_SANDBOX, true)
        set(value) = prefs.edit().putBoolean(KEY_SANDBOX, value).apply()

    fun getToken(): String =
        if (isSandbox) prefs.getString(KEY_TOKEN_SANDBOX, "").orEmpty()
        else prefs.getString(KEY_TOKEN_PROD, "").orEmpty()

    fun setSandboxToken(token: String) {
        prefs.edit().putString(KEY_TOKEN_SANDBOX, token).apply()
    }

    fun setProductionToken(token: String) {
        prefs.edit().putString(KEY_TOKEN_PROD, token).apply()
    }

    fun hasSandboxToken(): Boolean = !prefs.getString(KEY_TOKEN_SANDBOX, "").isNullOrEmpty()
    fun hasProductionToken(): Boolean = !prefs.getString(KEY_TOKEN_PROD, "").isNullOrEmpty()

    var lastProcessedPublicationUrl: String?
        get() = prefs.getString(KEY_LAST_PUB_URL, null)
        set(value) = prefs.edit().putString(KEY_LAST_PUB_URL, value).apply()

    companion object {
        private const val KEY_SANDBOX = "is_sandbox"
        private const val KEY_TOKEN_SANDBOX = "token_sandbox"
        private const val KEY_TOKEN_PROD = "token_production"
        private const val KEY_LAST_PUB_URL = "last_pub_url"
    }
}
