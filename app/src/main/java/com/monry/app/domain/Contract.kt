package com.monry.app.domain

import java.security.MessageDigest
import java.time.LocalDate

enum class Party(val label: String) { A("甲方"), B("乙方") }

/** Renders an amount in minor units as formal Chinese capitals, e.g. 12345.67 -> 壹万贰仟叁佰肆拾伍元陆角柒分. */
object ChineseMoney {
    private const val D = "零壹贰叁肆伍陆柒捌玖"
    private val UNITS = arrayOf("仟", "佰", "拾", "")

    private fun below10000(n: Int): String {
        val ds = intArrayOf(n / 1000, n / 100 % 10, n / 10 % 10, n % 10)
        val sb = StringBuilder()
        var started = false
        var zero = false
        for (i in 0..3) {
            val d = ds[i]
            if (d == 0) {
                if (started) zero = true
            } else {
                if (zero) sb.append('零')
                sb.append(D[d]).append(UNITS[i])
                started = true
                zero = false
            }
        }
        return sb.toString()
    }

    private fun yuanText(n: Long): String {
        val groups = listOf(n / 100_000_000 to "亿", (n / 10_000) % 10_000 to "万", n % 10_000 to "")
        val sb = StringBuilder()
        var gap = false
        for ((v, unit) in groups) {
            if (v == 0L) {
                if (sb.isNotEmpty()) gap = true
            } else {
                if (sb.isNotEmpty() && (gap || v < 1000)) sb.append('零')
                sb.append(below10000(v.toInt())).append(unit)
                gap = false
            }
        }
        return sb.toString()
    }

    fun upper(minor: Long): String {
        require(minor >= 0)
        if (minor == 0L) return "零元整"
        val yuan = minor / 100
        val jiao = (minor / 10 % 10).toInt()
        val fen = (minor % 10).toInt()
        val sb = StringBuilder()
        if (yuan > 0) sb.append(yuanText(yuan)).append('元')
        if (jiao > 0) sb.append(D[jiao]).append('角') else if (fen > 0 && yuan > 0) sb.append('零')
        if (fen > 0) sb.append(D[fen]).append('分') else sb.append('整')
        return sb.toString()
    }
}

data class ContractTerms(
    val subject: String,
    val partyAName: String,
    val partyAId: String,
    val partyBName: String,
    val partyBId: String,
    val principal: Long,
    val rateBp: Int,
    val mode: InterestMode,
    val start: LocalDate,
    val termMonths: Int?,
    val payment: String,
    val extra: String
)

object ContractText {
    const val TITLE = "投资合作合同"
    private val ORDINALS = listOf("一", "二", "三", "四", "五", "六", "七")

    private fun term(months: Int) = if (months % 12 == 0) "${months / 12} 年" else "$months 个月"

    /** Body paragraphs in order. The same list feeds the screen, the PDF and the fingerprint. */
    fun paragraphs(t: ContractTerms): List<String> {
        val maturity = InvestmentCalculator.maturityDate(t.start, t.termMonths)
        val out = ArrayList<String>()
        out += "甲方（出资方）：${t.partyAName}　证件号：${t.partyAId}"
        out += "乙方（接受投资方）：${t.partyBName}　证件号：${t.partyBId}"
        out += "甲乙双方经平等协商，就甲方向乙方出资一事订立如下条款，共同遵守。"

        var n = 0
        fun clause(title: String, body: String) {
            out += "第${ORDINALS[n++]}条　$title\n$body"
        }
        clause("投资标的", "乙方接受甲方就「${t.subject}」的投资。")
        clause("投资金额", "甲方投资本金为人民币 ${Money.format(t.principal)} 元（大写：${ChineseMoney.upper(t.principal)}）。")

        val rate = if (t.rateBp == 0) "年利率为 0%，即本投资不约定收益。"
        else "年利率为 ${Percent.format(t.rateBp)}，计息方式为${t.mode.label}。${t.mode.explain}"
        val period = if (maturity != null) {
            val profit = InvestmentCalculator.profitMinor(
                listOf(Tranche(t.start, t.principal)), InvestTerms(t.rateBp, t.mode, maturity), maturity.atStartOfDay()
            )
            "自 ${t.start} 起算，投资期限 ${term(t.termMonths!!)}，至 $maturity 止，到期后不再计息。" +
                "按上述约定计算，到期收益为人民币 ${Money.format(profit)} 元（大写：${ChineseMoney.upper(profit)}）。"
        } else {
            "自 ${t.start} 起算，未约定固定期限，结束时间由双方另行协商。"
        }
        clause("收益", rate + period)
        clause("本金及收益的支付", t.payment)
        if (t.extra.isNotBlank()) clause("补充条款", t.extra.trim())
        clause(
            "其他",
            "本合同自甲乙双方均已签名之日起生效。本合同为电子文本，双方的手写签名图像为本人亲笔所签；" +
                "合同内容在签署完成时以 SHA-256 指纹固定，之后不可修改。"
        )
        return out
    }

    fun fullText(t: ContractTerms): String = TITLE + "\n" + paragraphs(t).joinToString("\n")
}

/** Tamper evidence: a SHA-256 over the text, both signature images and the signing time. */
object ContractSeal {
    fun fingerprint(text: String, sigA: ByteArray, sigB: ByteArray, signedAtEpochSecond: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(text.toByteArray(Charsets.UTF_8))
        md.update(0)
        md.update(sigA)
        md.update(0)
        md.update(sigB)
        md.update(0)
        md.update(signedAtEpochSecond.toString().toByteArray(Charsets.UTF_8))
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** "ab12cd34 ef56…" for reading aloud / comparing. */
    fun grouped(fp: String): String = fp.chunked(8).joinToString(" ")
}

object IdNumber {
    private val WEIGHTS = intArrayOf(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
    private const val CHECK = "10X98765432"

    /** Null when acceptable: a valid 18-digit resident ID, or a 5–20 character passport-style number. */
    fun check(raw: String): String? {
        val s = raw.trim().uppercase()
        if (s.isEmpty()) return "请输入证件号"
        if (Regex("^\\d{17}[\\dX]$").matches(s)) {
            val sum = (0 until 17).sumOf { (s[it] - '0') * WEIGHTS[it] }
            return if (CHECK[sum % 11] == s[17]) null else "身份证号校验未通过，请核对"
        }
        return if (Regex("^[A-Z0-9]{5,20}$").matches(s)) null else "证件号格式不正确"
    }
}

object ContractValidator {
    fun validate(t: ContractTerms): String? {
        for ((label, name) in listOf("甲方" to t.partyAName, "乙方" to t.partyBName)) {
            if (name.trim().length !in 2..30) return "请填写${label}姓名（2–30 个字）"
        }
        IdNumber.check(t.partyAId)?.let { return "甲方：$it" }
        IdNumber.check(t.partyBId)?.let { return "乙方：$it" }
        if (t.partyAName.trim() == t.partyBName.trim() && t.partyAId.trim().equals(t.partyBId.trim(), true)) {
            return "甲乙双方不能是同一个人"
        }
        if (t.payment.isBlank()) return "请填写收益支付方式"
        if (t.payment.length > 200) return "支付方式不能超过 200 字"
        if (t.extra.length > 1000) return "补充条款不能超过 1000 字"
        if (t.principal <= 0) return "本金必须大于 0"
        return null
    }
}
