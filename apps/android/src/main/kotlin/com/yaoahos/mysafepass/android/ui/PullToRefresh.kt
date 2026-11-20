package com.yaoahos.mysafepass.android.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
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
import androidx.compose.runtime.remember
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

/**
 * 让整块内容区都能触发下拉刷新（收藏页条目少/为空时尤其关键）。
 *
 * PullToRefreshBox 只是嵌套滚动的接收方：必须由子级滚动容器把「没被消费的位移」
 * 往上抛，它才动。收藏页条目少甚至为空时走的是不可滚动的 EmptyState，或列表只占
 * 上方一小截，下半屏空白不属于任何滚动容器——从那里下拉完全没反应，而全部页条目
 * 多、列表填满屏幕，下拉就正常。同一个刷新容器在两页表现不一致，根源就在这里。
 *
 * 这里补一个滚动范围为 0 的可滚动节点：它自己不滚，只把位移原样往上传，于是标题栏、
 * 空白区、列表上任意位置下拉都能刷新；有内容可滚时内层先消费，剩下的一层再往上抛，
 * 也不会和列表滚动打架。
 */
@Composable
fun Modifier.pullRefreshArea(): Modifier {
    // consumeScrollDelta 恒定返回 0：这条滚动链一点都不吃位移，只负责往上抛。
    // 不能用 rememberScrollState()——它最大滚动值是无穷大，会把位移全吞掉自己「滚」进虚空。
    val passThrough = rememberScrollableState { 0f }
    return scrollable(state = passThrough, orientation = Orientation.Vertical)
}
