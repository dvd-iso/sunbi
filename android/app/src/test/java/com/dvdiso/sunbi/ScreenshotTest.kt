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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeUp
import com.dvdiso.sunbi.ui.Root
import com.dvdiso.sunbi.ui.SunBiTheme
import com.dvdiso.sunbi.ui.ThemeState
import org.junit.Assert.assertTrue
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
    @Test fun wide() {
        rule.setContent { SunBiTheme { Root() } }
        save("largo-1-hoje")
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

    /** Modo férias (toque na flor), editor de rotina e a assistente na primeira vez. */
    @Test fun newScreens() {
        rule.setContent { SunBiTheme { Root() } }
        rule.onNodeWithContentDescription("SunBi").performClick()
        save("claro-10-ferias")
        rule.onNodeWithText("Editar").performClick() // o da rotina
        save("claro-11-rotina")
        rule.onNodeWithText("Folga e férias").performClick()
        save("claro-12-rotina-folga")
        rule.onNodeWithText("Levantar da cama").performClick()
        save("claro-13-rotina-tarefa")
    }

    @Test fun sunAiSetup() {
        rule.setContent { SunBiTheme { Root() } }
        rule.onNodeWithText("Assistente").performClick()
        save("claro-14-sunai")
    }

    /** Assistente com uma conversa (sem rede: as mensagens são gravadas direto). */
    @Test fun sunAiChat() {
        Prefs.aiConsent = true
        Prefs.aiKey = "chave-de-teste"
        Assistant.save(listOf(
            Assistant.Msg("user", "Como foi minha semana?"),
            Assistant.Msg("assistant", "Sua semana teve **dois lados**:\n- Começo para baixo (humor -2), com sono longo.\n- Fim acelerado: sono caiu para 4,5 h e energia subiu.\n\nVale seguir o seu plano de subida hoje: deitar no horário e evitar cafeína."),
            Assistant.Msg("user", "Ajuste minha rotina"),
            Assistant.Msg("assistant", "Fiz dois ajustes pequenos para proteger o sono.",
                listOf(Assistant.Change("add", "semana", "21:00 Desligar notificações"), Assistant.Change("update", "semana", "22:30 Deitar → 22:15 Deitar")),
                org.json.JSONObject()),
        ))
        rule.setContent { SunBiTheme { Root() } }
        rule.onNodeWithText("Assistente").performClick()
        save("claro-16-sunai-conversa")
    }

    @Test fun sunAiEmpty() {
        Prefs.aiConsent = true
        Prefs.aiKey = "chave-de-teste"
        Assistant.clear()
        rule.setContent { SunBiTheme { Root() } }
        rule.onNodeWithText("Assistente").performClick()
        save("claro-17-sunai-vazia")
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

    /** Tour inicial: boas-vindas, nome, horários, remédios e a rotina montada. */
    @Test fun onboarding() {
        Prefs.onboarded = false
        rule.setContent { SunBiTheme { Root() } }
        save("tour-1-ola")
        rule.onNodeWithText("Vamos lá").performClick()
        save("tour-2-nome")
        rule.onNodeWithText("Pular").performClick()
        rule.onNodeWithText("TDAH").performClick()
        save("tour-2b-motivo")
        rule.onNodeWithText("Continuar").performClick() // o que te trouxe
        save("tour-3-sono")
        rule.onNodeWithText("Continuar").performClick()
        rule.onNodeWithText("Continuar").performClick() // dias de folga
        save("tour-4-folga")
        rule.onNodeWithText("Continuar").performClick()
        rule.onNodeWithText("De manhã").performClick()
        rule.onNodeWithText("À noite").performClick()
        save("tour-5-remedios")
        rule.onNodeWithText("Continuar").performClick()
        rule.onNodeWithText("Continuar").performClick() // dia
        rule.onNodeWithText("Continuar").performClick() // refeições
        save("tour-6-extras")
        rule.onNodeWithText("Continuar").performClick()
        save("tour-6b-lembretes")
        rule.onNodeWithText("Continuar").performClick()
        save("tour-6c-assistente")
        rule.onNodeWithText("Montar minha rotina").performClick()
        save("tour-7-pronto")
        rule.onNodeWithText("Começar").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Histórico").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(Prefs.onboarded)
        assertTrue(Repo.tasks().any { it.med })
        // a IA foi recusada (padrão): a aba não existe
        assertTrue(rule.onAllNodesWithText("Assistente").fetchSemanticsNodes().isEmpty())
        save("tour-8-hoje")
        Prefs.aiEnabled = true
    }

    /** Conquistas e a comemoração de uma conquista nova. */
    @Test fun achievements() {
        Prefs.seenLevel = 1 // conquistas novas desde a última vez são comemoradas
        Prefs.seenBadges = emptySet()
        rule.setContent { SunBiTheme { Root() } }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Que legal").fetchSemanticsNodes().isNotEmpty() }
        save("claro-18-conquista")
        rule.onNodeWithText("Que legal").performClick()
        rule.onNodeWithContentDescription("Conquistas", substring = true).performClick()
        save("claro-19-conquistas")
    }

    /** Tela inicial (modo launcher) com apps de exemplo e a gaveta com busca. */
    @Test fun launcher() {
        val me = android.os.Process.myUserHandle()
        fun app(name: String, pkg: String) = Launcher.AppEntry(name, android.content.ComponentName(pkg, "$pkg.Main"), me, 0L)
        val apps = listOf(app("WhatsApp", "com.whatsapp"), app("Câmera", "com.cam"), app("Spotify", "com.spotify"),
            app("Chrome", "com.chrome"), app("Mensagens", "com.msg"), app("Telefone", "com.phone"), app("Fotos", "com.photos"),
            app("Calendário", "com.cal"), app("Banco", "com.bank"), app("Mapas", "com.maps"))
        Prefs.launcherFavorites = listOf(apps[0].key, apps[5].key, apps[4].key, apps[3].key)
        rule.setContent { SunBiTheme { com.dvdiso.sunbi.ui.LauncherScreen(apps, 0L) {} } }
        save("claro-20-launcher")
        // segurar um app abre as opções (favoritar, renomear, ocultar...)
        rule.onNodeWithText("Telefone").performTouchInput { longClick() }
        save("claro-22-launcher-opcoes")
        rule.onNodeWithText("Renomear").performClick()
        save("claro-23-launcher-renomear")
        rule.onNodeWithText("Salvar").performClick()
        // deslizar para cima abre todos os apps
        rule.onNodeWithContentDescription("Tela inicial", substring = true).performTouchInput { swipeUp() }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Buscar app").fetchSemanticsNodes().isNotEmpty() }
        save("claro-21-launcher-apps")
    }

    /** Humor bem para baixo no check-in oferece deixar o dia só com o essencial. */
    @Test fun hardDayOffer() {
        Seed.clear()
        rule.setContent { SunBiTheme { Root() } }
        rule.onNodeWithText("Registrar").performClick()
        rule.onNodeWithText("Para baixo").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Quer deixar hoje só o essencial?").fetchSemanticsNodes().isNotEmpty() }
        save("claro-24-checkin-essencial")
        rule.onNodeWithText("Só o essencial").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Pronto").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(Repo.isHard(today()))
        assertTrue(Repo.tasksFor(today()).all { it.anchor || it.med })
    }

    @Test fun crisis() {
        rule.setContent { SunBiTheme { Root() } }
        rule.onNodeWithText("Plano").performClick()
        rule.onNodeWithText("Preciso de ajuda agora").performClick()
        save("claro-5-ajuda")
    }
}
