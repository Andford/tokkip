// Health Connect: where Samsung Health, Fitbit and the Pixel Watch put what you
// did. Fit reads steps, distance, active calories, exercise, sleep and heart
// rate, and writes its own workouts back (a session and its calories), so they
// show up in Samsung Health and Fitbit too. Each figure is read only if its
// permission was given, so a "no" to one never hides the others.

package mx.loperstack.tokkipfit.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import mx.loperstack.tokkip.fit.Done
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

object HealthData {
    private val STEPS = HealthPermission.getReadPermission(StepsRecord::class)
    private val DISTANCE = HealthPermission.getReadPermission(DistanceRecord::class)
    private val KCAL = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    private val EXERCISE = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    private val SLEEP = HealthPermission.getReadPermission(SleepSessionRecord::class)
    private val HEART = HealthPermission.getReadPermission(HeartRateRecord::class)
    private val RESTING = HealthPermission.getReadPermission(RestingHeartRateRecord::class)

    private val WRITE_EXERCISE = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    private val WRITE_KCAL = HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)
    private val WRITE_DISTANCE = HealthPermission.getWritePermission(DistanceRecord::class)
    /** Respira as a mindfulness session (where this phone's Health Connect has them). */
    private val WRITE_MINDFUL = HealthPermission.getWritePermission(MindfulnessSessionRecord::class)
    private const val WRITE_ROUTE = HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE
    /** So the widgets can read Hoy while Fit isn't open (optional). */
    private const val BACKGROUND = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    private val BASE = setOf(STEPS, DISTANCE, KCAL, EXERCISE, SLEEP, HEART, RESTING, WRITE_EXERCISE, WRITE_KCAL, WRITE_DISTANCE, WRITE_ROUTE, BACKGROUND)
    val PERMISSIONS = BASE + WRITE_MINDFUL

    /** What to ask for: mindfulness only where Health Connect has it. */
    fun permissions(context: Context): Set<String> = if (mindfulness(context)) PERMISSIONS else BASE

    /** Whether this phone's Health Connect keeps mindfulness sessions (Android 16 and newer Health Connect). */
    fun mindfulness(context: Context): Boolean = runCatching {
        status(context) == Status.AVAILABLE &&
            HealthConnectClient.getOrCreate(context).features.getFeatureStatus(HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    /**
     * Saves a workout done in Fit (phone or watch) as an exercise session and its active
     * calories. False if Health Connect isn't there or that permission wasn't given.
     */
    suspend fun write(context: Context, d: Done, route: List<mx.loperstack.tokkip.fit.Point> = emptyList()): Boolean {
        if (status(context) != Status.AVAILABLE) return false
        val granted = granted(context)
        val client = HealthConnectClient.getOrCreate(context)
        val end = Instant.ofEpochMilli(d.at)
        val start = end.minusSeconds(d.seconds.toLong().coerceAtLeast(1))
        val zone = java.time.ZoneId.systemDefault().rules.getOffset(end)
        val device = Device(
            type = if (d.from == "watch") Device.TYPE_WATCH else Device.TYPE_PHONE,
            manufacturer = if (d.from == "watch") null else android.os.Build.MANUFACTURER,
            model = if (d.from == "watch") null else android.os.Build.MODEL,
        )
        // Respira: a mindfulness session where there are; a guided-breathing workout otherwise.
        if (d.kind == "breathe" && WRITE_MINDFUL in granted && mindfulness(context)) {
            val r = MindfulnessSessionRecord(
                startTime = start, startZoneOffset = zone, endTime = end, endZoneOffset = zone,
                metadata = Metadata.activelyRecorded(device),
                mindfulnessSessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_BREATHING,
                title = "${d.name} · Tokkip Fit",
            )
            if (runCatching { client.insertRecords(listOf(r)) }.isSuccess) return true
        }
        if (WRITE_EXERCISE !in granted) return false
        val type = when (d.kind) {
            "stretch" -> ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING
            "strength" -> ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS
            "gym" -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
            "run" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            "walk" -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
            "bike" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            "breathe" -> ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING
            else -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
        }
        val path = if (route.size >= 2 && WRITE_ROUTE in granted) ExerciseRoute(
            route.filter { it.at in start.toEpochMilli()..end.toEpochMilli() }.map {
                ExerciseRoute.Location(
                    time = Instant.ofEpochMilli(it.at), latitude = it.lat, longitude = it.lon,
                    horizontalAccuracy = Length.meters(it.accuracy.toDouble()), altitude = it.alt?.let { a -> Length.meters(a) },
                )
            },
        ) else null
        val records = buildList<Record> {
            add(
                ExerciseSessionRecord(
                    startTime = start, startZoneOffset = zone, endTime = end, endZoneOffset = zone,
                    metadata = Metadata.activelyRecorded(device),
                    exerciseType = type, title = "${d.name} · Tokkip Fit",
                    exerciseRoute = path,
                ),
            )
            if (d.meters > 0 && WRITE_DISTANCE in granted) add(
                DistanceRecord(
                    startTime = start, startZoneOffset = zone, endTime = end, endZoneOffset = zone,
                    distance = Length.meters(d.meters), metadata = Metadata.activelyRecorded(device),
                ),
            )
            if (d.kcal > 0.5 && WRITE_KCAL in granted) add(
                ActiveCaloriesBurnedRecord(
                    startTime = start, startZoneOffset = zone, endTime = end, endZoneOffset = zone,
                    energy = Energy.kilocalories(d.kcal), metadata = Metadata.activelyRecorded(device),
                ),
            )
        }
        return runCatching { client.insertRecords(records) }.isSuccess
    }

    enum class Status { AVAILABLE, NEEDS_UPDATE, MISSING }

    fun status(context: Context): Status = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Status.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.NEEDS_UPDATE
        else -> Status.MISSING
    }

    /** Health Connect's own permission screen. */
    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun granted(context: Context): Set<String> = runCatching {
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
    }.getOrDefault(emptySet())

    /** The last seven days (today last), last night and the resting heart rate. */
    suspend fun today(context: Context, goals: Goals): Today? {
        if (status(context) != Status.AVAILABLE) return null
        val granted = granted(context)
        if (granted.none { it in PERMISSIONS && it.contains("READ") }) return null
        val client = HealthConnectClient.getOrCreate(context)
        val start = LocalDate.now().minusDays(6)
        val metrics = buildSet<AggregateMetric<*>> {
            if (STEPS in granted) add(StepsRecord.COUNT_TOTAL)
            if (DISTANCE in granted) add(DistanceRecord.DISTANCE_TOTAL)
            if (KCAL in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (EXERCISE in granted) add(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
        }
        val byDay = if (metrics.isEmpty()) emptyMap() else runCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = metrics,
                    timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), LocalDateTime.now()),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            ).associate { g ->
                val r = g.result
                g.startTime.toLocalDate() to Day(
                    date = g.startTime.toLocalDate(),
                    steps = r[StepsRecord.COUNT_TOTAL] ?: 0,
                    meters = r[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0,
                    kcal = r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0,
                    exerciseMin = r[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes() ?: 0,
                )
            }
        }.getOrDefault(emptyMap())
        val week = (0L..6L).map { start.plusDays(it) }.map { byDay[it] ?: Day(it) }
        val sleep = if (SLEEP in granted) lastNight(client) else null
        val resting = if (RESTING in granted) restingBpm(client) else null
        return Today(week, Body(sleep, resting), goals, sample = false)
    }

    /** Minutes asleep in the longest session that ended in the last 18 hours. */
    private suspend fun lastNight(client: HealthConnectClient): Long? = runCatching {
        val since = Instant.now().minus(Duration.ofHours(30))
        client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.after(since))).records
            .filter { Duration.between(it.endTime, Instant.now()).toHours() < 18 }
            .maxOfOrNull { s ->
                val awake = s.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE }
                    .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                Duration.between(s.startTime, s.endTime).toMinutes() - awake
            }
    }.getOrNull()

    private suspend fun restingBpm(client: HealthConnectClient): Long? = runCatching {
        val since = Instant.now().minus(Duration.ofDays(7))
        client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.after(since))).records
            .maxByOrNull { it.time }?.beatsPerMinute
    }.getOrNull()
}
