package com.monry.app.domain

enum class TxType(val label: String) {
    INCOME("收入"),
    EXPENSE("支出");

    val sign: Int get() = if (this == INCOME) 1 else -1
}

/** Where a ledger entry came from. Only MANUAL entries are editable by the user. */
enum class TxSource { MANUAL, RECURRING, INVESTMENT }

enum class AccountType(val label: String) {
    CASH("现金"),
    BANK("银行卡"),
    EWALLET("电子钱包"),
    OTHER("其他")
}

enum class Recurrence(val label: String) {
    ONCE("单次"),
    DAILY("每日"),
    WEEKLY("每周"),
    MONTHLY("每月"),
    YEARLY("每年")
}

enum class RuleStatus(val label: String) {
    ACTIVE("运行中"),
    PAUSED("已暂停"),
    COMPLETED("已完成")
}

enum class InterestMode(val label: String, val explain: String) {
    SIMPLE("单利", "利息 = 本金 × 年利率 × 持有年数，利息不再生息。不满一年的部分按实际经过的时间折算。"),
    COMPOUND("复利", "每满一年，上一年的收益并入本金继续计息；不满一年的部分按剩余时间以单利折算。")
}

enum class InvestStatus { ACTIVE, ENDED }

object Categories {
    val expense = listOf("餐饮", "交通", "购物", "居家", "娱乐", "医疗", "学习", "人情", "其他")
    val income = listOf("工资", "奖金", "理财", "兼职", "红包", "其他")
    const val INVEST_OUT = "投资"
    const val INVEST_BACK = "投资赎回"

    fun of(type: TxType) = if (type == TxType.EXPENSE) expense else income
}
