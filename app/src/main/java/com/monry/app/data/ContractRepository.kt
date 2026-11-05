package com.monry.app.data

import androidx.room.withTransaction
import com.monry.app.domain.ContractSeal
import com.monry.app.domain.ContractTerms
import com.monry.app.domain.ContractText
import com.monry.app.domain.ContractValidator
import com.monry.app.domain.Party
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

fun ContractEntity.terms() = ContractTerms(
    subject, partyAName, partyAId, partyBName, partyBId, principal, rateBp, mode,
    LocalDate.ofEpochDay(startEpochDay), termMonths, payment, extra
)

/** True when the stored fingerprint still matches the text and signatures, i.e. nothing was altered. */
fun ContractEntity.verify(): Boolean {
    val fp = fingerprint ?: return false
    val a = sigA ?: return false
    val b = sigB ?: return false
    val at = signedAt ?: return false
    return ContractSeal.fingerprint(ContractText.fullText(terms()), a, b, at) == fp
}

class ContractRepository(private val db: MonryDatabase) {
    private val dao = db.contracts()

    fun forInvestment(id: Long): Flow<List<ContractEntity>> = dao.observeFor(id)
    fun observe(id: Long): Flow<ContractEntity?> = dao.observe(id)

    suspend fun create(investmentId: Long, t: ContractTerms): Long {
        val t2 = t.copy(
            partyAName = t.partyAName.trim(), partyAId = t.partyAId.trim().uppercase(),
            partyBName = t.partyBName.trim(), partyBId = t.partyBId.trim().uppercase(),
            payment = t.payment.trim(), extra = t.extra.trim()
        )
        ContractValidator.validate(t2)?.let { throw UserError(it) }
        return dao.insert(
            ContractEntity(
                investmentId = investmentId, subject = t2.subject, partyAName = t2.partyAName, partyAId = t2.partyAId,
                partyBName = t2.partyBName, partyBId = t2.partyBId, principal = t2.principal, rateBp = t2.rateBp,
                mode = t2.mode, startEpochDay = t2.start.toEpochDay(), termMonths = t2.termMonths,
                payment = t2.payment, extra = t2.extra, createdAt = System.currentTimeMillis()
            )
        )
    }

    /** Adds [party]'s signature (replacing an earlier one). The second signature seals the contract. */
    suspend fun sign(id: Long, party: Party, png: ByteArray, nowEpochSecond: Long) = db.withTransaction {
        val c = dao.get(id) ?: throw UserError("合同不存在")
        if (c.sealed) throw UserError("合同已签署完成，不能再修改")
        if (png.isEmpty() || png.size > 500_000) throw UserError("签名图像无效，请重新签名")
        var next = if (party == Party.A) c.copy(sigA = png) else c.copy(sigB = png)
        val a = next.sigA
        val b = next.sigB
        if (a != null && b != null) {
            val fp = ContractSeal.fingerprint(ContractText.fullText(next.terms()), a, b, nowEpochSecond)
            next = next.copy(signedAt = nowEpochSecond, fingerprint = fp)
        }
        dao.update(next)
    }

    suspend fun clearSignatures(id: Long) = db.withTransaction {
        val c = dao.get(id) ?: return@withTransaction
        if (c.sealed) throw UserError("合同已签署完成，不能再修改")
        dao.update(c.copy(sigA = null, sigB = null))
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
