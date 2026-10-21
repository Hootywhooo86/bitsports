package com.bit128.bitsports.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface AiResult {
    data class Ok(val text: String) : AiResult
    data class Unavailable(val reason: String) : AiResult
}

/**
 * Borrows bitcall's on-device model through its Family AI service (see bitcall README).
 * Only works when both apps are signed with the 128bit family key.
 */
object FamilyAi {
    private const val PACKAGE = "app.bitcall"
    private const val ACTION = "app.bit128.family.AI_GENERATE"
    private const val TIMEOUT_MS = 90_000L

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    suspend fun generate(context: Context, system: String, user: String, maxTokens: Int = 220, temperature: Float = 0.4f): AiResult {
        if (!installed(context)) return AiResult.Unavailable("not_installed")
        val app = context.applicationContext
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                var connection: ServiceConnection? = null
                fun finish(r: AiResult) {
                    connection?.let { runCatching { app.unbindService(it) } }
                    connection = null
                    if (cont.isActive) cont.resume(r)
                }
                val replies = Messenger(Handler(Looper.getMainLooper()) { msg ->
                    val b = msg.data
                    val text = b.getString("text")
                    finish(if (b.getBoolean("ok") && !text.isNullOrBlank()) AiResult.Ok(text.trim()) else AiResult.Unavailable(b.getString("error") ?: "failed"))
                    true
                })
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val request = Message.obtain(null, 1).apply {
                            data = Bundle().apply {
                                putString("system", system)
                                putString("user", user)
                                putInt("maxTokens", maxTokens)
                                putFloat("temperature", temperature)
                            }
                            replyTo = replies
                        }
                        runCatching { Messenger(binder).send(request) }.onFailure { finish(AiResult.Unavailable("failed")) }
                    }
                    override fun onServiceDisconnected(name: ComponentName?) = finish(AiResult.Unavailable("failed"))
                }
                val bound = runCatching {
                    app.bindService(Intent(ACTION).setPackage(PACKAGE), connection!!, Context.BIND_AUTO_CREATE)
                }.getOrDefault(false)
                if (!bound) finish(AiResult.Unavailable("not_installed"))
                cont.invokeOnCancellation { connection?.let { c -> runCatching { app.unbindService(c) } } }
            }
        } ?: AiResult.Unavailable("timeout")
    }

    /** One honest sentence for why there's no recap. */
    fun reasonText(reason: String): String = when (reason) {
        "not_installed" -> "Install bitcall to get AI recaps."
        "ai_off" -> "Turn on AI in bitcall to get recaps."
        "no_model" -> "Download a model in bitcall to get recaps."
        "in_call" -> "bitcall is on a call, so the recap was skipped."
        "not_family" -> "bitcall and bitsports need matching 128bit builds for recaps."
        "timeout" -> "bitcall took too long to write the recap."
        else -> "bitcall couldn't write a recap this time."
    }
}
