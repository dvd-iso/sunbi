package com.dvdiso.sunbi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.lastDays
import com.dvdiso.sunbi.medAdherence
import com.dvdiso.sunbi.report
import com.dvdiso.sunbi.rhythm
import com.dvdiso.sunbi.wakeSpreadMinutes

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(todayStr: String, contentPadding: PaddingValues) {
    val ctx = LocalContext.current
    var n by rememberSaveable { mutableIntStateOf(14) }
    val days = lastDays(todayStr, n)

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Histórico", style = MaterialTheme.typography.headlineMediumEmphasized)
                ChoiceGroup(
                    options = listOf(7 to "7 dias", 14 to "14 dias", 28 to "28 dias"),
                    selected = n,
                    onSelect = { if (it != null) n = it },
                )
            }
        }
        item {
            val sl = days.mapNotNull { Repo.sleep(it) }
            val r = days.mapNotNull { rhythm(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${days.count { Repo.checkedIn(it) }}/$n", "dias com registro", Modifier.weight(1f))
                Stat(if (sl.isEmpty()) "–" else "%.1f h".format(sl.average()).replace('.', ','), "sono médio", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(medAdherence(days)?.let { "$it%" } ?: "–", "remédios marcados", Modifier.weight(1f))
                Stat(
                    if (r.isEmpty()) "–" else "${100 * r.sumOf { it.first } / r.sumOf { it.second }}%",
                    "âncoras no horário", Modifier.weight(1f),
                )
            }
        }
        item {
            SectionCard("Humor", subtitle = "Acima da linha: acelerado. Abaixo: para baixo. Pontos: sem registro.") {
                LifeChart(days)
            }
        }
        item {
            val spread = wakeSpreadMinutes(days)
            SectionCard(
                "Sono",
                subtitle = "Linha tracejada: seu sono habitual." + (spread?.let { " Horário de acordar variou ±$it min." } ?: ""),
            ) {
                SleepChart(days)
            }
        }
        item { TaskGrid(days.takeLast(7)) }
        item {
            val text = report(todayStr, n)
            var open by rememberSaveable { mutableStateOf(false) }
            SectionCard("Relatório para a consulta", subtitle = "Resumo em texto dos últimos $n dias para o psiquiatra ou psicólogo.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        runCatching { ctx.startActivity(Intent.createChooser(send, "Enviar relatório")) }
                    }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Filled.Share, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Compartilhar", maxLines = 1)
                    }
                    OutlinedButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Relatório SunBi", text))
                    }, shapes = ButtonDefaults.shapes()) { Text("Copiar", maxLines = 1) }
                }
                Text(
                    text,
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { open = !open }.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (open) Int.MAX_VALUE else 9, overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { open = !open }) { Text(if (open) "Mostrar menos" else "Ver relatório inteiro") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Grade de tarefas x dias (últimos 7). */
@Composable
private fun TaskGrid(days: List<String>) {
    val tasks = Repo.tasks()
    val cs = MaterialTheme.colorScheme
    val wd = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")
    SectionCard("Rotina na semana") {
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = 30.dp
        // o rótulo fica com o que sobra depois das 7 colunas (mínimo 72 dp: sobra rolagem lateral)
        val labelW = (maxWidth - cell * days.size).coerceIn(72.dp, 200.dp)
        Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Spacer(Modifier.width(labelW))
                for (d in days) {
                    val ld = java.time.LocalDate.parse(d)
                    Text("${wd[ld.dayOfWeek.value - 1]}\n${ld.dayOfMonth}", Modifier.width(cell), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            for (t in tasks) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (labelW < 110.dp) t.label else "${t.time} ${t.label}", Modifier.width(labelW), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium)
                    for (d in days) {
                        val y = Repo.done(d, t.id)
                        Box(Modifier.width(cell), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(14.dp).clip(if (y) RoundedCornerShape(4.dp) else CircleShape)
                                    .background(if (y) cs.primary else cs.surfaceContainerHighest),
                            )
                        }
                    }
                }
            }
        }
      }
    }
}
