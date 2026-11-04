package com.monry.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.monry.app.data.TransactionEntity
import com.monry.app.domain.Money
import com.monry.app.domain.TxSource
import com.monry.app.domain.TxType
import com.monry.app.ui.components.Hairline
import com.monry.app.ui.components.MText
import com.monry.app.ui.components.pressable
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import androidx.compose.ui.unit.dp

@Composable
fun TxRow(tx: TransactionEntity, accountName: String?, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth().pressable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val tag = when (tx.source) {
                    TxSource.RECURRING -> "  · 定时"
                    TxSource.INVESTMENT -> "  · 投资"
                    TxSource.MANUAL -> ""
                }
                MText(tx.category + tag, style = T.body)
                val detail = listOfNotNull(subtitle, accountName, tx.note.ifBlank { null }).joinToString(" · ")
                if (detail.isNotEmpty()) MText(detail, style = T.caption, color = M.c.inkSoft, maxLines = 1)
            }
            MText(
                (if (tx.type == TxType.INCOME) "+" else "-") + Money.format(tx.amount),
                style = T.amount, color = if (tx.type == TxType.INCOME) M.c.accent else M.c.ink
            )
        }
        Hairline()
    }
}
