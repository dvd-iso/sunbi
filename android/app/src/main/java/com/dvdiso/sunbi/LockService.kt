package com.dvdiso.sunbi

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Serviço de acessibilidade com uma função só: travar a tela quando a pessoa toca duas vezes
 * na tela inicial do SunBi (modo launcher). Não lê conteúdo de tela nem de outros apps
 * (canRetrieveWindowContent = false). A pessoa precisa ligá-lo nos ajustes de acessibilidade.
 */
class LockService : AccessibilityService() {
    companion object {
        @Volatile private var instance: LockService? = null

        fun enabled(): Boolean = instance != null

        /** Trava a tela. Devolve false se o serviço não está ligado. */
        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true

        fun openSettings(ctx: Context) = runCatching {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
}
