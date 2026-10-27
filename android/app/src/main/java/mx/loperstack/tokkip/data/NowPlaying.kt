// What's playing right now (Spotify, YouTube Music…), for the focus screen
// and its AOD mode: title, artist, cover, and play/pause/skip. Android only
// shares other apps' media sessions with an app you allow to read
// notifications — MediaListener is that permission; Tokkip reads nothing else.

package mx.loperstack.tokkip.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Only here so Android lets Tokkip see media sessions; it ignores the notifications. */
class MediaListener : NotificationListenerService()

object NowPlaying {
    data class Track(val title: String, val artist: String, val app: String, val playing: Boolean, val art: Bitmap?)

    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track.asStateFlow()

    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var appContext: Context? = null
    private var users = 0

    fun granted(context: Context) = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() { controller = null; publish() }
    }

    /** Starts listening (call from a screen; pair with stop()). */
    fun start(context: Context) {
        users++
        if (manager != null || !granted(context)) return
        val app = context.applicationContext
        appContext = app
        val msm = app.getSystemService(MediaSessionManager::class.java) ?: return
        val comp = ComponentName(app, MediaListener::class.java)
        runCatching {
            msm.addOnActiveSessionsChangedListener(sessions, comp)
            manager = msm
            pick(msm.getActiveSessions(comp))
        }
    }

    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessions) }
        controller?.unregisterCallback(callback)
        controller = null
        manager = null
    }

    private fun pick(list: List<MediaController>?) {
        val next = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = next
            next?.registerCallback(callback)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val m = c?.metadata
        if (c == null || m == null) { _track.value = null; return }
        val label = runCatching {
            val pm = appContext?.packageManager
            pm?.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0))?.toString()
        }.getOrNull() ?: c.packageName
        _track.value = Track(
            title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            app = label,
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART),
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    /** Opens where the music is playing: the app's own player (its session's screen), or the app. */
    fun openSource(context: Context) {
        val c = controller ?: return
        val opened = runCatching {
            val pi = c.sessionActivity ?: return@runCatching false
            val opts = android.app.ActivityOptions.makeBasic()
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                opts.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
            pi.send(context, 0, null, null, null, null, opts.toBundle())
            true
        }.getOrDefault(false)
        if (!opened) runCatching {
            context.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
                context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
}
