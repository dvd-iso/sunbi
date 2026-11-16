package com.dvdiso.sunbi.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.RingService

/** Tela cheia do alarme. Sem adiar: só "Concluído" ou "Parar". */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeState.load()
        setContent {
            SunBiTheme {
                val task by RingService.ringing.collectAsState()
                LaunchedEffect(task) { if (task == null) finish() }
                val t = task ?: return@SunBiTheme
                AlarmScreen(
                    time = t.time,
                    label = t.label,
                    step = t.step,
                    onDone = { send(RingService.DONE, t.id) },
                    onStop = { send(RingService.STOP, t.id) },
                )
            }
        }
    }

    private fun send(action: String, id: String) {
        startService(Intent(this, RingService::class.java).setAction(action).putExtra("task", id))
        finish()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AlarmScreen(time: String, label: String, step: String, onDone: () -> Unit, onStop: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // Surface define a cor do texto (onSurface); sem ela o texto ficava preto no tema escuro
    Surface(Modifier.fillMaxSize(), color = cs.surface, contentColor = cs.onSurface) {
    Column(
        // rola quando a tela é baixa (celular deitado, fonte grande): Parar nunca fica de fora
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SunBiMark(Modifier.padding(bottom = 28.dp), size = 132.dp, spin = true)
        Text(time, style = MaterialTheme.typography.displayLargeEmphasized, color = cs.primary)
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.headlineMediumEmphasized, color = cs.onSurface, textAlign = TextAlign.Center)
        if (step.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("Primeiro passo: $step", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onDone,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().height(ButtonDefaults.LargeContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
        ) {
            Icon(Icons.Filled.Check, null, Modifier.size(ButtonDefaults.LargeIconSize))
            Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(ButtonDefaults.LargeContainerHeight)))
            Text("Concluído", style = ButtonDefaults.textStyleFor(ButtonDefaults.LargeContainerHeight))
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = onStop,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
        ) { Text("Parar", style = MaterialTheme.typography.titleMedium) }
    }
    }
}
