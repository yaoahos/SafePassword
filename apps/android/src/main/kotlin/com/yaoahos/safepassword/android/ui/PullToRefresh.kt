package com.yaoahos.safepassword.android.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全局下拉刷新容器，替代原顶栏的「立即同步」按钮。
 *
 * 指示器用 Material3 默认的浅色圆底（surfaceContainerHigh），只把箭头 / 转圈
 * 换成主题 primary：圆底跟主题「面」走，浅色页面上不抢眼。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val pullState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    // 兜底收圈：同步很快结束时 isRefreshing 的 true→false 全在同一帧，
    // Compose 观测不到 true，M3 那条「刷新结束 → 收起」动画永远不触发，
    // 指示器就永久停在阈值位置（圆圈箭头挂在顶部）。这里不依赖观测到 isRefreshing
    // 变化，而是自己发起的刷新结束后等阈值动画走完再收回残余位置。
    val refreshingNow by rememberUpdatedState(isRefreshing)
    val startRefresh: () -> Unit = {
        onRefresh()
        scope.launch {
            repeat(1000) { // 最多约两分钟
                delay(120L)
                if (refreshingNow || pullState.isAnimating) return@repeat
                settlePullIndicator(pullState)
                return@launch
            }
        }
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = startRefresh,
        state = pullState,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                color = MaterialTheme.colorScheme.primary
            )
        },
        content = content
    )
}

/** 把下拉指示器彻底收回起点：先跑 M3 收起动画，再盯住残余位置用 snapTo 写死到 0。 */
@OptIn(ExperimentalMaterial3Api::class)
private suspend fun settlePullIndicator(state: PullToRefreshState) {
    if (state.distanceFraction > 0f) runCatching { state.animateToHidden() }
    repeat(3) {
        if (state.distanceFraction <= 0f) return
        delay(60L)
        if (state.distanceFraction > 0f && !state.isAnimating) runCatching { state.snapTo(0f) }
    }
}
