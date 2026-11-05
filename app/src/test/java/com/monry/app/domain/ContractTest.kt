package com.monry.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ContractTest {
    @Test fun chineseCapitals() {
        assertEquals("零元整", ChineseMoney.upper(0))
        assertEquals("壹元整", ChineseMoney.upper(100))
        assertEquals("壹元伍角整", ChineseMoney.upper(150))
        assertEquals("壹元零伍分", ChineseMoney.upper(105))
        assertEquals("伍分", ChineseMoney.upper(5))
        assertEquals("壹万贰仟叁佰肆拾伍元陆角柒分", ChineseMoney.upper(1_234_567))
        assertEquals("壹万零伍元整", ChineseMoney.upper(1_000_500))
        assertEquals("壹拾万元整", ChineseMoney.upper(10_000_000))
        assertEquals("壹佰万元整", ChineseMoney.upper(100_000_000))
        assertEquals("壹亿元整", ChineseMoney.upper(10_000_000_000))
        assertEquals("壹亿零壹元整", ChineseMoney.upper(10_000_000_100))
        assertEquals("壹万零壹拾元整", ChineseMoney.upper(1_001_000))
    }

    @Test fun idNumbers() {
        assertNull(IdNumber.check("11010519491231002X"))
        assertNull(IdNumber.check("11010519491231002x"))
        assertNotNull(IdNumber.check("110105194912310021"))
        assertNotNull(IdNumber.check(""))
        assertNull(IdNumber.check("E12345678"))
        assertNotNull(IdNumber.check("12 34"))
    }

    private val terms = ContractTerms(
        "某某公司", "张三", "11010519491231002X", "李四", "E12345678", 1_000_000, 500,
        InterestMode.SIMPLE, LocalDate.of(2025, 1, 1), 12, "到期一次性支付本金及全部收益", ""
    )

    @Test fun textContainsAgreedFigures() {
        val text = ContractText.fullText(terms)
        assertTrue(text.contains("10,000.00 元（大写：壹万元整）"))
        assertTrue(text.contains("5.00%"))
        assertTrue(text.contains("至 2026-01-01 止"))
        assertTrue(text.contains("到期收益为人民币 500.00 元（大写：伍佰元整）"))
        assertFalse(text.contains("补充条款"))
        assertTrue(ContractText.fullText(terms.copy(extra = "逾期按日万分之五")).contains("第五条　补充条款"))
    }

    @Test fun openEndedAndZeroRate() {
        assertTrue(ContractText.fullText(terms.copy(termMonths = null)).contains("未约定固定期限"))
        assertTrue(ContractText.fullText(terms.copy(rateBp = 0)).contains("年利率为 0%"))
    }

    @Test fun textIsDeterministic() = assertEquals(ContractText.fullText(terms), ContractText.fullText(terms.copy()))

    @Test fun sealDetectsAnyChange() {
        val text = ContractText.fullText(terms)
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(4, 5, 6)
        val fp = ContractSeal.fingerprint(text, a, b, 1000)
        assertEquals(64, fp.length)
        assertEquals(fp, ContractSeal.fingerprint(text, a.copyOf(), b.copyOf(), 1000))
        assertNotEquals(fp, ContractSeal.fingerprint(text.replace("500", "600"), a, b, 1000))
        assertNotEquals(fp, ContractSeal.fingerprint(text, byteArrayOf(1, 2, 4), b, 1000))
        assertNotEquals(fp, ContractSeal.fingerprint(text, a, b, 1001))
        assertNotEquals(fp, ContractSeal.fingerprint(text, b, a, 1000))
    }

    @Test fun validation() {
        assertNull(ContractValidator.validate(terms))
        assertNotNull(ContractValidator.validate(terms.copy(partyBName = "")))
        assertNotNull(ContractValidator.validate(terms.copy(partyBId = "110105194912310021")))
        assertNotNull(ContractValidator.validate(terms.copy(partyBName = "张三", partyBId = "11010519491231002X")))
        assertNotNull(ContractValidator.validate(terms.copy(payment = " ")))
        assertNotNull(ContractValidator.validate(terms.copy(extra = "字".repeat(1001))))
    }
}
