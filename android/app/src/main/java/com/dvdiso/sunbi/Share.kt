package com.dvdiso.sunbi

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object Share {
    /** Número no formato do WhatsApp (DDI + DDD + número, só dígitos). Sem DDI, assume Brasil (55). */
    fun waNumber(phone: String): String? {
        var d = phone.filter { it.isDigit() }.trimStart('0')
        if (d.length in 10..11) d = "55$d"
        return d.takeIf { it.length >= 12 }
    }

    /**
     * Manda o PDF pelo WhatsApp (ou WhatsApp Business). Com [phone], já abre a conversa dessa
     * pessoa. Sem WhatsApp, abre o menu de compartilhar. Devolve true se abriu o WhatsApp.
     */
    fun pdfToWhatsApp(ctx: Context, file: File, phone: String?, message: String): Boolean {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val base = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, message)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val jid = phone?.let { waNumber(it) }?.let { "$it@s.whatsapp.net" }
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                ctx.startActivity(Intent(base).setPackage(pkg).apply { if (jid != null) putExtra("jid", jid) })
                return true
            } catch (_: ActivityNotFoundException) {
            }
        }
        other(ctx, file, message)
        return false
    }

    fun other(ctx: Context, file: File, message: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val i = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, message)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Enviar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
