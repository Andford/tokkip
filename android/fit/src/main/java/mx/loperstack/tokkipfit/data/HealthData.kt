// Health Connect: where Samsung Health, Fitbit and the Pixel Watch put what you
// did. 0.1 only reads (steps, distance, active calories, exercise, sleep, heart
// rate); writing workouts back comes with them (0.2). Each figure is read only
// if its permission was given, so a "no" to one never hides the others.

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
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
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

    val PERMISSIONS = setOf(STEPS, DISTANCE, KCAL, EXERCISE, SLEEP, HEART, RESTING)

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
        if (granted.none { it in PERMISSIONS }) return null
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
