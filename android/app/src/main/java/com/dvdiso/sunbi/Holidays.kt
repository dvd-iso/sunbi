package com.dvdiso.sunbi

import java.time.LocalDate
import java.time.MonthDay

/**
 * Feriados nacionais do Brasil (e o Carnaval, ponto facultativo quase universal).
 * Os móveis saem da Páscoa. Usados para trocar sozinho para a rotina de folga.
 */
object Holidays {
    private val FIXED = mapOf(
        MonthDay.of(1, 1) to "Confraternização Universal",
        MonthDay.of(4, 21) to "Tiradentes",
        MonthDay.of(5, 1) to "Dia do Trabalho",
        MonthDay.of(9, 7) to "Independência",
        MonthDay.of(10, 12) to "Nossa Senhora Aparecida",
        MonthDay.of(11, 2) to "Finados",
        MonthDay.of(11, 15) to "Proclamação da República",
        MonthDay.of(11, 20) to "Consciência Negra",
        MonthDay.of(12, 25) to "Natal",
    )

    /** Domingo de Páscoa (algoritmo anônimo gregoriano). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    /** Nome do feriado nessa data, ou null. */
    fun name(date: LocalDate): String? {
        FIXED[MonthDay.from(date)]?.let { return it }
        val e = easter(date.year)
        return when (date) {
            e.minusDays(48), e.minusDays(47) -> "Carnaval"
            e.minusDays(2) -> "Paixão de Cristo"
            e.plusDays(60) -> "Corpus Christi"
            else -> null
        }
    }
}
