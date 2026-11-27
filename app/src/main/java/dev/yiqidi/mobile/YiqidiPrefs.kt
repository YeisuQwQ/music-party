package dev.yiqidi.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class YiqidiPrefs(context: Context) {
    private val data = context.getSharedPreferences("yiqidi", Context.MODE_PRIVATE)
    private val alias = "yiqidi-session-v1"

    private fun key(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun session(): String {
        val saved = data.getString("session_gcm", null) ?: return ""
        return runCatching {
            val parts = saved.split('.', limit = 2)
            if (parts.size != 2) return ""
            val secret = key(false) ?: return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun saveSession(value: String): Boolean {
        if (value.isBlank()) return data.edit().remove("session_gcm").remove("netease_cookie").commit()
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, checkNotNull(key(true)))
            val encoded = listOf(cipher.iv, cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
                .joinToString(".") { Base64.encodeToString(it, Base64.NO_WRAP) }
            data.edit().putString("session_gcm", encoded).remove("netease_cookie").commit()
        }.getOrDefault(false)
    }

    fun saveLibrary(value: String): Boolean {
        val edit = data.edit().putString("rust_library_v1", value)
        legacyLibraryKeys().forEach { edit.remove(it) }
        return edit.commit()
    }

    private fun legacyLibraryKeys() = data.all.keys.filter {
        it in setOf("offline_songs", "lists", "recent", "local_liked") || it.startsWith("lrc_")
    }

    fun deviceId(): String {
        val saved = data.getString("device_id", null)
        if (!saved.isNullOrBlank()) return saved
        val bytes = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val value = bytes.joinToString("") { "%02x".format(it) }
        data.edit().putString("device_id", value).apply()
        return value
    }

    fun searchHistory(): List<String> {
        val raw = data.getString("search_history", "[]") ?: "[]"
        return runCatching {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
    }

    fun addSearch(term: String): List<String> {
        val clean = term.trim().take(80)
        if (clean.isBlank()) return searchHistory()
        val next = (listOf(clean) + searchHistory().filterNot { it.equals(clean, ignoreCase = true) }).take(10)
        data.edit().putString("search_history", org.json.JSONArray(next).toString()).apply()
        return next
    }

    fun clearSearchHistory() {
        data.edit().remove("search_history").apply()
    }

    fun roomStickers(): String = data.getString("room_stickers_v1", "[]") ?: "[]"

    fun saveRoomStickers(value: String) {
        data.edit().putString("room_stickers_v1", value).apply()
    }

    fun library(): JSONObject {
        val saved = data.getString("rust_library_v1", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: args(
            "theme" to data.getInt("theme", 0),
            "lyricPrefs" to args("align" to data.getInt("lyric_align", 0), "shift" to data.getInt("lyric_shift", 0), "awake" to data.getBoolean("lyric_awake", false)),
        )
        if (legacyLibraryKeys().isNotEmpty()) saved.put("legacyLibrary", true)
        return saved
    }
}