package com.dvdiso.sunbi

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.dvdiso.sunbi.ui.LauncherScreen
import com.dvdiso.sunbi.ui.Root
import com.dvdiso.sunbi.ui.SunBiTheme
import com.dvdiso.sunbi.ui.ThemeState
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * As mesmas telas em tamanhos de aparelho de verdade (a altura real da janela, não uma página
 * comprida): celular comum, celular pequeno com fonte grande, celular deitado e tablet, claro e
 * escuro. Cada fluxo também confere que os botões continuam alcançáveis em todos os tamanhos.
 * As imagens vão para app/build/shots/tam-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SizesTest {
    @get:Rule val rule = createComposeRule()

    @Before
    fun data() {
        Prefs.dynamicColor = false
        Prefs.navLabels = true
        ThemeState.load()
        Seed.clear()
        Seed.twoWeeks()
    }

    private fun save(name: String) {
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/shots").apply { mkdirs() }
        File(dir, "tam-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Toca num texto, rolando até ele se a tela for baixa. */
    private fun tap(text: String) {
        val node = rule.onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    private fun tabs(p: String) {
        rule.setContent { SunBiTheme { Root() } }
        save("$p-hoje")
        tap("Histórico")
        save("$p-historico")
        tap("Assistente")
        save("$p-assistente")
        tap("Plano")
        save("$p-plano")
        rule.onNodeWithContentDescription("Ajustes").performClick()
        save("$p-ajustes")
        tap("Aparência e sons")
        save("$p-ajustes-aparencia")
    }

    private fun tour(p: String) {
        Prefs.onboarded = false
        rule.setContent { SunBiTheme { Root() } }
        save("$p-tour-ola")
        tap("Vamos lá")
        tap("Pular")
        save("$p-tour-motivo")
        tap("Continuar")
        save("$p-tour-sono")
        tap("Continuar")
        save("$p-tour-folga")
        tap("Continuar")
        tap("Continuar")
        tap("De manhã")
        tap("À tarde")
        tap("À noite")
        save("$p-tour-remedios")
        tap("Continuar")
        save("$p-tour-dia")
        tap("Continuar")
        save("$p-tour-refeicoes")
        tap("Continuar")
        save("$p-tour-extras")
        tap("Continuar")
        save("$p-tour-lembretes")
        tap("Continuar")
        tap("Quero usar")
        save("$p-tour-assistente")
        tap("Montar minha rotina")
        save("$p-tour-pronto")
        tap("Começar")
        rule.waitUntil(5000) { rule.onAllNodesWithText("Histórico").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(Prefs.onboarded)
        assertTrue(Repo.tasks().count { it.med } == 3)
        // a IA foi aceita: a aba existe
        assertTrue(rule.onAllNodesWithText("Assistente").fetchSemanticsNodes().isNotEmpty())
        save("$p-tour-hoje")
    }

    private fun launcher(p: String) {
        val me = android.os.Process.myUserHandle()
        fun app(name: String, pkg: String) = Launcher.AppEntry(name, android.content.ComponentName(pkg, "$pkg.Main"), me, 0L)
        val apps = listOf("WhatsApp", "Câmera", "Spotify", "Chrome", "Mensagens", "Telefone", "Fotos", "Calendário", "Banco",
            "Mapas", "Relógio", "Calculadora", "Arquivos", "Ajustes do sistema", "Um app com um nome bem comprido mesmo").map { app(it, "com.t.${it.hashCode()}") }
        Prefs.launcherFavorites = apps.take(6).map { it.key }
        rule.setContent { SunBiTheme { LauncherScreen(apps, 0L) {} } }
        save("$p-launcher")
        rule.onNodeWithContentDescription("Tela inicial", substring = true).performTouchInput { swipeUp() }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Buscar app").fetchSemanticsNodes().isNotEmpty() }
        save("$p-launcher-apps")
    }

    // ── celular comum ──
    @Config(qualifiers = "w360dp-h740dp-xhdpi") @Test fun phoneTabs() = tabs("celular")
    @Config(qualifiers = "w360dp-h740dp-xhdpi") @Test fun phoneTour() = tour("celular")
    @Config(qualifiers = "w360dp-h740dp-xhdpi") @Test fun phoneLauncher() = launcher("celular")

    @Config(qualifiers = "w360dp-h740dp-night-xhdpi") @Test fun phoneDarkTabs() = tabs("celular-escuro")
    @Config(qualifiers = "w360dp-h740dp-night-xhdpi") @Test fun phoneDarkTour() = tour("celular-escuro")
    @Config(qualifiers = "w360dp-h740dp-night-xhdpi") @Test fun phoneDarkLauncher() = launcher("celular-escuro")

    // ── celular pequeno com a fonte do sistema grande ──
    @Config(qualifiers = "w320dp-h568dp-xhdpi") @Test fun smallTabs() { RuntimeEnvironment.setFontScale(1.3f); tabs("pequeno") }
    @Config(qualifiers = "w320dp-h568dp-xhdpi") @Test fun smallTour() { RuntimeEnvironment.setFontScale(1.3f); tour("pequeno") }
    @Config(qualifiers = "w320dp-h568dp-xhdpi") @Test fun smallLauncher() { RuntimeEnvironment.setFontScale(1.3f); launcher("pequeno") }

    // ── celular deitado ──
    @Config(qualifiers = "w740dp-h360dp-land-xhdpi") @Test fun landscapeTabs() = tabs("deitado")
    @Config(qualifiers = "w740dp-h360dp-land-xhdpi") @Test fun landscapeTour() = tour("deitado")
    @Config(qualifiers = "w740dp-h360dp-land-xhdpi") @Test fun landscapeLauncher() = launcher("deitado")

    // ── tablet ──
    @Config(qualifiers = "w840dp-h1100dp-mdpi") @Test fun tabletTabs() = tabs("tablet")
    @Config(qualifiers = "w840dp-h1100dp-mdpi") @Test fun tabletTour() = tour("tablet")
    @Config(qualifiers = "w840dp-h1100dp-night-mdpi") @Test fun tabletDarkTabs() = tabs("tablet-escuro")
}
