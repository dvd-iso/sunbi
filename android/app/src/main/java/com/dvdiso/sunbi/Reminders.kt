package com.dvdiso.sunbi

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dvdiso.sunbi.ui.MainActivity
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Dois lembretes gentis, cada um desligável:
 * - check-in às 21h, só se o dia ainda não tem registro;
 * - resumo da semana pela SunAI no domingo às 19h (precisa da SunAI ligada).
 */
object Reminders {
    const val CHECKIN = "com.dvdiso.sunbi.REMIND_CHECKIN"
    const val WEEKLY = "com.dvdiso.sunbi.WEEKLY_SUMMARY"
    val CHECKIN_AT: LocalTime = LocalTime.of(21, 0)
    val WEEKLY_AT: LocalTime = LocalTime.of(19, 0)

    /** Próxima ocorrência diária de [at] depois de [now]. */
    fun nextDaily(now: LocalDateTime, at: LocalTime): LocalDateTime {
        val today = now.toLocalDate().atTime(at)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** Próximo domingo às [at] depois de [now]. */
    fun nextWeekly(now: LocalDateTime, at: LocalTime): LocalDateTime {
        val sunday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(at)
        return if (sunday.isAfter(now)) sunday else sunday.plusWeeks(1)
    }

    private fun pi(ctx: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(ctx, action.hashCode(), Intent(ctx, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        fun set(action: String, on: Boolean, at: LocalDateTime) {
            val p = pi(ctx, action)
            if (!on) { am.cancel(p); return }
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(zone).toInstant().toEpochMilli(), p) }
        }
        set(CHECKIN, Prefs.checkinReminder, nextDaily(now, CHECKIN_AT))
        set(WEEKLY, Prefs.weeklySummary && SunAI.ready(), nextWeekly(now, WEEKLY_AT))
    }

    fun notifyCheckin(ctx: Context) {
        if (Repo.checkedIn(today())) return
        val open = PendingIntent.getActivity(ctx, 9, Intent(ctx, MainActivity::class.java).putExtra("checkin", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, Notifications.REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sunbi)
            .setContentTitle("Como foi o seu dia?")
            .setContentText("Humor e sono em menos de um minuto. Se hoje não der, tudo bem.")
            .setContentIntent(open).setAutoCancel(true).setColor(0xFFF5B301.toInt())
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(Notifications.CHECKIN_ID, n) }
    }

    fun enqueueWeekly(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("weekly-summary", ExistingWorkPolicy.REPLACE, req)
    }

    const val WEEKLY_PROMPT = "Faça o resumo da minha semana para eu ler numa notificação: no máximo 90 palavras, " +
        "sem título, começando pelo que foi bem. Se houver sinal de subida ou descida, diga em uma frase e lembre o meu plano."
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Reminders.CHECKIN -> Reminders.notifyCheckin(ctx)
            Reminders.WEEKLY -> if (Prefs.weeklySummary && SunAI.ready()) Reminders.enqueueWeekly(ctx)
        }
        Reminders.schedule(ctx) // o próximo
    }
}

/** Pede o resumo à SunAI, guarda na conversa e avisa por notificação. */
class WeeklySummaryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!SunAI.ready()) return Result.success()
        val history = SunAI.load()
        val reply = runCatching { SunAI.ask(history, Reminders.WEEKLY_PROMPT) }.getOrElse {
            return if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
        SunAI.save(history + SunAI.Msg("user", "Resumo da semana") + reply)
        val open = PendingIntent.getActivity(applicationContext, 10, Intent(applicationContext, MainActivity::class.java).putExtra("tab", 2),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(applicationContext, Notifications.REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sunbi)
            .setContentTitle("Sua semana, pela SunAI")
            .setContentText(reply.text.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reply.text))
            .setContentIntent(open).setAutoCancel(true).setColor(0xFFF5B301.toInt())
            .build()
        runCatching { applicationContext.getSystemService(NotificationManager::class.java).notify(Notifications.WEEKLY_ID, n) }
        return Result.success()
    }
}
