package com.monry.app.contract

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.monry.app.data.ContractEntity
import com.monry.app.data.terms
import com.monry.app.domain.ContractSeal
import com.monry.app.domain.ContractText
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Renders a sealed contract to an A4 PDF using the system CJK fonts. */
object ContractPdf {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48

    fun write(context: Context, c: ContractEntity): File {
        val fingerprint = requireNotNull(c.fingerprint) { "contract is not sealed" }
        val sigA = requireNotNull(c.sigA)
        val sigB = requireNotNull(c.sigB)
        val signedAt = LocalDateTime.ofEpochSecond(requireNotNull(c.signedAt), 0, ZoneOffset.UTC)
        val width = PAGE_W - 2 * MARGIN

        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = Color.BLACK }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY }
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f; color = Color.BLACK; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            page = p
            canvas = p.canvas
            y = MARGIN.toFloat()
        }

        fun text(s: String, paint: TextPaint, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, gap: Float = 12f) {
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, width)
                .setAlignment(align).setLineSpacing(4f, 1f).build()
            if (y + layout.height > PAGE_H - MARGIN) newPage()
            canvas!!.save()
            canvas!!.translate(MARGIN.toFloat(), y)
            layout.draw(canvas!!)
            canvas!!.restore()
            y += layout.height + gap
        }

        newPage()
        text(ContractText.TITLE, title, Layout.Alignment.ALIGN_CENTER, gap = 20f)
        ContractText.paragraphs(c.terms()).forEach { text(it, body) }

        // Signature block, kept together on one page.
        if (y + 170 > PAGE_H - MARGIN) newPage()
        y += 12f
        val boxW = width / 2 - 12
        listOf(Triple("甲方：" + c.partyAName, sigA, 0), Triple("乙方：" + c.partyBName, sigB, 1)).forEach { (label, png, i) ->
            val x = MARGIN + i * (boxW + 24).toFloat()
            canvas!!.drawText(label, x, y + 12f, body)
            val bmp = BitmapFactory.decodeByteArray(png, 0, png.size)
            if (bmp != null) {
                val scale = minOf(boxW / bmp.width.toFloat(), 80f / bmp.height.toFloat())
                val w = (bmp.width * scale).toInt().coerceAtLeast(1)
                val h = (bmp.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
                canvas!!.drawBitmap(scaled, x, y + 20f, null)
            }
        }
        y += 112f
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }
        canvas!!.drawLine(MARGIN.toFloat(), y, (PAGE_W - MARGIN).toFloat(), y, line)
        y += 14f
        text("签署时间：${signedAt.toLocalDate()} ${"%02d:%02d".format(signedAt.hour, signedAt.minute)}", small, gap = 4f)
        text("SHA-256 指纹：" + ContractSeal.grouped(fingerprint), small, gap = 4f)
        text("指纹由合同全文、双方签名图像与签署时间共同计算得出，任何改动都会使其不一致。本文件由 Monry 在本机生成。", small, gap = 0f)

        page?.let { doc.finishPage(it) }
        val dir = File(context.cacheDir, "contracts").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "contract-${c.id}.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "分享合同").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
