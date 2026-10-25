package com.bit128.bitsports.account

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The 128bit account: the same email + password as Bitcal and 128bit Play, on the shared Supabase
 * project ("128bit play"). Signing in gives a short-lived token that row-level security checks, and
 * a refresh token kept in this app's private storage so you only sign in once.
 */
class Account128(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bitsports.account", Context.MODE_PRIVATE)

    val email: String? get() = prefs.getString("email", null)
    val signedIn: Boolean get() = prefs.getString("refresh", null) != null

    class AuthError(message: String) : Exception(message)

    suspend fun signIn(email: String, password: String) {
        val o = post("$URL_BASE/auth/v1/token?grant_type=password", JSONObject().put("email", email.trim()).put("password", password), null)
        save(o)
    }

    fun signOut() = prefs.edit().clear().apply()

    /** A token that's good for at least another minute, refreshing it when needed. */
    suspend fun token(): String = lock.withLock {
        val access = prefs.getString("access", null)
        if (access != null && prefs.getLong("expires", 0) - System.currentTimeMillis() > 60_000) return@withLock access
        val refresh = prefs.getString("refresh", null) ?: throw AuthError("Sign in to your 128bit account")
        val o = try {
            post("$URL_BASE/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh), null)
        } catch (e: AuthError) {
            signOut() // the refresh token was revoked (password changed, signed out everywhere)
            throw e
        }
        save(o)
        o.getString("access_token")
    }

    private fun save(o: JSONObject) {
        prefs.edit()
            .putString("access", o.getString("access_token"))
            .putString("refresh", o.getString("refresh_token"))
            .putLong("expires", System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000)
            .putString("email", o.optJSONObject("user")?.optString("email")?.ifBlank { null } ?: email)
            .apply()
    }

    companion object {
        const val URL_BASE = "https://wvflpwtaszpstmjpkrhu.supabase.co"
        // The project's publishable key: safe to ship, row-level security does the protecting.
        const val KEY = "sb_publishable_dO8uP74cTV1tbuqESgxysw_323C7lWh"
        private val lock = Mutex()

        /** One JSON request to the 128bit project; [token] signs it as you. Errors carry Supabase's own message. */
        suspend fun request(method: String, url: String, body: String?, token: String?, extra: Map<String, String> = emptyMap()): String =
            withContext(Dispatchers.IO) {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = method
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 20_000
                    conn.setRequestProperty("apikey", KEY)
                    conn.setRequestProperty("Authorization", "Bearer ${token ?: KEY}")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Accept", "application/json")
                    extra.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                    if (body != null) {
                        conn.doOutput = true
                        conn.outputStream.use { it.write(body.toByteArray()) }
                    }
                    val code = conn.responseCode
                    val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code == 400 || code == 401 || code == 403) throw AuthError(message(text) ?: "Not allowed ($code)")
                    if (code !in 200..299) throw IOException(message(text) ?: "128bit account answered $code")
                    text
                } finally {
                    conn.disconnect()
                }
            }

        private suspend fun post(url: String, body: JSONObject, token: String?) = JSONObject(request("POST", url, body.toString(), token))

        /** Supabase errors come as {msg} (auth) or {message} (database); "Invalid login credentials" reads fine as is. */
        private fun message(text: String): String? = runCatching {
            JSONObject(text).let { it.optString("msg").ifBlank { it.optString("error_description") }.ifBlank { it.optString("message") }.ifBlank { null } }
        }.getOrNull()
    }
}
