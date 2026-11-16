package com.dvdiso.sunbi.ui

import androidx.compose.foundation.layout.heightIn
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import com.dvdiso.sunbi.Prefs
import com.dvdiso.sunbi.ReportPdf
import com.dvdiso.sunbi.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.lastDays
import com.dvdiso.sunbi.medAdherence
import com.dvdiso.sunbi.report

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryScreen(todayStr: String, contentPadding: PaddingValues, onSettings: () -> Unit) {
    var n by rememberSaveable { mutableIntStateOf(14) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    val days = lastDays(todayStr, n)

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader("Histórico", onSettings = onSettings)
            ChoiceGroup(
                options = listOf(7 to "7 dias", 14 to "14 dias", 28 to "28 dias"),
                selected = n,
                onSelect = { if (it != null) n = it },
            )
        }
        item {
            // os três números numa linha de texto, e o relatório sempre à vista logo abaixo
            val sl = days.mapNotNull { Repo.sleep(it) }
            val reg = animatedInt(days.count { Repo.checkedIn(it) })
            val parts = listOfNotNull(
                "$reg ${if (reg == 1) "dia" else "dias"} com registro",
                if (sl.isEmpty()) null else "%.1f h de sono".format(sl.average()).replace('.', ','),
                medAdherence(days)?.let { "${animatedInt(it)}% dos remédios" },
            )
            Column(Modifier.enterFromBelow(0), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { Feedback.tap(); showReport = true }, shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Filled.Share, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Relatório para a consulta", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        item {
            SectionCard("Humor", Modifier.enterFromBelow(1), subtitle = "Barras para cima: humor acelerado. Para baixo: humor baixo.") { LifeChart(days) }
        }
        item {
            SectionCard("Sono", Modifier.enterFromBelow(2), subtitle = "Linha tracejada: o seu normal.") { SleepChart(days) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showReport) ReportSheet(todayStr, n) { showReport = false }
}

/** Gera o PDF bonitinho, mostra a primeira página e manda no WhatsApp para quem cuida de você. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReportSheet(todayStr: String, n: Int, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val contacts = Repo.contacts()
    // psicólogo(a) primeiro, depois outros profissionais
    val sorted = contacts.sortedBy { c -> when { c.role.startsWith("Psicólog") -> 0; c.role != "Pessoa de confiança" -> 1; else -> 2 } }
    var to by remember { mutableStateOf(sorted.firstOrNull()) }
    var name by remember { mutableStateOf(Prefs.reportName.ifBlank { Prefs.userName }) }
    var file by remember { mutableStateOf<java.io.File?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(n, to, name) {
        Prefs.reportName = name
        kotlinx.coroutines.delay(300)
        val f = withContext(Dispatchers.Default) { ReportPdf.build(ctx, todayStr, n, to?.let { "${it.name} (${it.role})" }) }
        file = f
        preview = withContext(Dispatchers.IO) { renderFirstPage(f) }
    }
    val message = "Oi! Segue o meu relatório do SunBi dos últimos $n dias."

    EditorSheet("Relatório para a consulta", "PDF dos últimos $n dias, pronto para mandar.", onDismiss) {
        Box(
            Modifier.fillMaxWidth().height(300.dp).clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = preview
            if (bmp == null) LoadingIndicator(Modifier.size(56.dp))
            else Image(bmp.asImageBitmap(), "Prévia do relatório", Modifier.fillMaxSize().padding(10.dp))
        }
        OutlinedTextField(
            name, { name = it }, label = { Text("Seu nome no relatório (opcional)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        )
        Label("Mandar para")
        if (sorted.isEmpty()) {
            Text(
                "Adicione o contato do seu psicólogo em Plano → Pessoas e profissionais para o WhatsApp já abrir a conversa dele.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column {
                for (c in sorted + listOf<com.dvdiso.sunbi.Contact?>(null)) {
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable { to = c }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(to == c, { to = c })
                        Text(c?.let { "${it.name} · ${it.role}" } ?: "Escolher no WhatsApp", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        Button(
            onClick = { file?.let { Share.pdfToWhatsApp(ctx, it, to?.phone, message) } },
            enabled = file != null, shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(Icons.Filled.Share, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Enviar no WhatsApp", style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { file?.let { Share.other(ctx, it, message) } }, enabled = file != null,
                shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Outro app", maxLines = 1) }
            OutlinedButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Relatório SunBi", report(todayStr, n)))
            }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Copiar texto", maxLines = 1) }
        }
    }
}

/** Primeira página do PDF como imagem, para a prévia. */
private fun renderFirstPage(f: java.io.File): android.graphics.Bitmap? = runCatching {
    android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        android.graphics.pdf.PdfRenderer(fd).use { r ->
            r.openPage(0).use { page ->
                val bmp = android.graphics.Bitmap.createBitmap(page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }
}.getOrNull()
