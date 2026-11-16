package com.dvdiso.sunbi

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
import com.dvdiso.sunbi.ui.AlarmActivity
import com.dvdiso.sunbi.ui.MainActivity
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
        Focus.restore()
        Sfx.init(this)
        Haptics.init(this)
        SunAI.init(this)
        Notifications.createChannels(this)
        Repo.addListener { keys ->
            // rotina, folga ou férias mudaram: reagenda os alarmes de hoje e amanhã
            if (keys.any { it == "tasks" || it == "tasks_off" || it == "vacation" || it.endsWith("/off") || it.startsWith("off/") }) {
                AlarmScheduler.scheduleAll(this)
            }
            SyncManager.pushSoon()
        }
        AlarmScheduler.scheduleAll(this)
    }
}

object Notifications {
    const val ALARM_CHANNEL = "alarms"
    const val SYNC_CHANNEL = "sync"
    const val FOCUS_CHANNEL = "focus2" // "focus" antigo não tinha som próprio
    const val FOCUS_ID = 1003
    const val ALARM_ID = 1001
    const val SYNC_ID = 1002

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ALARM_CHANNEL, "Alarmes da rotina", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null) // o som sai do RingService
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        nm.deleteNotificationChannel("focus")
        nm.createNotificationChannel(NotificationChannel(FOCUS_CHANNEL, "Fim do tempo de foco", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(
                Uri.parse("android.resource://${ctx.packageName}/${R.raw.focus_end}"),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
            )
        })
        nm.createNotificationChannel(NotificationChannel(SYNC_CHANNEL, "Sincronização", NotificationManager.IMPORTANCE_MIN).apply {
            setShowBadge(false)
        })
    }

    fun sync(ctx: Context): Notification {
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(ctx, SYNC_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sunbi)
            .setContentTitle("SunBi")
            .setContentText("Sincronizando com seus aparelhos na rede")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}

object AlarmScheduler {
    const val ACTION = "com.dvdiso.sunbi.ALARM"

    /** Um alarme por (dia, tarefa): hoje e amanhã podem usar rotinas diferentes. */
    private fun pi(ctx: Context, key: String): PendingIntent {
        val (day, id) = key.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION)
            .setData(Uri.parse("sunbi://task/$day/$id"))
            .putExtra("task", id)
            .putExtra("day", day)
        return PendingIntent.getBroadcast(ctx, key.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone).plusSeconds(5)
        val wanted = LinkedHashMap<String, Long>()
        if (Prefs.alarmsEnabled) {
            for (offset in 0L..1L) {
                val date = LocalDate.now(zone).plusDays(offset)
                val day = date.toString()
                for (task in Repo.tasksFor(day)) {
                    val t = runCatching { LocalTime.parse(task.time) }.getOrNull() ?: continue
                    val at = ZonedDateTime.of(date, t, zone)
                    if (at.isAfter(now)) wanted["$day|${task.id}"] = at.toInstant().toEpochMilli()
                }
            }
        }
        for (old in Prefs.scheduledIds - wanted.keys) am.cancel(pi(ctx, old))
        val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        for ((key, at) in wanted) {
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi(ctx, key))
            } catch (e: SecurityException) {
                Log.w("SunBiAlarm", "sem permissão de alarme exato: ${e.message}")
            }
        }
        Prefs.scheduledIds = wanted.keys
    }

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("task") ?: return
        val day = intent.getStringExtra("day") ?: today()
        AlarmScheduler.scheduleAll(ctx) // agenda o de amanhã
        // a rotina pode ter mudado (férias ligadas depois de agendar): só toca se a tarefa ainda vale
        if (!Prefs.alarmsEnabled || Repo.done(day, id) || Repo.tasksFor(day).none { it.id == id }) return
        val i = Intent(ctx, RingService::class.java).setAction(RingService.START).putExtra("task", id).putExtra("day", day)
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

/** Toca o alarme. Sem adiar: só "Concluído" ou "Parar". */
class RingService : Service() {
    companion object {
        const val START = "start"
        const val DONE = "done"
        const val STOP = "stop"
        const val MAX_MS = 5 * 60_000L
        /** O volume sobe de 15% a 100% nestes milissegundos: acorda sem susto. */
        const val RAMP_MS = 30_000L

        fun soundRes(): Int = if (Prefs.alarmSound == "girassol") R.raw.girassol else R.raw.amanhecer

        /** Tarefa tocando agora (a tela de alarme observa). */
        val ringing = MutableStateFlow<Task?>(null)
    }

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finish() }
    private var rampStart = 0L
    private val ramp = object : Runnable {
        override fun run() {
            val p = player ?: return
            val f = ((System.currentTimeMillis() - rampStart).toFloat() / RAMP_MS).coerceIn(0f, 1f)
            val v = 0.15f + 0.85f * f * f
            runCatching { p.setVolume(v, v) }
            if (f < 1f) handler.postDelayed(this, 500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> {
                val id = intent.getStringExtra("task")
                val task = (Repo.tasksFor(today()) + Repo.tasks()).firstOrNull { it.id == id } ?: Task(id ?: "?", "", "Alarme")
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
            .setSmallIcon(R.drawable.ic_stat_sunbi)
            .setContentTitle("${task.time}  ${task.label}")
            .setContentText(task.step.ifBlank { "SunBi" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .setColor(0xFFF5B301.toInt())
            .addAction(0, "Concluído ✓", action(DONE, task.id, 2))
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
            setDataSource(this@RingService, Uri.parse("android.resource://$packageName/${soundRes()}"))
            isLooping = true
            setVolume(0.15f, 0.15f)
            setOnPreparedListener {
                it.start()
                rampStart = System.currentTimeMillis()
                handler.post(ramp)
            }
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
        handler.removeCallbacks(ramp)
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

/**
 * Cronômetro de foco (TDAH): um bloco curto e visível para começar uma tarefa.
 * O fim é agendado no AlarmManager, então avisa mesmo com o app fechado.
 */
object Focus {
    const val ACTION = "com.dvdiso.sunbi.FOCUS_END"

    /** Momento do fim (ms) do bloco em andamento, ou null. Observável pela interface. */
    val end = MutableStateFlow<Long?>(null)

    fun restore() {
        end.value = Prefs.focusEnd.takeIf { it > System.currentTimeMillis() }
    }

    private fun pi(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 77, Intent(ctx, FocusReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun start(ctx: Context, taskId: String, minutes: Int) {
        val ms = minutes * 60_000L
        val at = System.currentTimeMillis() + ms
        Prefs.focusEnd = at
        Prefs.focusTotal = ms
        Prefs.focusTask = taskId
        end.value = at
        schedule(ctx, at)
        Sfx.play(Sfx.S.FOCUS_START)
    }

    fun extend(ctx: Context, minutes: Int) {
        val cur = end.value ?: return
        val at = cur + minutes * 60_000L
        Prefs.focusEnd = at
        Prefs.focusTotal = Prefs.focusTotal + minutes * 60_000L
        end.value = at
        schedule(ctx, at)
    }

    fun stop(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx))
        Prefs.focusEnd = 0
        end.value = null
    }

    private fun schedule(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (AlarmScheduler.canScheduleExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx))
        } catch (e: SecurityException) {
            Log.w("SunBiFocus", "sem permissão de alarme exato: ${e.message}")
        }
    }
}

class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val task = Repo.tasksFor(today()).firstOrNull { it.id == Prefs.focusTask }
        Prefs.focusEnd = 0
        Focus.end.value = null
        val open = PendingIntent.getActivity(ctx, 5, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val text = task?.let { "Como foi com \"${it.label}\"? Pode marcar como feito ou fazer mais um bloco." }
            ?: "Hora de uma pausa curta."
        val n = NotificationCompat.Builder(ctx, Notifications.FOCUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sunbi)
            .setContentTitle("Tempo de foco acabou")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setColor(0xFFF5B301.toInt())
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(Notifications.FOCUS_ID, n) }
    }
}
