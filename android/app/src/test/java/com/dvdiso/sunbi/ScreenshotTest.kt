package com.dvdiso.sunbi

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dvdiso.sunbi.ui.Root
import com.dvdiso.sunbi.ui.SunBiTheme
import com.dvdiso.sunbi.ui.ThemeState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renderiza as telas de verdade no JVM e salva em app/build/shots/ para revisão visual.
 * Rodar: gradle :app:testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h2000dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Before
    fun data() {
        Prefs.dynamicColor = false
        ThemeState.load()
        Seed.clear()
        Seed.twoWeeks()
    }

    private fun save(name: String) {
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tabs(prefix: String) {
        rule.setContent { SunBiTheme { Root() } }
        save("$prefix-1-hoje")
        rule.onNodeWithText("Histórico").performClick()
        save("$prefix-2-historico")
        rule.onNodeWithText("Plano").performClick()
        save("$prefix-3-plano")
        rule.onNodeWithContentDescription("Ajustes").performClick()
        save("$prefix-4-ajustes")
    }

    @Test fun light() = tabs("claro")

    @Config(qualifiers = "+night")
    @Test fun dark() = tabs("escuro")

    @Config(qualifiers = "w960dp-h600dp-mdpi")
    @Test fun questWide() {
        rule.setContent { SunBiTheme { Root() } }
        save("quest-1-hoje")
    }

    /** Celular pequeno com fonte do sistema grande: onde o layout costuma quebrar. */
    @Config(qualifiers = "w320dp-h2400dp-xhdpi")
    @Test fun smallBigFont() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        tabs("pequeno")
        rule.onNodeWithText("Plano").performClick()
        rule.onNodeWithText("Preciso de ajuda agora").performClick()
        save("pequeno-5-ajuda")
    }

    /** Check-in em passos: começa vazio, escolhe o humor e chega aos detalhes. */
    @Test fun checkin() {
        Seed.clear()
        rule.setContent { SunBiTheme { Root() } }
        save("claro-6-hoje-vazio")
        rule.onNodeWithText("Registrar").performClick()
        save("claro-7-checkin-humor")
        rule.onNodeWithText("Um pouco acelerado").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Mais detalhes").fetchSemanticsNodes().isNotEmpty() }
        save("claro-8-checkin-sono")
        rule.onNodeWithText("Mais detalhes").performClick()
        save("claro-9-checkin-detalhes")
    }

    /** Modo férias (toque na flor), editor de rotina e a SunAI na primeira vez. */
    @Test fun newScreens() {
        rule.setContent { SunBiTheme { Root() } }
        rule.onNodeWithContentDescription("SunBi").performClick()
        save("claro-10-ferias")
        rule.onAllNodesWithText("Editar")[1].performClick() // o da rotina (o outro é do check-in)
        save("claro-11-rotina")
        rule.onNodeWithText("Folga e férias").performClick()
        save("claro-12-rotina-folga")
        rule.onNodeWithText("Levantar da cama").performClick()
        save("claro-13-rotina-tarefa")
    }

    @Test fun sunAiSetup() {
        rule.setContent { SunBiTheme { Root() } }
        rule.onNodeWithText("SunAI").performClick()
        save("claro-14-sunai")
    }

    @Config(qualifiers = "+night")
    @Test fun alarmDark() {
        rule.setContent {
            SunBiTheme { com.dvdiso.sunbi.ui.AlarmScreen("07:00", "Levantar da cama", "Abrir a cortina", {}, {}) }
        }
        save("escuro-15-alarme")
    }

    /** O PDF de verdade, para conferir o visual (convertido em PNG fora do teste). */
    @Test fun reportPdf() {
        Prefs.reportName = "Davi"
        // o Robolectric não tem PdfDocument: as mesmas páginas são desenhadas em imagens
        val pages = mutableListOf<Bitmap>()
        ReportPdf.draw(object : ReportPdf.PageSink {
            override fun start(n: Int, w: Int, h: Int): android.graphics.Canvas {
                val b = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
                pages += b
                return android.graphics.Canvas(b).apply { scale(2f, 2f) }
            }
            override fun end() {}
        }, today(), 14, "Dra. Ana (Psicólogo(a))")
        val dir = File("build/shots").apply { mkdirs() }
        pages.forEachIndexed { i, b -> File(dir, "relatorio-pagina-${i + 1}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    @Test fun crisis() {
        rule.setContent { SunBiTheme { Root() } }
        rule.onNodeWithText("Plano").performClick()
        rule.onNodeWithText("Preciso de ajuda agora").performClick()
        save("claro-5-ajuda")
    }
}
