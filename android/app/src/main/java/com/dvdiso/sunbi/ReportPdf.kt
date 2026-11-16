package com.dvdiso.sunbi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Relatório para a consulta em PDF (A4), com a cara do SunBi: fundo creme, cabeçalho
 * amarelo com a flor, cartões de resumo, gráficos de humor e sono e a tabela dia a dia.
 */
object ReportPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 36f

    private val CREAM = Color.rgb(0xFF, 0xF8, 0xEE)
    private val BAND = Color.rgb(0xFF, 0xE6, 0x9C)
    private val CARD = Color.WHITE
    private val INK = Color.rgb(0x2E, 0x22, 0x10)
    private val SOFT = Color.rgb(0x7A, 0x6A, 0x50)
    private val LINE = Color.rgb(0xEA, 0xDF, 0xCB)
    private val AMBER = Color.rgb(0xE0, 0x8A, 0x00)
    private val GOLD = Color.rgb(0xB0, 0x7A, 0x00)
    private val BLUE = Color.rgb(0x3D, 0x6F, 0xD6)
    private val GREEN = Color.rgb(0x4A, 0x8F, 0x5A)
    private val RED = Color.rgb(0xC6, 0x3B, 0x3B)
    private val PETAL = Color.rgb(0xFF, 0xB3, 0x00)
    private val CORE = Color.rgb(0x6D, 0x43, 0x21)

    private val PT = Locale.forLanguageTag("pt-BR")
    private val DM = DateTimeFormatter.ofPattern("dd/MM", PT)
    private val DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT)
    private val WD = DateTimeFormatter.ofPattern("EEE", PT)

    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)

    private fun paint(color: Int, size: Float = 10f, face: Typeface = regular) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = face }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    /** Texto com quebra de linha dentro de [width]; devolve a altura usada. */
    private fun text(c: Canvas, s: String, x: Float, y: Float, width: Float, p: TextPaint, maxLines: Int = 20): Float {
        val l = StaticLayout.Builder.obtain(s, 0, s.length, p, width.toInt().coerceAtLeast(10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).setLineSpacing(1.5f, 1f).build()
        c.save()
        c.translate(x, y)
        l.draw(c)
        c.restore()
        return l.height.toFloat()
    }

    private fun flower(c: Canvas, cx: Float, cy: Float, r: Float) {
        val petal = fill(PETAL)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0 - 90)
            c.drawCircle(cx + (r * 0.62f * Math.cos(a)).toFloat(), cy + (r * 0.62f * Math.sin(a)).toFloat(), r * 0.38f, petal)
        }
        c.drawCircle(cx, cy, r * 0.5f, fill(CORE))
        val face = fill(CREAM)
        c.drawCircle(cx - r * 0.17f, cy - r * 0.07f, r * 0.07f, face)
        c.drawCircle(cx + r * 0.17f, cy - r * 0.07f, r * 0.07f, face)
        val smile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CREAM; style = Paint.Style.STROKE; strokeWidth = r * 0.07f; strokeCap = Paint.Cap.ROUND }
        c.drawArc(RectF(cx - r * 0.18f, cy - r * 0.05f, cx + r * 0.18f, cy + r * 0.22f), 20f, 140f, false, smile)
    }

    private fun card(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int = CARD) {
        c.drawRoundRect(RectF(x, y, x + w, y + h), 16f, 16f, fill(color))
    }

    /** Para onde as páginas vão: o PDF (no app) ou imagens (nos testes de visual). */
    interface PageSink {
        fun start(n: Int, w: Int, h: Int): Canvas
        fun end()
    }

    private class PdfSink(val doc: PdfDocument) : PageSink {
        private var page: PdfDocument.Page? = null
        override fun start(n: Int, w: Int, h: Int): Canvas {
            val p = doc.startPage(PdfDocument.PageInfo.Builder(w, h, n).create())
            page = p
            return p.canvas
        }
        override fun end() { page?.let { doc.finishPage(it) }; page = null }
    }

    private class Pager(val sink: PageSink) {
        var n = 0
        private var open = false
        lateinit var c: Canvas
        fun next(): Canvas {
            if (open) sink.end()
            n++
            c = sink.start(n, W, H)
            open = true
            c.drawColor(CREAM)
            return c
        }
        fun finish() { if (open) sink.end(); open = false }
    }

    private fun footer(c: Canvas, page: Int) {
        val p = paint(SOFT, 8f)
        c.drawText("Registrado pelo próprio paciente no app SunBi. Não é avaliação clínica.", M, H - 22f, p)
        val t = "página $page"
        c.drawText(t, W - M - p.measureText(t), H - 22f, p)
    }

    /** Gera o PDF dos últimos [n] dias até [end]. Devolve o arquivo em cache/reports. */
    fun build(ctx: Context, end: String, n: Int, recipient: String?): File {
        val doc = PdfDocument()
        draw(PdfSink(doc), end, n, recipient)
        val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "SunBi-relatorio-${end}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    /** Desenha todas as páginas do relatório em [sink]. */
    fun draw(sink: PageSink, end: String, n: Int, recipient: String?) {
        val days = lastDays(end, n)
        val pg = Pager(sink)
        var c = pg.next()

        // ── cabeçalho ──
        card(c, M, M, W - 2 * M, 104f, BAND)
        flower(c, M + 52f, M + 52f, 34f)
        c.drawText("Relatório SunBi", M + 104f, M + 44f, paint(INK, 24f, bold))
        val period = "${LocalDate.parse(days.first()).format(DM)} a ${LocalDate.parse(end).format(DMY)} · $n dias"
        val who = Prefs.reportName.takeIf { it.isNotBlank() }
        c.drawText(listOfNotNull(who, period).joinToString(" · "), M + 104f, M + 66f, paint(SOFT, 11f, medium))
        recipient?.let { c.drawText("Para: $it", M + 104f, M + 84f, paint(SOFT, 10f)) }

        // ── resumo ──
        var y = M + 124f
        val sl = days.mapNotNull { Repo.sleep(it) }
        val r = days.mapNotNull { rhythm(it) }
        val hum = days.mapNotNull { Repo.humor(it) }
        val stats = listOf(
            "${days.count { Repo.checkedIn(it) }}/$n" to "dias com registro",
            (if (sl.isEmpty()) "–" else String.format(PT, "%.1f h", sl.average())) to "sono médio",
            (medAdherence(days)?.let { "$it%" } ?: "–") to "remédios marcados",
            (if (r.isEmpty()) "–" else "${100 * r.sumOf { it.first } / r.sumOf { it.second }}%") to "âncoras no horário",
        )
        val sw = (W - 2 * M - 3 * 10f) / 4
        stats.forEachIndexed { i, (v, l) ->
            val x = M + i * (sw + 10f)
            card(c, x, y, sw, 64f)
            c.drawText(v, x + 14f, y + 30f, paint(GOLD, 20f, bold))
            c.drawText(l, x + 14f, y + 50f, paint(SOFT, 9f, medium))
        }
        y += 80f
        if (hum.isNotEmpty()) {
            val line = String.format(PT, "Humor (−3 a +3): mínimo %+d, máximo %+d, média %+.1f", hum.min(), hum.max(), hum.average())
            c.drawText(line, M + 4f, y, paint(INK, 10f, medium))
            y += 16f
        }
        wakeSpreadMinutes(days)?.let {
            c.drawText("Horário de acordar variou ±$it min", M + 4f, y, paint(INK, 10f, medium))
            y += 16f
        }

        // ── gráfico de humor ──
        y += 6f
        card(c, M, y, W - 2 * M, 190f)
        c.drawText("Humor", M + 16f, y + 24f, paint(INK, 13f, bold))
        c.drawText("acima: acelerado · abaixo: para baixo", M + 70f, y + 24f, paint(SOFT, 9f))
        moodChart(c, days, RectF(M + 16f, y + 36f, W - M - 16f, y + 162f))
        axis(c, days, M + 16f, W - M - 16f, y + 178f)
        y += 204f

        // ── gráfico de sono ──
        card(c, M, y, W - 2 * M, 160f)
        c.drawText("Sono", M + 16f, y + 24f, paint(INK, 13f, bold))
        c.drawText("linha tracejada: o sono habitual", M + 60f, y + 24f, paint(SOFT, 9f))
        sleepChart(c, days, RectF(M + 16f, y + 36f, W - M - 16f, y + 132f))
        axis(c, days, M + 16f, W - M - 16f, y + 148f)
        y += 174f

        // ── sinais e acontecimentos mais frequentes ──
        val signs = Repo.signs().associateBy { it.id }
        val events = EVENTS.toMap()
        val sCount = days.flatMap { Repo.signsOn(it) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            .mapNotNull { (id, k) -> signs[id]?.let { "${it.label} ($k)" } }
        val eCount = days.flatMap { Repo.eventsOn(it) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            .mapNotNull { (id, k) -> events[id]?.let { "$it ($k)" } }
        if (sCount.isNotEmpty() || eCount.isNotEmpty()) {
            // o cartão tem a altura do conteúdo (no máximo o que sobra na página)
            val tw = W - 2 * M - 32f
            val sText = if (sCount.isEmpty()) "" else "Sinais: " + sCount.joinToString(" · ")
            val eText = if (eCount.isEmpty()) "" else "Acontecimentos: " + eCount.joinToString(" · ")
            fun h(t: String) = if (t.isEmpty()) 0f else StaticLayout.Builder.obtain(t, 0, t.length, paint(INK, 9.5f), tw.toInt())
                .setMaxLines(3).setLineSpacing(1.5f, 1f).build().height + 4f
            val boxH = (34f + h(sText) + h(eText) + 12f).coerceAtMost((H - 50f) - y)
            if (boxH > 60f) {
                card(c, M, y, W - 2 * M, boxH)
                var ty = y + 22f
                c.drawText("Sinais e acontecimentos", M + 16f, ty, paint(INK, 13f, bold))
                ty += 8f
                if (sCount.isNotEmpty()) ty += text(c, "Sinais: " + sCount.joinToString(" · "), M + 16f, ty, W - 2 * M - 32f, paint(INK, 9.5f), 3) + 4f
                if (eCount.isNotEmpty()) text(c, "Acontecimentos: " + eCount.joinToString(" · "), M + 16f, ty, W - 2 * M - 32f, paint(INK, 9.5f), 3)
            }
        }
        footer(c, pg.n)

        // ── dia a dia (páginas seguintes) ──
        c = pg.next()
        y = M
        c.drawText("Dia a dia", M, y + 18f, paint(INK, 18f, bold))
        y += 34f
        val cols = floatArrayOf(58f, 44f, 70f, 46f, 42f, 50f) // data, humor, sono, energia, rotina, remédios
        val heads = listOf("Dia", "Humor", "Sono", "Energia", "Rotina", "Remédios")
        val restW = W - 2 * M - cols.sum() - 8f
        fun header(): Float {
            card(c, M, y, W - 2 * M, 24f, BAND)
            var x = M + 8f
            heads.forEachIndexed { i, h -> c.drawText(h, x, y + 16f, paint(INK, 9f, bold)); x += cols[i] }
            c.drawText("Sinais, acontecimentos e anotação", x, y + 16f, paint(INK, 9f, bold))
            return 28f
        }
        y += header()
        days.forEachIndexed { i, d ->
            val ld = LocalDate.parse(d)
            val extras = buildList {
                if (Repo.isOff(d)) add("folga")
                Repo.signsOn(d).mapNotNull { signs[it]?.label }.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
                Repo.eventsOn(d).mapNotNull { events[it] }.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
                Repo.note(d).takeIf { it.isNotBlank() }?.let { add("“${it.replace('\n', ' ')}”") }
            }.joinToString(" · ")
            val p = paint(INK, 8.5f)
            val extraH = if (extras.isEmpty()) 0f else StaticLayout.Builder.obtain(extras, 0, extras.length, p, restW.toInt())
                .setMaxLines(4).setEllipsize(android.text.TextUtils.TruncateAt.END).build().height.toFloat()
            val rowH = maxOf(24f, extraH + 10f)
            if (y + rowH > H - 50f) {
                footer(c, pg.n)
                c = pg.next()
                y = M
                y += header()
            }
            if (i % 2 == 0) c.drawRoundRect(RectF(M, y, W - M, y + rowH), 8f, 8f, fill(CARD))
            var x = M + 8f
            val base = y + 15f
            c.drawText("${ld.format(WD).take(3)} ${ld.format(DM)}", x, base, paint(INK, 9f, medium)); x += cols[0]
            val h = Repo.humor(d)
            c.drawText(h?.let { if (it > 0) "+$it" else "$it" } ?: "–", x, base,
                paint(when { h == null -> SOFT; h < 0 -> BLUE; h > 0 -> AMBER; else -> GREEN }, 9.5f, bold)); x += cols[1]
            val s = Repo.sleep(d)
            c.drawText(s?.let { String.format(PT, "%.1f h", it) } ?: "–", x, base, paint(if (s != null && s < 6) RED else INK, 9f)); x += cols[2]
            c.drawText(Repo.level(d, "energy")?.let { "$it/5" } ?: "–", x, base, paint(INK, 9f)); x += cols[3]
            c.drawText(if (Repo.hasDay(d)) "${Repo.percent(d)}%" else "–", x, base, paint(INK, 9f)); x += cols[4]
            val meds = Repo.tasksFor(d).filter { it.med }
            c.drawText(if (meds.isEmpty() || !Repo.hasDay(d)) "–" else "${meds.count { Repo.done(d, it.id) }}/${meds.size}", x, base, paint(INK, 9f)); x += cols[5]
            if (extras.isNotEmpty()) text(c, extras, x, y + 5f, restW, p, 4)
            y += rowH + 2f
        }
        footer(c, pg.n)
        pg.finish()
    }

    private fun moodChart(c: Canvas, days: List<String>, r: RectF) {
        val mid = r.centerY()
        val unit = (r.height() / 2 - 4f) / 3f
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LINE; strokeWidth = 1f; pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f) }
        for (k in 1..3) {
            c.drawLine(r.left, mid - k * unit, r.right, mid - k * unit, dash)
            c.drawLine(r.left, mid + k * unit, r.right, mid + k * unit, dash)
        }
        c.drawLine(r.left, mid, r.right, mid, Paint().apply { color = SOFT; strokeWidth = 1.5f })
        val slot = r.width() / days.size
        val bw = (slot * 0.6f).coerceAtMost(18f)
        days.forEachIndexed { i, d ->
            val cx = r.left + slot * i + slot / 2
            val v = Repo.humor(d)
            when {
                v == null -> c.drawCircle(cx, mid, 2.5f, fill(LINE))
                v == 0 -> c.drawRoundRect(RectF(cx - bw / 2, mid - 3f, cx + bw / 2, mid + 3f), 3f, 3f, fill(GREEN))
                else -> {
                    val hh = kotlin.math.abs(v) * unit
                    val rect = if (v > 0) RectF(cx - bw / 2, mid - hh, cx + bw / 2, mid) else RectF(cx - bw / 2, mid, cx + bw / 2, mid + hh)
                    c.drawRoundRect(rect, bw / 2.5f, bw / 2.5f, fill(if (v > 0) AMBER else BLUE))
                }
            }
        }
    }

    private fun sleepChart(c: Canvas, days: List<String>, r: RectF) {
        val maxH = 12.0
        fun y(h: Double) = r.bottom - (h.coerceIn(0.0, maxH) / maxH * r.height()).toFloat()
        val base = sleepBaseline(days.last())
        val slot = r.width() / days.size
        val bw = (slot * 0.6f).coerceAtMost(18f)
        days.forEachIndexed { i, d ->
            val cx = r.left + slot * i + slot / 2
            val v = Repo.sleep(d)
            if (v == null) c.drawCircle(cx, r.bottom - 3f, 2.5f, fill(LINE))
            else c.drawRoundRect(RectF(cx - bw / 2, y(v), cx + bw / 2, r.bottom), bw / 2.5f, bw / 2.5f, fill(if (v < 6.0) RED else GOLD))
        }
        c.drawLine(r.left, y(base), r.right, y(base), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GREEN; strokeWidth = 1.5f; pathEffect = DashPathEffect(floatArrayOf(6f, 5f), 0f)
        })
    }

    private fun axis(c: Canvas, days: List<String>, left: Float, right: Float, y: Float) {
        val slot = (right - left) / days.size
        val step = kotlin.math.ceil(days.size * 16f / (right - left)).toInt().coerceAtLeast(1)
        val p = paint(SOFT, 7.5f)
        days.forEachIndexed { i, d ->
            if ((days.lastIndex - i) % step != 0) return@forEachIndexed
            val t = LocalDate.parse(d).dayOfMonth.toString()
            c.drawText(t, left + slot * i + slot / 2 - p.measureText(t) / 2, y, p)
        }
    }
}
