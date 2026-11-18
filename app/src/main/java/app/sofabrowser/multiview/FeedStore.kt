package app.sofabrowser.multiview

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Multiview feeds (up to [Feed.MAX]) and which one has the sound. The feed list holds camera logins, so it is kept
 * encrypted with an AES-GCM key that lives in the Android Keystore and never leaves it (as the phone-pairing token is).
 * Some TVs' keystores fail; the list then stays in SFM's private storage, unencrypted, as the token does.
 */
class FeedStore(context: Context) {
    private val prefs = context.getSharedPreferences("multiview", Context.MODE_PRIVATE)

    fun load(): List<Feed?> {
        val json = prefs.getString("plain", null) ?: runCatching { prefs.getString("feeds", null)?.let(::decrypt) }.getOrNull()
        val out = MutableList<Feed?>(Feed.MAX) { null }
        if (json != null) runCatching {
            val arr = JSONArray(json)
            for (i in 0 until minOf(arr.length(), Feed.MAX)) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = runCatching { Feed.Kind.valueOf(o.getString("kind")) }.getOrNull() ?: continue
                val url = o.getString("url")
                // Stored feeds are checked again, so an edited file can't bring in a scheme the parser refuses.
                val parsed = Feed.parse(url) as? Feed.Parsed.Ok ?: continue
                // A camera keeps its kind without its login (which is stored apart), as long as it is https.
                if (kind == Feed.Kind.CAMERA) { if (!parsed.feed.url.startsWith("https://")) continue }
                else if (parsed.feed.kind != kind && !(kind == Feed.Kind.STREAM && parsed.feed.kind == Feed.Kind.WEB && o.has("frame"))) continue
                // A browser video's page must be http(s) too; anything else and the headers are left off.
                val frame = o.optString("frame").ifEmpty { null }?.takeIf { kind == Feed.Kind.STREAM && NavigationLock.siteOf(it) != null }
                out[i] = Feed(kind, parsed.feed.url, o.optString("user").ifEmpty { null },
                    o.optString("password").ifEmpty { null }, o.optString("pin").ifEmpty { null },
                    frame = frame, media = frame != null && o.optBoolean("media"),
                    userAgent = o.optString("ua").ifEmpty { null }?.takeIf { frame != null && it.none { c -> c < ' ' } })
            }
        }
        return out
    }

    fun save(feeds: List<Feed?>) {
        val arr = JSONArray()
        feeds.take(Feed.MAX).forEach { f ->
            arr.put(if (f == null) JSONObject.NULL else JSONObject().apply {
                put("kind", f.kind.name)
                put("url", f.url)
                f.user?.let { put("user", it) }
                f.password?.let { put("password", it) }
                f.pin?.let { put("pin", it) }
                f.frame?.let { put("frame", it) }
                if (f.media) put("media", true)
                f.userAgent?.let { put("ua", it) }
            })
        }
        val json = arr.toString()
        val enc = runCatching { encrypt(json) }.getOrNull()
        prefs.edit().putString("feeds", enc).putString("plain", if (enc == null) json else null).apply()
    }

    /** Web feeds that don't start, or show a player error, reload by themselves (on by default). */
    var autoReload: Boolean
        get() = prefs.getBoolean("auto_reload", true)
        set(v) = prefs.edit().putBoolean("auto_reload", v).apply()

    /** Which tile (0–3) has the sound; -1 when all are muted. */
    var audio: Int
        get() = prefs.getInt("audio", 0)
        set(v) = prefs.edit().putInt("audio", v).apply()

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(blob64: String): String {
        val blob = Base64.decode(blob64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
        return String(cipher.doFinal(blob, 12, blob.size - 12))
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    companion object {
        private const val ALIAS = "sfm_multiview_feeds"
        private const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
