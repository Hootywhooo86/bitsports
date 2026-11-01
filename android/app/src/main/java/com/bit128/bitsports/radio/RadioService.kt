package com.bit128.bitsports.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.bit128.bitsports.MainActivity
import com.bit128.bitsports.R
import com.bit128.bitsports.alerts.AlertLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live radio inside bitsports: the station's own stream (the same one its website's play button
 * uses), played by Android with the screen off, from the lock screen and through Bluetooth. A
 * dropped connection reconnects on its own; a phone call or another app's audio pauses it, and
 * unplugging headphones stops it.
 */
class RadioService : Service() {
    enum class Status { IDLE, CONNECTING, PLAYING, PAUSED, FAILED }
    data class State(val status: Status = Status.IDLE, val station: String? = null, val url: String? = null, val team: String? = null)

    companion object {
        private const val ACTION_PLAY = "com.bit128.bitsports.radio.PLAY"
        private const val ACTION_STOP = "com.bit128.bitsports.radio.STOP"
        private const val ACTION_TOGGLE = "com.bit128.bitsports.radio.TOGGLE"
        private const val CHANNEL = "radio"
        private const val NOTIFICATION_ID = 7_880
        private val RETRY_MS = longArrayOf(1_000, 3_000, 6_000, 12_000, 20_000)

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state

        fun playIntent(context: Context, station: String, url: String, team: String?): Intent =
            Intent(context, RadioService::class.java).setAction(ACTION_PLAY)
                .putExtra("station", station).putExtra("url", url).putExtra("team", team)

        fun play(context: Context, station: String, url: String, team: String?) {
            context.startForegroundService(playIntent(context, station, url, team))
        }

        fun stop(context: Context) {
            if (_state.value.status == Status.IDLE) return
            context.startService(Intent(context, RadioService::class.java).setAction(ACTION_STOP))
        }

        /** Pause while playing, play (live again) while paused or after a failure. */
        fun toggle(context: Context) {
            if (_state.value.status == Status.IDLE) return
            context.startService(Intent(context, RadioService::class.java).setAction(ACTION_TOGGLE))
        }

        /** For a notification button: plays from the shade without opening the app. */
        fun playPending(context: Context, station: String, url: String, team: String?, request: Int): PendingIntent =
            PendingIntent.getForegroundService(context, request, playIntent(context, station, url, team),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var session: MediaSession? = null
    private var wifi: WifiManager.WifiLock? = null
    private var focus: AudioFocusRequest? = null
    private var tries = 0
    private var noisyRegistered = false
    private val audio by lazy { getSystemService(AudioManager::class.java) }

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { stopAll() } // headphones out: don't blast the room
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra("url") ?: return stopAll().let { START_NOT_STICKY }
                val station = intent.getStringExtra("station") ?: "Radio"
                _state.value = State(Status.CONNECTING, station, url, intent.getStringExtra("team"))
                foreground()
                tries = 0
                start()
            }
            ACTION_TOGGLE -> when {
                _state.value.url == null -> stopAll() // the service was gone: nothing to play
                _state.value.status == Status.PLAYING -> pause()
                else -> resume()
            }
            ACTION_STOP -> stopAll()
            else -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        val url = _state.value.url ?: return
        release()
        if (!takeFocus()) { fail("Another app is using the audio"); return }
        session().isActive = true
        if (!noisyRegistered) {
            registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            noisyRegistered = true
        }
        wifi = wifi ?: getSystemService(WifiManager::class.java)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "bitsports:radio")?.apply { setReferenceCounted(false); acquire() }
        set(Status.CONNECTING)
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setWakeMode(this@RadioService, PowerManager.PARTIAL_WAKE_LOCK)
            setOnPreparedListener { it.start(); tries = 0; set(Status.PLAYING) }
            // A live stream never "completes": either way it means the connection dropped.
            setOnErrorListener { _, what, extra -> retry("error $what/$extra"); true }
            setOnCompletionListener { retry("stream ended") }
            try {
                setDataSource(url)
                prepareAsync()
            } catch (e: Exception) {
                retry(e.message ?: "couldn't open the stream")
            }
        }
    }

    private fun retry(why: String) {
        release()
        if (tries >= RETRY_MS.size) { fail("Lost the station ($why)"); return }
        set(Status.CONNECTING)
        main.postDelayed({ if (_state.value.status == Status.CONNECTING) start() }, RETRY_MS[tries++])
    }

    private fun pause() {
        release()
        set(Status.PAUSED)
    }

    /** Live radio picks up where the broadcast is now, not where you paused. */
    private fun resume() {
        if (_state.value.url == null) return
        tries = 0
        start()
    }

    private fun fail(why: String) {
        AlertLog(this).failed("Radio: $why", null)
        release()
        set(Status.FAILED)
    }

    private fun takeFocus(): Boolean {
        val req = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> stopAll()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (_state.value.status == Status.PLAYING) { pause(); pausedForCall = true }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.3f, 0.3f)
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        player?.setVolume(1f, 1f)
                        if (pausedForCall) { pausedForCall = false; resume() }
                    }
                }
            }
            .build().also { focus = it }
        return audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    private var pausedForCall = false

    private fun session(): MediaSession = session ?: MediaSession(this, "bitsports-radio").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() = resume()
            override fun onPause() = pause()
            override fun onStop() = stopAll()
        })
        session = this
    }

    private fun set(status: Status) {
        _state.value = _state.value.copy(status = status)
        session?.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
            .setState(when (status) {
                Status.PLAYING -> PlaybackState.STATE_PLAYING
                Status.CONNECTING -> PlaybackState.STATE_BUFFERING
                Status.PAUSED -> PlaybackState.STATE_PAUSED
                Status.FAILED -> PlaybackState.STATE_ERROR
                Status.IDLE -> PlaybackState.STATE_STOPPED
            }, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build())
        if (status != Status.IDLE) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun foreground() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Live radio", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val s = _state.value
        fun action(action: String, req: Int) = PendingIntent.getService(this, req,
            Intent(this, RadioService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        val playing = s.status == Status.PLAYING || s.status == Status.CONNECTING
        val toggle = Notification.Action.Builder(null, if (playing) "Pause" else "Play", action(ACTION_TOGGLE, 1)).build()
        val stop = Notification.Action.Builder(null, "Stop", action(ACTION_STOP, 2)).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_puck)
            .setContentTitle(s.station ?: "Radio")
            .setContentText(when (s.status) {
                Status.CONNECTING -> "Connecting…"
                Status.PLAYING -> "Live" + (s.team?.let { " · $it radio" } ?: "")
                Status.PAUSED -> "Paused · Play picks up live"
                Status.FAILED -> "Couldn't reach the station · tap Play to try again"
                Status.IDLE -> ""
            })
            .setContentIntent(open)
            .setOngoing(playing)
            .setDeleteIntent(action(ACTION_STOP, 3))
            .addAction(toggle)
            .addAction(stop)
            .setStyle(Notification.MediaStyle().setMediaSession(session().sessionToken).setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun release() {
        main.removeCallbacksAndMessages(null)
        player?.run { setOnErrorListener(null); setOnCompletionListener(null); try { reset() } catch (_: Exception) {}; release() }
        player = null
    }

    private fun stopAll() {
        release()
        focus?.let { audio.abandonAudioFocusRequest(it) }
        if (noisyRegistered) { unregisterReceiver(noisy); noisyRegistered = false }
        wifi?.release(); wifi = null
        session?.run { isActive = false; release() }; session = null
        _state.value = State()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (_state.value.status != Status.IDLE) stopAll()
        super.onDestroy()
    }
}
