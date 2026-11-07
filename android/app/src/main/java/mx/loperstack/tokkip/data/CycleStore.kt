// "Ciclo": the menstrual cycle and fertility tracker, an optional part of
// Tokkip (off until you turn it on in Ajustes). What you mark is health data,
// so it's kept apart from everything else: encrypted in its own file with a key
// in the Android Keystore, only on this phone. It doesn't go to the watch, the
// PC, Tokkip's backups or Google's (backup_rules.xml), and "Borrar" removes it.

package mx.loperstack.tokkip.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CycleData(
    val enabled: Boolean = false,
    /** Days with period, as epoch days. */
    val period: Set<Int> = emptySet(),
    val notes: Map<Int, CycleDay> = emptyMap(),
    val remindPeriod: Boolean = true,
    val remindFertile: Boolean = false,
)

object CycleStore {
    private const val FILE = "ciclo.bin"
    private const val KEY = "tokkip_ciclo"
    private const val PREFS = "ciclo"

    val SYMPTOMS get() = CycleMath.SYMPTOMS

    private val _state = MutableStateFlow(CycleData())
    val state: StateFlow<CycleData> = _state.asStateFlow()
    @Volatile private var loaded = false

    fun today(): Int = LocalDate.now().toEpochDay().toInt()

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val base = CycleData(
            enabled = prefs.getBoolean("activo", false),
            remindPeriod = prefs.getBoolean("avisoPeriodo", true),
            remindFertile = prefs.getBoolean("avisoFertil", false),
        )
        _state.value = read(app)?.let { (p, n) -> base.copy(period = p, notes = n) } ?: base
    }

    fun setEnabled(context: Context, on: Boolean) {
        init(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("activo", on).apply()
        _state.value = _state.value.copy(enabled = on)
        mx.loperstack.tokkip.wellness.CycleReminder.sync(context)
    }

    /** From the welcome: "Mujer" turns Ciclo on; anything else leaves it off (it's always in Ajustes). */
    fun setGender(context: Context, gender: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("genero", gender).apply()
        setEnabled(context, gender == "mujer")
    }

    fun gender(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("genero", "").orEmpty()

    fun setReminders(context: Context, period: Boolean, fertile: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("avisoPeriodo", period).putBoolean("avisoFertil", fertile).apply()
        _state.value = _state.value.copy(remindPeriod = period, remindFertile = fertile)
        mx.loperstack.tokkip.wellness.CycleReminder.sync(context)
    }

    /** Marks or unmarks one day as a period day. */
    fun toggle(context: Context, day: Int) {
        val d = _state.value
        save(context, d.copy(period = if (day in d.period) d.period - day else d.period + day))
    }

    /** "Empezó hoy": today and the usual length after it (you can unmark any day). */
    fun startPeriod(context: Context, day: Int, length: Int) {
        val d = _state.value
        save(context, d.copy(period = d.period + (day until day + length).toSet()))
    }

    /** "Terminó": the period's days after [day] are no longer marked. */
    fun endPeriod(context: Context, day: Int) {
        val d = _state.value
        val p = CycleMath.periods(d.period).lastOrNull { day in it.start..it.end + 1 } ?: return
        save(context, d.copy(period = d.period.filterNot { it in (day + 1)..p.end }.toSet()))
    }

    fun note(context: Context, day: Int, n: CycleDay) {
        val d = _state.value
        save(context, d.copy(notes = if (n == CycleDay()) d.notes - day else d.notes + (day to n)))
    }

    /** Everything marked goes away (the switch and reminders stay as they were). */
    fun forget(context: Context) {
        File(context.filesDir, FILE).delete()
        _state.value = _state.value.copy(period = emptySet(), notes = emptyMap())
        mx.loperstack.tokkip.wellness.CycleReminder.sync(context)
    }

    private fun save(context: Context, d: CycleData) {
        _state.value = d
        write(context.applicationContext, d)
        mx.loperstack.tokkip.wellness.CycleReminder.sync(context)
    }

    // ── The file ─────────────────────────────────────────────────────────────

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun read(context: Context): Pair<Set<Int>, Map<Int, CycleDay>>? = runCatching {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        val bytes = f.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val o = JSONObject(String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8))
        val days = o.optJSONArray("periodo") ?: JSONArray()
        val period = (0 until days.length()).map { days.getInt(it) }.toSet()
        val notesJson = o.optJSONObject("notas") ?: JSONObject()
        val notes = notesJson.keys().asSequence().mapNotNull { k ->
            val n = notesJson.optJSONObject(k) ?: return@mapNotNull null
            val s = n.optJSONArray("sintomas") ?: JSONArray()
            k.toIntOrNull()?.let {
                it to CycleDay(
                    flow = n.optInt("flujo"),
                    symptoms = (0 until s.length()).map { i -> s.getString(i) }.toSet(),
                    sex = n.optInt("sexo"),
                    ovulationTest = n.optInt("pruebaOvulacion"),
                    pregnancyTest = n.optInt("pruebaEmbarazo"),
                    mucus = n.optInt("moco"),
                    temp = n.optDouble("temperatura", 0.0).takeIf { t -> !t.isNaN() } ?: 0.0,
                    note = n.optString("nota"),
                )
            }
        }.toMap()
        period to notes
    }.getOrNull()

    private fun write(context: Context, d: CycleData) {
        runCatching {
            val notes = JSONObject()
            for ((day, n) in d.notes) notes.put(
                day.toString(),
                JSONObject().put("flujo", n.flow).put("sintomas", JSONArray(n.symptoms.toList()))
                    .put("sexo", n.sex).put("pruebaOvulacion", n.ovulationTest).put("pruebaEmbarazo", n.pregnancyTest)
                    .put("moco", n.mucus).put("temperatura", n.temp).put("nota", n.note),
            )
            val json = JSONObject().put("periodo", JSONArray(d.period.sorted())).put("notas", notes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val sealed = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeBytes(sealed)
            tmp.renameTo(File(context.filesDir, FILE))
        }
    }
}
