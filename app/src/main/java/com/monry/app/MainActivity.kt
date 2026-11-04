package com.monry.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.monry.app.ui.components.LocalToast
import com.monry.app.ui.components.MDialog
import com.monry.app.update.UpdateInfo
import com.monry.app.update.Updater
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.monry.app.ui.components.MText
import com.monry.app.ui.components.pressable
import com.monry.app.ui.screens.AddTxScreen
import com.monry.app.ui.screens.HomeScreen
import com.monry.app.ui.screens.InvestDetailScreen
import com.monry.app.ui.screens.InvestNewScreen
import com.monry.app.ui.screens.InvestScreen
import com.monry.app.ui.screens.LedgerScreen
import com.monry.app.ui.screens.OnboardingScreen
import com.monry.app.ui.screens.RuleEditScreen
import com.monry.app.ui.screens.RuleHistoryScreen
import com.monry.app.ui.screens.RulesScreen
import com.monry.app.ui.screens.SettingsScreen
import com.monry.app.ui.screens.StatsScreen
import com.monry.app.ui.theme.LocalReduceMotion
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.MonryTheme
import com.monry.app.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private enum class Tab(val route: String, val label: String) {
    Home("home", "总览"), Add("add", "记账"), Rules("rules", "定时"), Invest("invest", "投资"), Stats("stats", "统计")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MonryApp).container

        // Catch up on missed rule executions at every start/resume and then once a minute while visible.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    runCatching { container.processor.run(LocalDateTime.now()) }
                    delay(60_000)
                }
            }
        }
        setContent { MonryTheme { Root(container) } }
    }
}

@Composable
private fun Root(container: AppContainer) {
    var onboarded by remember { mutableStateOf(container.onboarded) }
    var toastText by remember { mutableStateOf<String?>(null) }
    var toastSeq by remember { mutableStateOf(0) }
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }

    // Silent check at every cold start; failures (offline, rate limit) are ignored.
    LaunchedEffect(Unit) { update = runCatching { Updater.check(context) }.getOrNull() }

    LaunchedEffect(toastSeq) {
        if (toastText != null) {
            delay(2_000)
            toastText = null
        }
    }

    CompositionLocalProvider(LocalToast provides { msg -> toastText = msg; toastSeq++ }) {
        Box(Modifier.fillMaxSize().background(M.c.bg)) {
            if (!onboarded) {
                OnboardingScreen(onDone = { onboarded = true })
            } else {
                AppNav()
            }
            update?.let { u ->
                MDialog(
                    title = "发现新版本 ${u.version}",
                    onDismiss = { if (progress == null) update = null },
                    confirmText = if (progress == null) "立即更新" else "下载中…",
                    onConfirm = {
                        if (progress == null) scope.launch {
                            try {
                                progress = 0f
                                val apk = Updater.download(context, u) { progress = it }
                                Updater.install(context, apk)
                            } catch (e: Exception) {
                                toastText = "下载失败，请稍后重试"; toastSeq++
                            } finally {
                                progress = null
                                update = null
                            }
                        }
                    },
                    dismissText = "以后再说"
                ) {
                    MText(u.notes.ifBlank { "有新版本可用。" }, style = T.body, color = M.c.inkSoft)
                    progress?.let { MText("已下载 ${(it * 100).toInt()}%", Modifier.padding(top = 12.dp), T.caption, M.c.inkSoft) }
                }
            }
            AnimatedVisibility(
                visible = toastText != null,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
                enter = if (reduce) fadeIn(tween(0)) else fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut()
            ) {
                Box(Modifier.clip(RoundedCornerShape(22.dp)).background(M.c.ink).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    MText(toastText ?: "", style = T.label, color = M.c.bg)
                }
            }
        }
    }
}

private const val PUSH_MS = 380

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val reduce = LocalReduceMotion.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTab = Tab.entries.any { it.route == route }

    fun goTab(t: Tab) = nav.navigate(t.route) {
        popUpTo(Tab.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    val fadeIn: (NavBackStackEntry) -> EnterTransition = { if (reduce) EnterTransition.None else fadeIn(tween(PUSH_MS)) }
    val fadeOut: (NavBackStackEntry) -> ExitTransition = { if (reduce) ExitTransition.None else fadeOut(tween(PUSH_MS / 2)) }
    // Detail pages slide in from the right while the page underneath dims back slightly.
    val pushIn: (NavBackStackEntry) -> EnterTransition = {
        if (reduce) EnterTransition.None else slideInHorizontally(tween(PUSH_MS)) { it / 4 } + fadeIn(tween(PUSH_MS))
    }
    val popOut: (NavBackStackEntry) -> ExitTransition = {
        if (reduce) ExitTransition.None else slideOutHorizontally(tween(PUSH_MS)) { it / 4 } + fadeOut(tween(PUSH_MS))
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(
                nav, startDestination = Tab.Home.route, Modifier.fillMaxSize(),
                enterTransition = { if (isTabRoute(targetState.destination.route)) fadeIn(this.targetState) else pushIn(this.targetState) },
                exitTransition = { fadeOut(this.initialState) },
                popEnterTransition = { fadeIn(this.targetState) },
                popExitTransition = { popOut(this.initialState) }
            ) {
                composable(Tab.Home.route) {
                    HomeScreen(
                        onSettings = { nav.navigate("settings") }, onLedger = { nav.navigate("ledger") },
                        onEditTx = { nav.navigate("tx/$it") }, onInvest = { goTab(Tab.Invest) }
                    )
                }
                composable(Tab.Add.route) { AddTxScreen(-1L, onBack = {}, onSaved = { goTab(Tab.Home) }) }
                composable(Tab.Rules.route) {
                    RulesScreen(onNew = { nav.navigate("rule/-1") }, onEdit = { nav.navigate("rule/$it") }, onHistory = { nav.navigate("rule-history/$it") })
                }
                composable(Tab.Invest.route) { InvestScreen(onNew = { nav.navigate("invest-new") }, onOpen = { nav.navigate("invest/$it") }) }
                composable(Tab.Stats.route) { StatsScreen() }
                composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                composable("ledger") { LedgerScreen(onBack = { nav.popBackStack() }, onEditTx = { nav.navigate("tx/$it") }) }
                composable("tx/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                    AddTxScreen(it.arguments!!.getLong("id"), onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
                }
                composable("rule/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                    RuleEditScreen(it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
                }
                composable("rule-history/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                    RuleHistoryScreen(it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
                }
                composable("invest-new") {
                    InvestNewScreen(onBack = { nav.popBackStack() }, onCreated = { id ->
                        nav.navigate("invest/$id") { popUpTo("invest-new") { inclusive = true } }
                    })
                }
                composable("invest/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                    InvestDetailScreen(it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
                }
            }
        }
        AnimatedVisibility(
            visible = isTab,
            enter = if (reduce) EnterTransition.None else slideInVertically { it },
            exit = if (reduce) ExitTransition.None else slideOutVertically { it }
        ) { BottomBar(route, ::goTab) }
    }
}

private fun isTabRoute(r: String?) = Tab.entries.any { it.route == r }

@Composable
private fun BottomBar(current: String?, onTab: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(M.c.bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(M.c.line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp)) {
            Tab.entries.forEach { t ->
                val on = t.route == current
                Box(Modifier.weight(1f).fillMaxSize().pressable { onTab(t) }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MText(t.label, style = T.label, color = if (on) M.c.ink else M.c.inkFaint)
                        Box(Modifier.padding(top = 5.dp).size(4.dp).clip(RoundedCornerShape(2.dp)).background(if (on) M.c.ink else androidx.compose.ui.graphics.Color.Transparent))
                    }
                }
            }
        }
    }
}
