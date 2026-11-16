package com.dvdiso.sunbi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogicTest {
    private val day = "2026-03-10"
    private fun d(back: Long) = LocalDate.parse(day).minusDays(back).toString()

    @Before
    fun clean() = Seed.clear()

    @Test fun sleepAcrossMidnight() {
        Repo.setSleepTimes(day, "23:30", "07:00")
        assertEquals(7.5, Repo.sleep(day)!!, 0.01)
        Repo.setSleepTimes(day, "01:15", "06:45")
        assertEquals(5.5, Repo.sleep(day)!!, 0.01)
    }

    @Test fun oldMoodScaleIsMapped() {
        Repo.set("$day/mood", 1)
        assertEquals(-2, Repo.humor(day))
        Repo.set("$day/mood", 5)
        assertEquals(2, Repo.humor(day))
        Repo.set("$day/humor", -3)
        assertEquals(-3, Repo.humor(day))
    }

    @Test fun doneAcceptsBoolAndTime() {
        Repo.set("$day/done/wake", true)
        assertTrue(Repo.done(day, "wake"))
        assertNull(Repo.doneAt(day, "wake"))
        Repo.set("$day/done/wake", "07:20")
        assertTrue(Repo.done(day, "wake"))
        assertEquals("07:20", Repo.doneAt(day, "wake"))
        Repo.set("$day/done/wake", false)
        assertFalse(Repo.done(day, "wake"))
    }

    @Test fun rhythmCountsAnchorsWithin45Minutes() {
        val anchors = Repo.tasks().filter { it.anchor }
        assertEquals(5, anchors.size)
        assertNull(rhythm(day))
        Repo.set("$day/done/wake", "07:40")   // alvo 07:00: +40 → conta
        Repo.set("$day/done/contact", "10:00") // alvo 08:30: +90 → não conta
        Repo.set("$day/done/bed", "23:10")     // alvo 22:30: +40 → conta
        assertEquals(2 to 5, rhythm(day))
    }

    @Test fun rhythmWrapsMidnight() {
        Repo.set("$day/done/bed", "00:05") // alvo 22:30 → 95 min depois
        assertEquals(0 to 5, rhythm(day))
        Repo.set("$day/done/bed", "23:10")
        assertEquals(1 to 5, rhythm(day))
    }

    @Test fun shortSleepWithEnergyFlagsUp() {
        for (i in 2L..12L) Repo.setSleepTimes(d(i), "23:00", "07:00") // 8 h habitual
        Repo.setSleepTimes(d(1), "02:00", "06:30")
        Repo.setSleepTimes(day, "02:30", "06:30")
        Repo.setLevel(day, "energy", 5)
        val kinds = insights(day).map { it.kind }
        assertTrue(kinds.toString(), InsightKind.UP in kinds)
        assertFalse(InsightKind.SLEEP in kinds) // já explicado pelo aviso de subida
    }

    @Test fun shortSleepAloneIsGentleSleepHint() {
        for (i in 2L..12L) Repo.setSleepTimes(d(i), "23:00", "07:00")
        for (x in listOf(d(2), d(1), day)) for (m in Repo.tasksFor(x).filter { it.med }) Repo.set("$x/done/${m.id}", true)
        Repo.setSleepTimes(d(1), "02:00", "06:30")
        Repo.setSleepTimes(day, "02:30", "06:30")
        val kinds = insights(day).map { it.kind }
        assertEquals(listOf(InsightKind.SLEEP), kinds.filter { it != InsightKind.KUDOS })
    }

    @Test fun persistentLowMoodFlagsDown() {
        Repo.set("${d(2)}/humor", -2)
        Repo.set("${d(1)}/humor", -1)
        Repo.set("$day/humor", -2)
        assertTrue(insights(day).any { it.kind == InsightKind.DOWN })
    }

    @Test fun twoPersonalSignsFlagTheirPole() {
        Repo.setStrSet("${d(1)}/signs", setOf("d_isolate"))
        Repo.setStrSet("$day/signs", setOf("d_interest"))
        val ins = insights(day)
        assertTrue(ins.any { it.kind == InsightKind.DOWN })
        assertFalse(ins.any { it.kind == InsightKind.UP })
    }

    @Test fun stableWeekHasNoWarnings() {
        for (i in 0L..6L) {
            Repo.set("${d(i)}/humor", 0)
            Repo.setSleepTimes(d(i), "23:00", "07:00")
            for (t in Repo.tasksFor(d(i))) Repo.set("${d(i)}/done/${t.id}", t.time)
        }
        val kinds = insights(day).map { it.kind }
        assertEquals(listOf(InsightKind.KUDOS), kinds)
    }

    @Test fun missedMedsIgnoreDaysWithoutAnyRecord() {
        // nenhum registro: o app pode não ter sido aberto, então não acusa doses
        assertEquals(0, missedMeds(listOf(d(2), d(1))))
        Repo.set("${d(1)}/humor", 0)
        assertEquals(2, missedMeds(listOf(d(2), d(1)))) // 2 remédios no dia com registro
    }

    @Test fun mergeIsLastWriterWins() {
        Repo.set("$day/humor", 1)
        val newer = JSONObject().put("$day/humor", JSONObject().put("v", -1).put("t", System.currentTimeMillis() + 10_000).put("d", "x"))
        val older = JSONObject().put("$day/humor", JSONObject().put("v", 3).put("t", 5).put("d", "y"))
        assertEquals(setOf("$day/humor"), Repo.merge(newer))
        assertEquals(emptySet<String>(), Repo.merge(older))
        assertEquals(-1, Repo.humor(day))
    }

    @Test fun reportMentionsKeyFacts() {
        Repo.set("$day/humor", 2)
        Repo.setSleepTimes(day, "01:00", "06:00")
        val r = report(day, 7)
        assertTrue(r, r.contains("humor +2"))
        assertTrue(r, r.contains("sono 5,0 h 01:00→06:00"))
        assertTrue(r, r.contains("Não é avaliação clínica"))
    }

    @Test fun signsAndPlanHaveDefaults() {
        assertTrue(Repo.signs().count { it.pole == "up" } >= 5)
        assertTrue(Repo.signs().count { it.pole == "down" } >= 5)
        assertNotNull(Repo.planList("up").firstOrNull())
        Repo.setPlanList("warn", listOf("Não consigo parar de andar"))
        assertEquals(listOf("Não consigo parar de andar"), Repo.planList("warn"))
    }

    // ── folga, feriados e férias ────────────────────────────────────────────

    @Test fun holidaysIncludeFixedAndEasterBased() {
        assertEquals(LocalDate.of(2026, 4, 5), Holidays.easter(2026))
        assertEquals("Paixão de Cristo", Holidays.name(LocalDate.of(2026, 4, 3)))
        assertEquals("Carnaval", Holidays.name(LocalDate.of(2026, 2, 17)))
        assertEquals("Proclamação da República", Holidays.name(LocalDate.of(2026, 11, 15)))
        assertNull(Holidays.name(LocalDate.of(2026, 11, 16)))
    }

    @Test fun weekendsAndHolidaysUseOffRoutine() {
        assertEquals(Repo.OffReason.WEEKEND, Repo.offReason("2026-03-07")) // sábado
        assertEquals(Repo.OffReason.HOLIDAY, Repo.offReason("2026-04-21")) // Tiradentes, terça
        assertNull(Repo.offReason("2026-03-10"))
        assertEquals(Repo.offTasks(), Repo.tasksFor("2026-03-07"))
        Repo.set("off/weekends", false)
        assertNull(Repo.offReason("2026-03-07"))
    }

    @Test fun vacationStartsTodayAndEndsKeepingHistory() {
        val t = today()
        val y = LocalDate.parse(t).minusDays(1).toString()
        Repo.set("vacation", JSONObject().put("since", y)) // começou ontem
        assertEquals(Repo.OffReason.VACATION, Repo.offReason(t))
        assertFalse(Repo.toggleOff(t)) // toque na flor: acaba
        assertNull(Repo.vacationSince())
        assertEquals(true, Repo.get("$y/off")) // ontem continua de folga no histórico
        assertFalse(Repo.isOff(t))
        assertTrue(Repo.toggleOff(t)) // liga de novo
        assertEquals(t, Repo.vacationSince())
    }

    @Test fun medAdherenceUsesEachDaysRoutine() {
        Repo.set("2026-03-07/humor", 0) // sábado: rotina de folga
        for (m in Repo.offTasks().filter { it.med }) Repo.set("2026-03-07/done/${m.id}", true)
        assertEquals(100, medAdherence(listOf("2026-03-07")))
    }

    // ── SunAI: regras das ferramentas ───────────────────────────────────────

    @Test fun sunAiCanAddAndUndo() {
        val s = SunAI.Session()
        val before = Repo.tasks().size
        val r = SunAI.exec(s, "add_task", JSONObject().put("routine", "semana").put("time", "10:00").put("label", "Alongar"))
        assertTrue(r, r.startsWith("ok"))
        assertEquals(before + 1, Repo.tasks().size)
        assertEquals(1, s.changes.size)
        SunAI.undo(SunAI.Msg("assistant", "", s.changes, s.undo))
        assertEquals(before, Repo.tasks().size)
    }

    @Test fun sunAiCannotTouchMedicationTimesOrRemoveThem() {
        val s = SunAI.Session()
        val med = Repo.tasks().first { it.med }
        val r1 = SunAI.exec(s, "remove_task", JSONObject().put("routine", "semana").put("id", med.id))
        assertTrue(r1, r1.startsWith("erro"))
        val r2 = SunAI.exec(s, "update_task", JSONObject().put("routine", "semana").put("id", med.id).put("time", "11:00"))
        assertTrue(r2, r2.startsWith("erro"))
        assertEquals(med.time, Repo.tasks().first { it.id == med.id }.time)
        val r3 = SunAI.exec(s, "add_task", JSONObject().put("routine", "folga").put("time", "25:00").put("label", "x"))
        assertTrue(r3, r3.startsWith("erro"))
        assertTrue(s.changes.isEmpty())
    }

    @Test fun sunAiContextHasRoutinesAndDays() {
        Repo.set("${today()}/humor", 1)
        val c = SunAI.context()
        assertTrue(c.contains("ROTINA DE SEMANA"))
        assertTrue(c.contains("ROTINA DE FOLGA"))
        assertTrue(c.contains("humor +1"))
    }

    // ── gamificação ─────────────────────────────────────────────────────────

    @Test fun xpLevelsAndStreakSkipOffDays() {
        assertEquals(1, Game.levelFor(0))
        assertEquals(1, Game.levelFor(99))
        assertEquals(2, Game.levelFor(100))
        assertEquals(3, Game.levelFor(300))
        assertEquals(300, Game.xpForLevel(3))
        // sex 06/03 e seg 09/03 com registro; sáb e dom de folga sem nada: sequência de 3 até terça
        Repo.set("2026-03-06/humor", 0)
        Repo.set("2026-03-09/humor", 0)
        Repo.set("2026-03-10/humor", 1)
        assertEquals(3, Game.streak("2026-03-10"))
        // quinta 05/03 sem nada (dia de semana) quebra
        Repo.set("2026-03-04/humor", 0)
        assertEquals(3, Game.streak("2026-03-10"))
        // hoje ainda sem nada não quebra: conta a partir de ontem
        assertEquals(3, Game.streak("2026-03-11"))
        val st = Game.stats("2026-03-10")
        assertEquals(3, st.streak)
        assertTrue(st.bestStreak >= 3)
        assertEquals(80, st.xp) // 4 check-ins × 20
        assertTrue("first_checkin" in st.badges)
        assertTrue("streak_3" in st.badges)
        assertFalse("streak_7" in st.badges)
    }

    @Test fun fullRoutineDayGivesBonusAndBadge() {
        for (t in Repo.tasksFor(day)) Repo.set("$day/done/${t.id}", t.time)
        Repo.set("$day/humor", 0)
        val n = Repo.tasksFor(day).size
        assertEquals(10 * n + 20 + 30 + 15, Game.dayXp(day)) // tudo feito, no horário
        val st = Game.stats(day)
        assertTrue("full_1" in st.badges)
        assertTrue("rhythm_1" in st.badges)
        assertTrue(st.goalAnchors)
    }

    // ── tour inicial ────────────────────────────────────────────────────────

    @Test fun routineBuilderFollowsAnswers() {
        val a = Answers(name = "Davi", wake = "06:30", bed = "23:00", weekendDelta = 60, meds = "both", medAm = "07:00", medPm = "22:30",
            dayKind = "study", start = "08:00", lunch = "12:00", dinner = "19:00", extras = setOf("contact", "dim"))
        val w = RoutineBuilder.weekdays(a)
        assertEquals(5, w.count { it.anchor })
        assertEquals("06:30", w.first { it.id == "wake" }.time)
        assertEquals("23:00", w.first { it.id == "bed" }.time)
        assertEquals("Começar os estudos", w.first { it.id == "start" }.label)
        assertEquals(2, w.count { it.med })
        assertEquals("22:00", w.first { it.id == "dim" }.time) // 1 h antes de deitar
        assertNull(w.firstOrNull { it.id == "water1" })
        assertEquals(w.map { it.time }.sorted(), w.map { it.time })
        val o = RoutineBuilder.offDays(a)
        assertEquals("07:30", o.first { it.id == "wake" }.time) // 1 h depois
        assertEquals("07:00", o.first { it.id == "medam" }.time) // remédio não muda
        assertEquals("00:00", o.first { it.id == "bed" }.time)
        RoutineBuilder.apply(a)
        assertEquals("Davi", Prefs.userName)
        assertEquals(w, Repo.tasks())
        assertTrue(RoutineBuilder.kickoff(a).contains("06:30"))
    }

    @Test fun freshInstallHasNoRoutineUntilTour() {
        Repo.set("tasks", null)
        Repo.set("tasks_off", null)
        assertTrue(Repo.tasks().isEmpty())
        assertFalse(Repo.hasRoutine())
        assertEquals(0, Repo.percent(day))
        assertNull(rhythm(day))
    }

    // ── modo complexo ───────────────────────────────────────────────────────

    @Test fun webConfigNeedsModeAndPin() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        Prefs.webConfig = false
        assertEquals(404, WebConfig.handle(ctx, "GET", "/", null, emptyMap(), null)!!.status)
        assertEquals(403, WebConfig.handle(ctx, "GET", "/api/config", "000000", emptyMap(), null)!!.status)
        assertNull(WebConfig.handle(ctx, "POST", "/v1/merge", null, emptyMap(), "{}")) // rota do protocolo
        WebConfig.enable(null, true)
        val pin = Prefs.webPin
        assertEquals(6, pin.length)
        val page = WebConfig.handle(ctx, "GET", "/", null, emptyMap(), null)!!
        assertEquals(200, page.status)
        assertTrue(page.body.contains("Modo complexo"))
        assertEquals(401, WebConfig.handle(ctx, "GET", "/api/config", "nope", emptyMap(), null)!!.status)
        val ok = WebConfig.handle(ctx, "GET", "/api/config", pin, emptyMap(), null)!!
        assertEquals(200, ok.status)
        val cfg = JSONObject(ok.body)
        assertEquals(Repo.tasks().size, cfg.getJSONArray("tasks").length())
        // cinco erros desligam o modo
        repeat(5) { WebConfig.handle(ctx, "GET", "/api/config", "nope", emptyMap(), null) }
        assertFalse(Prefs.webConfig)
    }

    @Test fun webConfigEditsRoutinePlanAndDay() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        WebConfig.enable(null, true)
        val pin = Prefs.webPin
        val body = JSONObject()
            .put("tasks", org.json.JSONArray().put(JSONObject().put("time", "08:00").put("label", "Acordar").put("anchor", true)))
            .put("plan", JSONObject().put("up", org.json.JSONArray(listOf("Dormir cedo"))))
            .put("contacts", org.json.JSONArray().put(JSONObject().put("name", "Ana").put("role", "Psicólogo(a)").put("phone", "11 9")))
            .put("prefs", JSONObject().put("userName", "Davi").put("alarmSound", "girassol"))
        val r = WebConfig.handle(ctx, "POST", "/api/config", pin, emptyMap(), body.toString())!!
        assertEquals(r.body, 200, r.status)
        assertEquals(listOf("Acordar"), Repo.tasks().map { it.label })
        assertEquals(listOf("Dormir cedo"), Repo.planList("up"))
        assertEquals("Ana", Repo.contacts().single().name)
        assertEquals("Davi", Prefs.userName)
        assertEquals("girassol", Prefs.alarmSound)
        // horário inválido é recusado sem mexer em nada
        val bad = WebConfig.handle(ctx, "POST", "/api/config", pin, emptyMap(),
            JSONObject().put("tasks", org.json.JSONArray().put(JSONObject().put("time", "25:00").put("label", "x"))).toString())!!
        assertEquals(400, bad.status)
        assertEquals(1, Repo.tasks().size)
        val d = WebConfig.handle(ctx, "POST", "/api/day", pin, emptyMap(),
            JSONObject().put("day", day).put("humor", 2).put("bed", "23:00").put("wake", "07:00").put("note", "ok").toString())!!
        assertEquals(200, d.status)
        assertEquals(2, Repo.humor(day))
        assertEquals(8.0, Repo.sleep(day)!!, 0.01)
        val days = WebConfig.handle(ctx, "GET", "/api/days", pin, mapOf("n" to "3"), null)!!
        assertEquals(3, org.json.JSONArray(days.body).length())
    }

    @Test fun launcherSearchIgnoresAccentsAndPrefersWordStarts() {
        val me = android.os.Process.myUserHandle()
        fun app(n: String) = Launcher.AppEntry(n, android.content.ComponentName("p.$n", "p.$n.Main"), me, 0L)
        val apps = listOf(app("Câmera"), app("Mensagens"), app("Google Maps"), app("Spotify"))
        assertEquals(listOf("Câmera"), Launcher.filter(apps, "cam").map { it.label })
        assertEquals(listOf("Google Maps"), Launcher.filter(apps, "ma").map { it.label })
        assertEquals(listOf("Mensagens"), Launcher.filter(apps, "ens").map { it.label }) // trecho, não começo
        assertEquals(4, Launcher.filter(apps, " ").size)
        Launcher.toggleFavorite(apps[0])
        Launcher.toggleFavorite(apps[1])
        assertEquals(listOf(apps[0].key, apps[1].key), Launcher.favorites())
        Launcher.setHidden(apps[0], true)
        assertEquals(listOf(apps[1].key), Launcher.favorites()) // escondido sai dos favoritos
        assertTrue(apps[0].key in Launcher.hidden())
        // renomear: mostra o nome novo, busca acha pelos dois, nome vazio volta ao original
        val r = Launcher.rename(apps[3], "Música")
        assertEquals("Música", r.label)
        assertEquals("Spotify", r.original)
        assertTrue(r.renamed)
        assertEquals(listOf("Música"), Launcher.filter(listOf(r), "spot").map { it.label })
        assertEquals(listOf("Música"), Launcher.filter(listOf(r), "mus").map { it.label })
        assertEquals("Música", Launcher.withName(apps[3]).label) // guardado
        assertEquals("Spotify", Launcher.rename(apps[3], " ").label)
        assertFalse(Launcher.withName(apps[3]).renamed)
    }

    // ── dia difícil, alarme num aparelho só, lembretes, widget, Health Connect ──

    @Test fun hardDayKeepsOnlyAnchorsAndMeds() {
        val all = Repo.tasksFor(day).size
        Repo.setHard(day, true)
        val t = Repo.tasksFor(day)
        assertTrue(t.size < all)
        assertTrue(t.all { it.anchor || it.med })
        assertEquals(0, Repo.percent(day))
        Repo.setHard(day, false)
        assertEquals(all, Repo.tasksFor(day).size)
    }

    @Test fun alarmDeviceChoosesWhoRings() {
        assertTrue(Repo.ringsHere())
        Repo.set("alarm_device", "quest-abc")
        assertFalse(Repo.ringsHere())
        Repo.set("alarm_device", Prefs.device)
        assertTrue(Repo.ringsHere())
        Repo.set("alarm_device", null)
        assertTrue(Repo.ringsHere())
    }

    @Test fun reminderTimesRollForward() {
        val t = java.time.LocalDateTime.of(2026, 3, 10, 21, 30) // terça, depois das 21h
        assertEquals(java.time.LocalDateTime.of(2026, 3, 11, 21, 0), Reminders.nextDaily(t, Reminders.CHECKIN_AT))
        assertEquals(java.time.LocalDateTime.of(2026, 3, 10, 21, 0), Reminders.nextDaily(t.minusHours(2), Reminders.CHECKIN_AT))
        assertEquals(java.time.LocalDateTime.of(2026, 3, 15, 19, 0), Reminders.nextWeekly(t, Reminders.WEEKLY_AT))
        val sun = java.time.LocalDateTime.of(2026, 3, 15, 20, 0)
        assertEquals(java.time.LocalDateTime.of(2026, 3, 22, 19, 0), Reminders.nextWeekly(sun, Reminders.WEEKLY_AT))
    }

    @Test fun widgetShowsLateThenNextTask() {
        val c1 = SunBiWidget.content(day, 7 * 60 + 10) // 07:10: levantar (07:00) atrasado, remédio ainda não
        assertEquals("wake", c1.taskId)
        assertTrue(c1.title, c1.title.startsWith("Agora"))
        Repo.set("$day/done/wake", "07:05")
        val c2 = SunBiWidget.content(day, 7 * 60 + 10)
        assertEquals("medam", c2.taskId)
        assertTrue(c2.title, c2.title.startsWith("Próxima"))
        assertTrue(c2.footer, c2.footer.contains("1 de"))
        Repo.set("tasks", null); Repo.set("tasks_off", null)
        assertNull(SunBiWidget.content(day, 600).taskId)
    }

    @Test fun healthPicksLongestSessionInWindow() {
        val zone = java.time.ZoneId.of("America/Sao_Paulo")
        val (from, to) = Health.window("2026-03-10", zone)
        assertEquals(java.time.LocalDateTime.of(2026, 3, 9, 12, 0).atZone(zone).toInstant(), from)
        assertEquals(java.time.LocalDateTime.of(2026, 3, 10, 14, 0).atZone(zone).toInstant(), to)
        val nap = java.time.LocalDateTime.of(2026, 3, 9, 14, 0).atZone(zone).toInstant() to java.time.LocalDateTime.of(2026, 3, 9, 14, 40).atZone(zone).toInstant()
        val night = java.time.LocalDateTime.of(2026, 3, 9, 23, 20).atZone(zone).toInstant() to java.time.LocalDateTime.of(2026, 3, 10, 6, 50).atZone(zone).toInstant()
        val p = Health.pick(listOf(nap, night))!!
        assertEquals("23:20", Health.hm(p.first, zone))
        assertEquals("06:50", Health.hm(p.second, zone))
        assertNull(Health.pick(emptyList()))
    }

    @Test fun whatsappNumberGetsCountryCode() {
        assertEquals("5511987654321", Share.waNumber("(11) 98765-4321"))
        assertEquals("5511987654321", Share.waNumber("+55 11 98765-4321"))
        assertNull(Share.waNumber("188"))
    }
}
