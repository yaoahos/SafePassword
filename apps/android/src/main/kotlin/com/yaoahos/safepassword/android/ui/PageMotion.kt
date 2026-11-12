package com.yaoahos.safepassword.android.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * 全应用统一的页面转场参数，与 MyFileBox 的 PageMotion 保持一致：
 * 前进时新页整屏从右侧推入、旧页只让出三分之一；返回时严格反过来。
 * 只有水平位移，不做缩放、不做淡入淡出，避免文字在动画里发虚。
 */
object PageMotion {
    /** 前进与返回都是 300ms。 */
    const val DURATION_MS = 300
    val Easing = FastOutSlowInEasing

    /** 左边缘滑动返回的可起手宽度（dp）。 */
    const val EDGE_DP = 20

    /** 松手提交阈值：位移超过 30% 页宽才真正返回。 */
    const val COMMIT_RATIO = 0.30f
}

/**
 * 页面转场规格。
 *
 * | 方向 | 进入 | 退出 |
 * |------|------|------|
 * | 前进 | slideInHorizontally { it }      | slideOutHorizontally { -it / 3 } |
 * | 返回 | slideInHorizontally { -it / 3 } | slideOutHorizontally { it }      |
 *
 * zIndex：前进时新页盖住旧页；返回时当前页盖住即将回来的上一页，
 * 否则返回动画里「目标页」会压在正在退出的当前页上面，看起来是倒放的。
 */
fun pageTransition(forward: Boolean): ContentTransform {
    val spec = tween<IntOffset>(PageMotion.DURATION_MS, easing = PageMotion.Easing)
    val enter = if (forward) {
        slideInHorizontally(animationSpec = spec) { it }
    } else {
        slideInHorizontally(animationSpec = spec) { -it / 3 }
    }
    val exit = if (forward) {
        slideOutHorizontally(animationSpec = spec) { -it / 3 }
    } else {
        slideOutHorizontally(animationSpec = spec) { it }
    }
    return (enter togetherWith exit).apply {
        targetContentZIndex = if (forward) 1f else -1f
    }
}
