package com.dvdiso.sunbi.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.dvdiso.sunbi.Launcher

/**
 * Tela inicial do telefone (modo launcher). Só existe quando a pessoa liga em Ajustes:
 * a atividade fica desligada no manifesto até lá.
 */
class LauncherActivity : ComponentActivity() {
    private val apps = mutableStateOf<List<Launcher.AppEntry>>(emptyList())
    private val homePressed = mutableLongStateOf(0L)
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { reload() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // tela inicial não "volta" para lugar nenhum
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { homePressed.longValue++ }
        })
        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        setContent {
            SunBiTheme {
                LauncherScreen(apps = apps.value, homePressed = homePressed.longValue, onOpenSunBi = { Launcher.openSunBi(this) })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    /** Botão Home com o launcher já aberto: volta para a tela inicial (fecha a gaveta). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePressed.longValue++
    }

    private fun reload() {
        Thread { val l = Launcher.apps(this); runOnUiThread { apps.value = l } }.start()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pkgReceiver) }
        super.onDestroy()
    }
}
