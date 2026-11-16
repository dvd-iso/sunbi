package com.dvdiso.sunbi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.dvdiso.sunbi.ui.MainActivity
import java.time.LocalTime

/**
 * Widget de tela inicial: a tarefa da vez com um botão Concluído, a sequência e a rotina do dia.
 * Atualiza a cada mudança nos registros, a cada alarme e a cada meia hora.
 */
class SunBiWidget : AppWidgetProvider() {
    companion object {
        const val DONE = "com.dvdiso.sunbi.WIDGET_DONE"

        data class Content(val title: String, val body: String, val taskId: String?, val footer: String)

        /** O que mostrar agora (mesma regra do cartão "Agora" do app). */
        fun content(day: String = today(), now: Int = LocalTime.now().toSecondOfDay() / 60): Content {
            val tasks = Repo.tasksFor(day)
            fun mins(t: String) = runCatching { LocalTime.parse(t).toSecondOfDay() / 60 }.getOrNull()
            val pending = tasks.filter { !Repo.done(day, it.id) && mins(it.time) != null }
            val late = pending.lastOrNull { val m = mins(it.time)!!; m <= now && now - m <= 180 }
            val next = pending.firstOrNull { mins(it.time)!! > now }
            val st = Game.stats(day)
            val done = tasks.count { Repo.done(day, it.id) }
            val footer = buildString {
                if (st.streak > 0) append("🔥 ${st.streak} ${if (st.streak == 1) "dia" else "dias"} · ")
                append(if (tasks.isEmpty()) "sem rotina ainda" else "$done de ${tasks.size} feitas")
                if (!Repo.checkedIn(day)) append(" · check-in pendente")
            }
            val t = late ?: next
            return when {
                tasks.isEmpty() -> Content("Monte a sua rotina", "Toque para abrir o SunBi", null, footer)
                t == null -> Content("Nada pendente agora", "Descansar também faz parte da rotina.", null, footer)
                late != null -> Content("Agora · era às ${t.time}", t.label + if (t.step.isNotBlank()) "\nComece por: ${t.step}" else "", t.id, footer)
                else -> Content("Próxima · ${t.time}", t.label, t.id, footer)
            }
        }

        fun update(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, SunBiWidget::class.java))
            if (ids.isEmpty()) return
            val c = content()
            val views = RemoteViews(ctx.packageName, R.layout.widget).apply {
                setTextViewText(R.id.w_title, c.title)
                setTextViewText(R.id.w_body, c.body)
                setTextViewText(R.id.w_footer, c.footer)
                setViewVisibility(R.id.w_done, if (c.taskId != null) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 20, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                if (c.taskId != null) {
                    // o dia vai junto: tocar depois da meia-noite num widget ainda não atualizado marca o dia mostrado
                    val i = Intent(ctx, SunBiWidget::class.java).setAction(DONE).putExtra("task", c.taskId).putExtra("day", today())
                    setOnClickPendingIntent(R.id.w_done, PendingIntent.getBroadcast(ctx, 21, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }
            for (id in ids) runCatching { mgr.updateAppWidget(id, views) }
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == DONE) {
            intent.getStringExtra("task")?.let { Repo.setDone(intent.getStringExtra("day") ?: today(), it, true) }
            Sfx.play(Sfx.S.POP)
            Haptics.play(Haptics.H.DONE)
            update(ctx)
        }
    }
}
