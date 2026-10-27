// Nights from Health Connect — where the Fitbit app puts what your Pixel Watch
// measured (with its stages: deep, REM, awake). Read-only: Tokkip never writes
// there. Needs Health Connect (built into Android 14+) and the "sleep" permission.

package mx.loperstack.tokkip.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant

object HealthSleep {
    val PERMISSIONS = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

    fun available(context: Context) = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** The contract that shows Health Connect's own permission screen. */
    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun granted(context: Context): Boolean = runCatching {
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }.getOrDefault(false)

    /** The last `days` nights, as log entries (source "health"). */
    suspend fun nights(context: Context, days: Long = 21): List<SleepEntry> {
        val client = HealthConnectClient.getOrCreate(context)
        val res = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.after(Instant.now().minus(Duration.ofDays(days)))),
        )
        // Naps and the main night share a morning: keep the longest session per morning.
        return res.records
            .filter { Duration.between(it.startTime, it.endTime).toMinutes() in 60L..960L }
            .map { r ->
                fun minutesOf(type: Int) = r.stages.filter { it.stage == type }.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
                sleepEntry(r.startTime.toEpochMilli(), r.endTime.toEpochMilli(), source = "health").copy(
                    deepMin = minutesOf(SleepSessionRecord.STAGE_TYPE_DEEP),
                    remMin = minutesOf(SleepSessionRecord.STAGE_TYPE_REM),
                    awakeMin = minutesOf(SleepSessionRecord.STAGE_TYPE_AWAKE),
                )
            }
            .groupBy { it.night }
            .map { (_, list) -> list.maxBy { it.wake - it.bed } }
    }
}
