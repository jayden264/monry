package com.monry.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monry.app.AppContainer
import com.monry.app.MonryApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as MonryApp).container

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline build: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = build(container) as T
        }
    )
}

/** Emits the wall-clock time now and then every [periodMs]. */
fun clock(periodMs: Long): Flow<LocalDateTime> = flow {
    while (true) {
        emit(LocalDateTime.now())
        delay(periodMs)
    }
}
