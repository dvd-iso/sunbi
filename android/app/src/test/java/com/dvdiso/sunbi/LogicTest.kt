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
        for (x in listOf(d(2), d(1), day)) for (m in Repo.tasks().filter { it.med }) Repo.set("$x/done/${m.id}", true)
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
            for (t in Repo.tasks()) Repo.set("${d(i)}/done/${t.id}", t.time)
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
}
