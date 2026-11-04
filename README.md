# Monry — 个人记账（Android 原生，Kotlin + Jetpack Compose）

## 运行
1. 安装 JDK 17 与 Android Studio（含 Android SDK 35）。
2. Android Studio 打开本目录，等待 Gradle 同步，运行 `app`；或命令行 `./gradlew :app:installDebug`。
3. 单元测试：`./gradlew :app:testDebugUnitTest`

## 结构
- `domain/` 纯 Kotlin：`Money`（分为单位的 Long）、`InvestmentCalculator`（BigDecimal 收益）、`Schedule`/`RecurringProcessor`（定时与补记）、`StatsCalculator`。
- `data/` Room 实体、DAO、仓库；`AppContainer` 手写依赖容器 + WorkManager。
- `ui/` Compose 主题、动效组件（`Motion.kt`）、各页面。

## 使用
- 首次启动：录入现金/银行卡/电子钱包金额；之后在 设置 中改名、校准余额、增删账户。
- 记账：底部「记账」；首页「全部」进入按月流水；统计页看日/月/年汇总与分类占比。
- 定时：「定时」页新建规则（单次/每日/每周/每月/每年），可编辑、暂停/恢复、删除、查看执行记录。
- 投资：「投资」页新增项目，选单利/复利；详情页有实时收益、趋势图（拖动读值）、追加、改条款、赎回到账户。总资产 = 账户余额 + 进行中投资的当前价值。

## 关键设计
- 防重复补记：`rule_executions` 对 (规则, 版本, 第 n 次) 加唯一索引，执行与记账在同一事务；启动、前台每分钟、WorkManager 每 15 分钟都会运行，重复运行不会重复入账。
- 按“第 n 次”由起点推算（而非上次+1 个月），所以 31 日 → 2 月 28/29、3 月 31；2 月 29 日年度规则在平年落在 2 月 28。
- 时间使用本地墙钟，时区变化不会产生或丢失执行。
- 计息：按整周年 + 当年剩余秒数折算，闰年精确；单利 P(1+r·t)，复利 P(1+r)^n(1+r·f)。到期后停止计息。
- 减少动画：读取系统 animator scale = 0，关闭滚动数字、倾斜、入场与转场动画。

## 已知限制
- **本工程在编写环境中未编译、未运行测试**（该环境没有 JDK/Android SDK），首次同步可能需要修少量编译错误。
- 单币种（¥），无导出/备份；编辑已有规则的执行时间会开启新“版本”，旧执行记录保留。
- 后台补记依赖系统调度（WorkManager，最短 15 分钟，省电策略可能延后）；最迟在下次打开应用时补齐。
- 动效帧率未在真机上实测。
