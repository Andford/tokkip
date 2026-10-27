// Tokkip's own sounds (tools/make_sounds.py, synthesised), played with a
// SoundPool so they start instantly, on the phone and on the watch alike.

package mx.loperstack.tokkip.character

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import mx.loperstack.tokkip.shared.R

object Sounds {
    /** The names the PC uses, mapped to the copied WAVs. */
    private val RAW = mapOf(
        "annoyed" to R.raw.annoyed, "approval" to R.raw.approval, "approve" to R.raw.approve,
        "blip" to R.raw.blip, "close" to R.raw.close, "error" to R.raw.error, "finish" to R.raw.finish,
        "greet" to R.raw.greet, "love" to R.raw.love, "open" to R.raw.open, "peek" to R.raw.peek,
        "pop" to R.raw.pop, "proud" to R.raw.proud, "question" to R.raw.question, "sleep" to R.raw.sleep,
        "tick" to R.raw.tick, "wink" to R.raw.wink, "work" to R.raw.work, "yawn" to R.raw.yawn,
        "dizzy" to R.raw.dizzy, "slap" to R.raw.slap, "gulp" to R.raw.gulp,
        "flute_sigh" to R.raw.flute_sigh, "breath_in" to R.raw.breath_in, "breath_out" to R.raw.breath_out,
        "lullaby" to R.raw.lullaby, "page" to R.raw.page, "spark" to R.raw.spark,
        "focus_start" to R.raw.focus_start, "focus_break" to R.raw.focus_break, "task_done" to R.raw.task_done,
        "coin" to R.raw.coin, "cash" to R.raw.cash, "gb_on" to R.raw.gb_on, "gb_level" to R.raw.gb_level, "gb_warn" to R.raw.gb_warn,
        "level_up" to R.raw.level_up,
        // The notification tones, to hear them in Ajustes.
        "notif_morning" to R.raw.notif_morning, "notif_night" to R.raw.notif_night, "notif_event" to R.raw.notif_event,
        "notif_task" to R.raw.notif_task, "notif_streak" to R.raw.notif_streak, "notif_money" to R.raw.notif_money,
        "notif_screen" to R.raw.notif_screen, "notif_focus" to R.raw.notif_focus,
    )

    private var pool: SoundPool? = null
    private val loaded = mutableMapOf<String, Int>()
    private val ready = mutableSetOf<Int>()
    private val pending = mutableSetOf<Int>()

    /** On by default; the app can turn Tokkip's sounds off. */
    @Volatile var enabled = true

    fun init(context: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        p.setOnLoadCompleteListener { pool, id, status ->
            if (status == 0) {
                ready += id
                if (pending.remove(id)) pool.play(id, 0.8f, 0.8f, 1, 0, 1f)
            }
        }
        pool = p
        val app = context.applicationContext
        for ((name, res) in RAW) loaded[name] = p.load(app, res, 1)
    }

    fun play(name: String, volume: Float = 0.8f) {
        if (!enabled) return
        val p = pool ?: return
        val id = loaded[name] ?: return
        if (id in ready) p.play(id, volume, volume, 1, 0, 1f) else pending += id
    }
}
