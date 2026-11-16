package com.dvdiso.sunbi

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sono vindo do relógio ou da pulseira, pelo Health Connect (só leitura de sessões de sono).
 * A pessoa escolhe puxar no check-in; nada é lido sozinho.
 */
object Health {
    val PERMISSIONS = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))
    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    fun available(ctx: Context): Boolean =
        runCatching { HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE }.getOrDefault(false)

    suspend fun granted(ctx: Context): Boolean = runCatching {
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }.getOrDefault(false)

    fun hm(i: Instant, zone: ZoneId = ZoneId.systemDefault()): String = LocalTime.ofInstant(i, zone).format(HM)

    /** Janela da "noite que terminou em [day]": do meio-dia da véspera às 14h do dia. */
    fun window(day: String, zone: ZoneId = ZoneId.systemDefault()): Pair<Instant, Instant> {
        val d = LocalDate.parse(day)
        return d.minusDays(1).atTime(12, 0).atZone(zone).toInstant() to d.atTime(14, 0).atZone(zone).toInstant()
    }

    /** Entre várias sessões na janela, a mais longa é a noite. */
    fun pick(sessions: List<Pair<Instant, Instant>>): Pair<Instant, Instant>? =
        sessions.maxByOrNull { it.second.toEpochMilli() - it.first.toEpochMilli() }

    /** (deitei, acordei) em HH:mm da noite que terminou em [day], ou null se não há sessão. */
    suspend fun lastNight(ctx: Context, day: String): Pair<String, String>? = runCatching {
        val (from, to) = window(day)
        val client = HealthConnectClient.getOrCreate(ctx)
        val res = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(from, to)))
        val s = pick(res.records.map { it.startTime to it.endTime }) ?: return null
        hm(s.first) to hm(s.second)
    }.getOrNull()
}
