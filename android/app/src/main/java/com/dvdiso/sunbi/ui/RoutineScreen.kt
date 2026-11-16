package com.dvdiso.sunbi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.Task
import com.dvdiso.sunbi.newId
import java.time.LocalTime

private fun load(off: Boolean) = if (off) Repo.offTasks() else Repo.tasks()
private fun store(off: Boolean, list: List<Task>) = if (off) Repo.setOffTasks(list) else Repo.setTasks(list)

private fun period(time: String): String {
    val h = runCatching { LocalTime.parse(time).hour }.getOrDefault(12)
    return when {
        h < 12 -> "Manhã"
        h < 18 -> "Tarde"
        else -> "Noite"
    }
}

/**
 * Editor da rotina: escolhe semana ou folga, toca numa tarefa para mudar, "+" para criar.
 * Tudo salva na hora.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoutineScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    var off by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf(false) }
    val tasks = load(off)
    val groups = tasks.groupBy { period(it.time) }

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            ScreenHeader("Rotina", leading = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            })
            ChoiceGroup(listOf(false to "Dias de semana", true to "Folga e férias"), off, { if (it != null) off = it })
            Spacer(Modifier.height(8.dp))
            Text(
                if (off) "Usada nos fins de semana, feriados e no modo férias (toque na flor em Hoje). " +
                    "Dica: acorde no máximo 1 hora depois do normal, isso protege o seu ritmo."
                else "Toque numa tarefa para mudar. Os alarmes tocam nesses horários.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (off) {
            item { OffAutoSwitches() }
        }
        for (p in listOf("Manhã", "Tarde", "Noite")) {
            val list = groups[p] ?: continue
            item(key = "h-$off-$p") {
                Text(p, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp))
            }
            itemsIndexed(list, key = { _, t -> "t-$off-${t.id}" }) { i, t ->
                SegmentedListItem(
                    onClick = { editing = t },
                    shapes = ListItemDefaults.segmentedShapes(i, list.size),
                    modifier = Modifier.animateItem(),
                    leadingContent = {
                        Text(t.time, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    },
                    supportingContent = listOfNotNull(
                        if (t.med) "remédio" else null,
                        if (t.anchor) "âncora" else null,
                        t.step.takeIf { it.isNotBlank() }?.let { "1º passo: $it" },
                    ).takeIf { it.isNotEmpty() }?.let { bits -> { Text(bits.joinToString(" · ")) } },
                ) { Text(t.label, style = MaterialTheme.typography.bodyLarge) }
            }
        }
        item {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { creating = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Nova tarefa", style = MaterialTheme.typography.titleMedium)
                }
                if (off) {
                    OutlinedButton(onClick = { Repo.setOffTasks(Repo.tasks()) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                        Text("Copiar a rotina dos dias de semana")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editing?.let { t ->
        TaskSheet(t, isNew = false, onDismiss = { editing = null }, onSave = { nt ->
            store(off, load(off).map { if (it.id == t.id) nt else it })
            editing = null
        }, onDelete = {
            store(off, load(off).filterNot { it.id == t.id })
            editing = null
        })
    }
    if (creating) {
        TaskSheet(Task(newId("t"), "12:00", ""), isNew = true, onDismiss = { creating = false }, onSave = { nt ->
            store(off, load(off) + nt)
            creating = false
        }, onDelete = { creating = false })
    }
}

@Composable
private fun OffAutoSwitches() {
    SectionCard("Quando usar a folga") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sábados e domingos", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(Repo.offWeekends(), { Repo.set("off/weekends", it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Feriados nacionais", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(Repo.offHolidays(), { Repo.set("off/holidays", it) })
        }
    }
}

/** Formulário curto de uma tarefa: nome, horário, primeiro passo e dois interruptores. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TaskSheet(task: Task, isNew: Boolean, onDismiss: () -> Unit, onSave: (Task) -> Unit, onDelete: () -> Unit) {
    var label by remember { mutableStateOf(task.label) }
    var time by remember { mutableStateOf(task.time) }
    var step by remember { mutableStateOf(task.step) }
    var med by remember { mutableStateOf(task.med) }
    var anchor by remember { mutableStateOf(task.anchor) }
    var picking by remember { mutableStateOf(false) }

    EditorSheet(if (isNew) "Nova tarefa" else "Editar tarefa", onDismiss = onDismiss) {
        OutlinedTextField(label, { label = it }, label = { Text("O que fazer") }, modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large)
        ValueTile("Horário", time, Modifier.fillMaxWidth()) { picking = true }
        OutlinedTextField(step, { step = it }, label = { Text("Primeiro passo (opcional)") },
            placeholder = { Text("A menor ação para começar") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("É remédio", style = MaterialTheme.typography.bodyLarge)
                Text("Conta nas doses do relatório", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(med, { med = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("É âncora do dia", style = MaterialTheme.typography.bodyLarge)
                Text("Horário que protege o humor: levantar, primeiro contato, começar o dia, jantar, deitar",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(anchor, { anchor = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(!isNew) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Excluir", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onSave(task.copy(label = label.trim(), time = time, step = step.trim(), med = med, anchor = anchor)) },
                enabled = label.isNotBlank(), shapes = ButtonDefaults.shapes(),
            ) { Text("Salvar") }
        }
    }

    if (picking) {
        val lt = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.NOON)
        val state = rememberTimePickerState(lt.hour, lt.minute, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { picking = false },
            title = { Text("Horário") },
            confirmButton = {
                TextButton(onClick = {
                    time = "%02d:%02d".format(state.hour, state.minute)
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        ) { TimePicker(state) }
    }
}
