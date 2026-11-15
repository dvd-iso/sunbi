package com.dvdiso.sunflowerclocks

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dvdiso.sunflowerclocks.ui.AlarmActivity
import com.dvdiso.sunflowerclocks.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Repo.init(this)
        Notifications.createChannels(this)
        Repo.addListener { keys ->
            if ("tasks" in keys) AlarmScheduler.scheduleAll(this)
            SyncManager.pushSoon()
        }
        AlarmScheduler.scheduleAll(this)
    }
}

object Notifications {
    const val ALARM_CHANNEL = "alarms"
    const val SYNC_CHANNEL = "sync"
    const val ALARM_ID = 1001
    const val SYNC_ID = 1002

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ALARM_CHANNEL, "Alarmes da rotina", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null) // o som sai do RingService
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel(SYNC_CHANNEL, "Sincronização", NotificationManager.IMPORTANCE_MIN).apply {
            setShowBadge(false)
        })
    }

    fun sync(ctx: Context): Notification {
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(ctx, SYNC_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sunflower)
            .setContentTitle("Sunflower Clocks")
            .setContentText("Sincronizando com seus aparelhos na rede")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}

object AlarmScheduler {
    const val ACTION = "com.dvdiso.sunflowerclocks.ALARM"

    private fun pi(ctx: Context, id: String): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION)
            .setData(Uri.parse("sunflower://task/$id"))
            .putExtra("task", id)
        return PendingIntent.getBroadcast(ctx, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun next(time: String): Long? {
        val t = runCatching { LocalTime.parse(time) }.getOrNull() ?: return null
        val zone = ZoneId.systemDefault()
        var at = ZonedDateTime.of(LocalDate.now(zone), t, zone)
        if (!at.isAfter(ZonedDateTime.now(zone).plusSeconds(5))) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    fun scheduleAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val tasks = Repo.tasks()
        val ids = tasks.map { it.id }.toSet()
        for (old in Prefs.scheduledIds - ids) am.cancel(pi(ctx, old))
        if (!Prefs.alarmsEnabled) {
            for (id in ids) am.cancel(pi(ctx, id))
            Prefs.scheduledIds = emptySet()
            return
        }
        val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        for (task in tasks) {
            val at = next(task.time) ?: continue
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi(ctx, task.id))
            } catch (e: SecurityException) {
                Log.w("SunflowerAlarm", "sem permissão de alarme exato: ${e.message}")
            }
        }
        Prefs.scheduledIds = ids
    }

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("task") ?: return
        AlarmScheduler.scheduleAll(ctx) // agenda o de amanhã
        if (!Prefs.alarmsEnabled || Repo.done(today(), id)) return
        val i = Intent(ctx, RingService::class.java).setAction(RingService.START).putExtra("task", id)
        ContextCompat.startForegroundService(ctx, i)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AlarmScheduler.scheduleAll(ctx)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching { SyncManager.start(ctx) }
        }
    }
}

/** Toca o alarme. Sem adiar: só "Feito" ou "Parar". */
class RingService : Service() {
    companion object {
        const val START = "start"
        const val DONE = "done"
        const val STOP = "stop"
        const val MAX_MS = 5 * 60_000L

        /** Tarefa tocando agora (a tela de alarme observa). */
        val ringing = MutableStateFlow<Task?>(null)
    }

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finish() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> {
                val id = intent.getStringExtra("task")
                val task = Repo.tasks().firstOrNull { it.id == id } ?: Task(id ?: "?", "", "Alarme")
                start(task)
            }
            DONE -> {
                val id = intent.getStringExtra("task") ?: ringing.value?.id
                if (id != null) Repo.setDone(today(), id, true)
                finish()
            }
            else -> finish()
        }
        return START_NOT_STICKY
    }

    private fun action(act: String, id: String, code: Int): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, RingService::class.java).setAction(act).putExtra("task", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun start(task: Task) {
        ringing.value = task
        val full = PendingIntent.getActivity(this, 1, Intent(this, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, Notifications.ALARM_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sunflower)
            .setContentTitle("${task.time}  ${task.label}")
            .setContentText("Sunflower Clocks")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .setColor(0xFFF5B301.toInt())
            .addAction(0, "Feito ✓", action(DONE, task.id, 2))
            .addAction(0, "Parar", action(STOP, task.id, 3))
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Notifications.ALARM_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Notifications.ALARM_ID, n)
        }

        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            setDataSource(this@RingService, Uri.parse("android.resource://$packageName/${R.raw.girassol}"))
            isLooping = true
            setOnPreparedListener { it.start() }
            prepareAsync()
        }
        vibrate()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, MAX_MS)
        // Com a tela ligada a notificação aparece no topo; tenta abrir a tela cheia também.
        runCatching { startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 600, 400, 1600), 0))
    }

    @Suppress("DEPRECATION")
    private fun finish() {
        handler.removeCallbacks(timeout)
        runCatching { player?.stop() }
        player?.release()
        player = null
        getSystemService(Vibrator::class.java)?.cancel()
        ringing.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (ringing.value != null) finish()
        super.onDestroy()
    }
}
