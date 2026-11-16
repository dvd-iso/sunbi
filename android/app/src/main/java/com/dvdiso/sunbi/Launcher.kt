package com.dvdiso.sunbi

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import java.text.Normalizer

/**
 * Modo launcher: o SunBi como tela inicial do telefone, no espírito dos launchers minimalistas
 * (lista de apps em texto, busca que abre na hora, nada de ícones gritando), com a rotina do dia
 * na frente. Tudo opcional: liga em Ajustes → Tela inicial.
 */
object Launcher {
    /** Um app que pode ser aberto. [key] identifica pacote + atividade + usuário (perfil de trabalho). */
    data class AppEntry(val label: String, val component: ComponentName, val user: UserHandle, val userSerial: Long) {
        val key: String get() = "${component.flattenToShortString()}#$userSerial"
        val search: String = fold(label)
    }

    private val activity = ComponentName("com.dvdiso.sunbi", "com.dvdiso.sunbi.ui.LauncherActivity")

    const val MAX_FAVORITES = 8

    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    // ── ligar e desligar ────────────────────────────────────────────────────

    fun isEnabled(ctx: Context): Boolean =
        ctx.packageManager.getComponentEnabledSetting(activity) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Liga a atividade de tela inicial (ela fica desligada no manifesto até a pessoa querer). */
    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            activity,
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    fun isDefault(ctx: Context): Boolean = runCatching {
        ctx.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
    }.getOrDefault(false)

    /** Pede ao sistema para escolher o SunBi como tela inicial (diálogo do próprio Android). */
    fun requestDefaultIntent(ctx: Context): Intent? {
        val rm = ctx.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_HOME)) return Intent(Settings.ACTION_HOME_SETTINGS)
        return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }

    // ── apps ────────────────────────────────────────────────────────────────

    /** Todos os apps abríveis, de todos os perfis, em ordem alfabética, sem o próprio launcher. */
    fun apps(ctx: Context): List<AppEntry> {
        val la = ctx.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val um = ctx.getSystemService(UserManager::class.java)
        val users = runCatching { um?.userProfiles }.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(Process.myUserHandle())
        val out = ArrayList<AppEntry>()
        for (u in users) {
            val serial = runCatching { um?.getSerialNumberForUser(u) }.getOrNull() ?: 0L
            val list = runCatching { la.getActivityList(null, u) }.getOrNull() ?: continue
            for (a in list) {
                if (a.componentName == activity) continue
                out += AppEntry(a.label.toString(), a.componentName, u, serial)
            }
        }
        return out.sortedWith(compareBy({ it.search }, { it.userSerial }))
    }

    fun launch(ctx: Context, app: AppEntry): Boolean = runCatching {
        ctx.getSystemService(LauncherApps::class.java).startMainActivity(app.component, app.user, null, null)
        true
    }.getOrDefault(false)

    fun appInfo(ctx: Context, app: AppEntry) = runCatching {
        ctx.getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.component, app.user, null, null)
    }

    fun uninstall(ctx: Context, app: AppEntry) = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openSunBi(ctx: Context) {
        ctx.startActivity(Intent(ctx, com.dvdiso.sunbi.ui.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ── favoritos e escondidos (locais a cada aparelho) ─────────────────────

    fun favorites(): List<String> = Prefs.launcherFavorites
    fun hidden(): Set<String> = Prefs.launcherHidden

    fun toggleFavorite(app: AppEntry): Boolean {
        val f = Prefs.launcherFavorites
        return if (app.key in f) { Prefs.launcherFavorites = f - app.key; false }
        else { Prefs.launcherFavorites = (f + app.key).takeLast(MAX_FAVORITES); true }
    }

    fun setHidden(app: AppEntry, hide: Boolean) {
        Prefs.launcherHidden = if (hide) Prefs.launcherHidden + app.key else Prefs.launcherHidden - app.key
        if (hide) Prefs.launcherFavorites = Prefs.launcherFavorites - app.key
    }

    /** Busca: começo de palavra primeiro, depois qualquer trecho; sem acentos e maiúsculas. */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = fold(query.trim())
        if (q.isEmpty()) return apps
        val starts = apps.filter { a -> a.search.split(' ').any { it.startsWith(q) } }
        val contains = apps.filter { it.search.contains(q) && it !in starts }
        return starts + contains
    }
}
