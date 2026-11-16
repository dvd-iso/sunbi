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
    /**
     * Um app que pode ser aberto. [label] é o nome mostrado (o que a pessoa deu, se renomeou);
     * [original] é o nome do próprio app. A busca acha pelos dois.
     */
    data class AppEntry(
        val label: String, val component: ComponentName, val user: UserHandle, val userSerial: Long,
        val original: String = label,
    ) {
        val key: String get() = "${component.flattenToShortString()}#$userSerial"
        val search: String = fold(if (original == label) label else "$label $original")
        val renamed: Boolean get() = original != label
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

    /** Pedido do papel HOME (diálogo do próprio Android), ou null se o sistema não oferece. */
    fun roleRequestIntent(ctx: Context): Intent? = runCatching {
        val rm = ctx.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_HOME)) null else rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }.getOrNull()

    /**
     * Abre a tela "App de início" dos ajustes do Android. É o caminho que sempre funciona,
     * inclusive quando o diálogo do papel HOME não aparece (alguns Samsung, ou depois de recusas).
     */
    fun openHomeSettings(ctx: Context) {
        for (action in listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS)) {
            val ok = runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
            if (ok) return
        }
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
                out += withName(AppEntry(a.label.toString(), a.componentName, u, serial))
            }
        }
        return sorted(out)
    }

    fun sorted(list: List<AppEntry>): List<AppEntry> = list.sortedWith(compareBy({ fold(it.label) }, { it.userSerial }))

    /** Aplica o nome que a pessoa deu ao app, se houver. */
    fun withName(app: AppEntry): AppEntry {
        val custom = Prefs.launcherNames[app.key]?.takeIf { it.isNotBlank() } ?: return app.copy(label = app.original)
        return app.copy(label = custom)
    }

    /** Renomeia (nome vazio volta ao original). Devolve o app com o nome novo. */
    fun rename(app: AppEntry, name: String): AppEntry {
        val n = name.trim()
        Prefs.launcherNames = if (n.isEmpty() || n == app.original) Prefs.launcherNames - app.key else Prefs.launcherNames + (app.key to n)
        return withName(app)
    }

    fun launch(ctx: Context, app: AppEntry): Boolean = runCatching {
        ctx.getSystemService(LauncherApps::class.java).startMainActivity(app.component, app.user, null, null)
        true
    }.getOrDefault(false)

    fun appInfo(ctx: Context, app: AppEntry) = runCatching {
        ctx.getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.component, app.user, null, null)
    }

    /** App de sistema (não dá para desinstalar, só desativar nas informações do app). */
    fun isSystem(ctx: Context, app: AppEntry): Boolean = runCatching {
        val info = ctx.getSystemService(LauncherApps::class.java).getApplicationInfo(app.component.packageName, 0, app.user)
        info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
    }.getOrDefault(false)

    /**
     * Pede ao Android para desinstalar (ele mostra a confirmação). App de sistema ou de outro
     * perfil (trabalho) abre as informações do app, onde o Android oferece o que for possível.
     * Devolve false quando caiu nas informações.
     */
    fun uninstall(ctx: Context, app: AppEntry): Boolean {
        if (app.user != Process.myUserHandle() || isSystem(ctx, app)) {
            appInfo(ctx, app)
            return false
        }
        val ok = runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}"))
                    .putExtra(Intent.EXTRA_RETURN_RESULT, false)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
        if (!ok) appInfo(ctx, app)
        return ok
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
