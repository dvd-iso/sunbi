package com.dvdiso.sunflowerclocks.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dvdiso.sunflowerclocks.RingService

/** Tela cheia do alarme. Sem adiar: só "Feito" ou "Parar". */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SunflowerTheme {
                val task by RingService.ringing.collectAsState()
                LaunchedEffect(task) { if (task == null) finish() }
                val t = task ?: return@SunflowerTheme
                AlarmScreen(
                    time = t.time,
                    label = t.label,
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

@Composable
private fun AlarmScreen(time: String, label: String, onDone: () -> Unit, onStop: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Sun.bg).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SunflowerIcon(Modifier.padding(bottom = 24.dp), size = 112.dp)
        Text(time, color = Sun.sun, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(label, color = Sun.ink, fontSize = 24.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onDone,
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Sun.sun, contentColor = Sun.dark),
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) { Text("Feito ✓", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = onStop,
            shape = RectangleShape,
            modifier = Modifier.fillMaxWidth().height(60.dp),
        ) { Text("Parar", fontSize = 20.sp, color = Sun.dim) }
    }
}
