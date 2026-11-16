package com.dvdiso.sunbi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
            val sl = days.mapNotNull { Repo.sleep(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${days.count { Repo.checkedIn(it) }}", "dias com registro", Modifier.weight(1f))
                Stat(if (sl.isEmpty()) "–" else "%.1f h".format(sl.average()).replace('.', ','), "de sono por noite", Modifier.weight(1f))
                Stat(medAdherence(days)?.let { "$it%" } ?: "–", "dos remédios", Modifier.weight(1f))
            }
        }
        item {
            SectionCard("Humor", subtitle = "Para cima: acelerado. Para baixo: para baixo.") { LifeChart(days) }
        }
        item {
            SectionCard("Sono", subtitle = "Linha tracejada: o seu normal.") { SleepChart(days) }
        }
        item {
            FilledTonalButton(
                onClick = { showReport = true }, shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Share, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Relatório para a consulta", style = MaterialTheme.typography.titleMedium)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showReport) ReportSheet(todayStr, n) { showReport = false }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReportSheet(todayStr: String, n: Int, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val text = report(todayStr, n)
    EditorSheet("Relatório para a consulta", "Resumo dos últimos $n dias para levar ao psiquiatra ou psicólogo.", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                runCatching { ctx.startActivity(Intent.createChooser(send, "Enviar relatório")) }
            }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Compartilhar", maxLines = 1) }
            OutlinedButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Relatório SunBi", text))
            }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Copiar", maxLines = 1) }
        }
        Text(
            text,
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
