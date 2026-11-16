package com.dvdiso.sunbi

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
        rule.onNodeWithText("Ajustes").performClick()
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

    @Test fun crisis() {
        rule.setContent { SunBiTheme { Root() } }
        rule.onNodeWithText("Plano").performClick()
        rule.onNodeWithText("Preciso de ajuda agora").performClick()
        save("claro-5-ajuda")
    }
}
