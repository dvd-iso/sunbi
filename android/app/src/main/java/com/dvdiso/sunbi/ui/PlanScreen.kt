package com.dvdiso.sunbi.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.Contact
import com.dvdiso.sunbi.Repo
import com.dvdiso.sunbi.Sign
import com.dvdiso.sunbi.newId

private val SAFETY_STEPS = listOf(
    "warn" to "1. Sinais de que uma crise está chegando",
    "cope" to "2. O que eu faço sozinho para me acalmar",
    "distract" to "3. Pessoas e lugares que me distraem",
    "safe" to "6. Deixar o ambiente mais seguro",
    "reasons" to "Meus motivos para seguir em frente",
)

fun dial(ctx: Context, number: String) {
    val digits = number.filter { it.isDigit() || it == '+' }
    runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlanScreen(contentPadding: PaddingValues, openCrisis: Boolean, onCrisisShown: () -> Unit) {
    var crisis by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(openCrisis) {
        if (openCrisis) {
            crisis = true
            onCrisisShown()
        }
    }
    var about by rememberSaveable { mutableStateOf(false) }

    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text("Meu plano", style = MaterialTheme.typography.headlineMediumEmphasized)
                Text(
                    "Monte com calma, num dia bom, e de preferência junto com sua equipe de saúde. É ele que você vai seguir quando os sinais aparecerem.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            val cs = MaterialTheme.colorScheme
            Button(
                onClick = { crisis = true },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) {
                Icon(Icons.Filled.Favorite, null)
                Spacer(Modifier.width(10.dp))
                Text("Preciso de ajuda agora", style = MaterialTheme.typography.titleMediumEmphasized)
            }
        }
        item { SignsCard("up", "Sinais de que o humor está subindo", "ex.: dormir 4 h e acordar com energia") }
        item { PlanListCard("up", "Quando o humor sobe, eu vou...") }
        item { SignsCard("down", "Sinais de que o humor está descendo", "ex.: parar de responder mensagens") }
        item { PlanListCard("down", "Quando o humor desce, eu vou...") }
        item { SafetyPlanCard() }
        item { ContactsCard() }
        item {
            SectionCard("De onde vêm estes métodos", subtitle = "IPSRT, NIMH Life Chart, psicoeducação de Barcelona, plano de segurança Stanley-Brown e TCC para TDAH.") {
                FilledTonalButton(onClick = { about = true }, shapes = ButtonDefaults.shapes()) { Text("Ler resumo") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (crisis) CrisisSheet { crisis = false }
    if (about) AboutSheet { about = false }
}

@Composable
private fun SignsCard(pole: String, title: String, hint: String) {
    val all = Repo.signs()
    val mine = all.filter { it.pole == pole }
    SectionCard(title, subtitle = "Os seus, do seu jeito. Eles aparecem no check-in para marcar.") {
        EditableList(
            items = mine.map { it.label },
            onChange = { labels ->
                val kept = labels.map { l -> mine.firstOrNull { it.label == l } ?: Sign(newId(pole.take(1) + "_"), l, pole) }
                Repo.setSigns(all.filter { it.pole != pole } + kept)
            },
            placeholder = hint,
        )
    }
}

@Composable
private fun PlanListCard(section: String, title: String) {
    SectionCard(title) {
        EditableList(Repo.planList(section), { Repo.setPlanList(section, it) }, "Adicionar um passo")
    }
}

@Composable
private fun SafetyPlanCard() {
    SectionCard(
        "Plano de segurança",
        subtitle = "Os passos do plano de Stanley e Brown, para momentos de crise ou pensamentos de se machucar. Os passos 4 e 5 são seus contatos, logo abaixo.",
    ) {
        for ((key, title) in SAFETY_STEPS) {
            Label(title)
            EditableList(Repo.planList(key), { Repo.setPlanList(key, it) }, "Adicionar")
            Spacer(Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContactsCard() {
    val ctx = LocalContext.current
    val list = Repo.contacts()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pro by remember { mutableStateOf(false) }
    SectionCard("4 e 5. Pessoas e profissionais", subtitle = "Quem você pode chamar: pessoas de confiança, psiquiatra, psicólogo, CAPS.") {
        for ((i, c) in list.withIndex()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("${c.role} · ${c.phone}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { dial(ctx, c.phone) }) { Icon(Icons.Filled.Phone, "Ligar para ${c.name}") }
                IconButton(onClick = { Repo.setContacts(list.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Filled.Close, "Remover ${c.name}") }
            }
        }
        HorizontalDivider()
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
        OutlinedTextField(
            phone, { phone = it }, label = { Text("Telefone") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        ChoiceGroup(listOf(false to "Pessoa", true to "Profissional"), pro, { pro = it ?: false })
        Button(
            onClick = {
                Repo.setContacts(list + Contact(name.trim(), if (pro) "Profissional" else "Pessoa de confiança", phone.trim()))
                name = ""; phone = ""
            },
            enabled = name.isNotBlank() && phone.isNotBlank(),
            shapes = ButtonDefaults.shapes(),
        ) { Text("Adicionar contato") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CrisisSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Você não está só", style = MaterialTheme.typography.headlineSmallEmphasized)
            Text("Se você corre perigo agora, ligue para o SAMU ou vá ao pronto-socorro mais próximo.", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { dial(ctx, "188") }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Text("CVV 188")
                }
                Button(
                    onClick = { dial(ctx, "192") }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError),
                ) { Text("SAMU 192") }
            }
            Text("CVV: conversa gratuita, 24 horas, também por chat em cvv.org.br.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)

            val contacts = Repo.contacts()
            if (contacts.isNotEmpty()) {
                Label("Minhas pessoas e profissionais")
                for (c in contacts) {
                    FilledTonalButton(onClick = { dial(ctx, c.phone) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Phone, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("${c.name} · ${c.role}")
                    }
                }
            }
            for ((key, title) in SAFETY_STEPS.filter { it.first != "warn" }) {
                val items = Repo.planList(key)
                if (items.isEmpty()) continue
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        for (s in items) Text("• $s", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Fechar") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Como o SunBi funciona", style = MaterialTheme.typography.headlineSmall)
            for ((t, b) in ABOUT) {
                Text(t, style = MaterialTheme.typography.titleMedium)
                Text(b, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "O SunBi ajuda a organizar e observar. Ele não diagnostica, não substitui tratamento e não deve ser usado para mudar remédios por conta própria. As referências completas estão em docs/METODOS.md no repositório.",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val ABOUT = listOf(
    "Ritmo social (IPSRT)" to "Horários regulares para levantar, falar com alguém, começar o dia, jantar e deitar ajudam a estabilizar o relógio biológico. Essas são as âncoras da rotina; o histórico mostra quantas foram feitas a até 45 minutos do horário (ideia do Social Rhythm Metric).",
    "Registro diário do humor (NIMH Life Chart)" to "Uma nota por dia, de -3 a +3, mais sono, energia, irritação e ansiedade. Olhar o desenho das semanas ajuda você e sua equipe a ver padrões e a resposta ao tratamento.",
    "Sinais de alerta e plano de ação (psicoeducação)" to "Cada pessoa tem seus sinais. Aprender a reconhecê-los cedo e ter um plano combinado é um dos pilares da psicoeducação para o transtorno bipolar, junto com a regularidade e a adesão ao tratamento.",
    "Sono" to "Dormir menos e não sentir cansaço é um dos sinais mais comuns antes de uma subida. O SunBi compara suas noites com o seu próprio normal.",
    "Plano de segurança (Stanley-Brown)" to "Passos curtos e pessoais para atravessar uma crise: sinais, o que fazer sozinho, distrações, quem chamar, profissionais e deixar o ambiente mais seguro.",
    "TDAH: tornar o tempo e o começo visíveis" to "A TCC para TDAH em adultos trabalha com lembretes externos, planos do tipo quando-então e quebrar tarefas em passos pequenos. Por isso cada tarefa pode ter um primeiro passo, uma duração estimada e um bloco curto de foco.",
    "Sem culpa" to "Não há sequências que zeram. Um dia sem registro não apaga nada: recomeçar faz parte.",
)
