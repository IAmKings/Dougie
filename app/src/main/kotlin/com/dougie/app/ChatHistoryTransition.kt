package com.dougie.app

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

internal const val CHAT_HISTORY_TRANSITION_MS = 300

internal fun usesChatHistorySharedBounds(
    taskId: String,
    sharedTaskId: String?,
    reduceMotion: Boolean,
): Boolean = !reduceMotion && sharedTaskId == taskId

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ChatHistoryTransition(
    route: AppRoute,
    sharedTaskId: String?,
    chat: @Composable (sharedBoundsFor: @Composable (String) -> Modifier) -> Unit,
    history: @Composable (sharedBoundsFor: @Composable (String) -> Modifier) -> Unit,
) {
    val reduceMotion = Settings.Global.getFloat(
        LocalContext.current.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        val sharedScope = this
        AnimatedContent(
            targetState = route,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(
                        animationSpec = tween(
                            durationMillis = CHAT_HISTORY_TRANSITION_MS,
                            easing = FastOutSlowInEasing,
                        ),
                    ),
                    initialContentExit = fadeOut(
                        animationSpec = tween(
                            durationMillis = CHAT_HISTORY_TRANSITION_MS,
                            easing = FastOutSlowInEasing,
                        ),
                    ),
                    sizeTransform = null,
                )
            },
            label = "chatHistory",
        ) { target ->
            val sharedBoundsFor: @Composable (String) -> Modifier = { taskId ->
                sharedScope.chatHistorySharedBounds(
                    taskId = taskId,
                    sharedTaskId = sharedTaskId,
                    reduceMotion = reduceMotion,
                    animatedVisibilityScope = this,
                )
            }
            when (target) {
                AppRoute.Chat -> chat(sharedBoundsFor)
                AppRoute.History -> history(sharedBoundsFor)
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.chatHistorySharedBounds(
    taskId: String,
    sharedTaskId: String?,
    reduceMotion: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier {
    val state = rememberSharedContentState(key = taskId)
    if (!usesChatHistorySharedBounds(taskId, sharedTaskId, reduceMotion)) {
        return Modifier
    }
    val fadeSpec = tween<Float>(
        durationMillis = CHAT_HISTORY_TRANSITION_MS,
        easing = FastOutSlowInEasing,
    )
    return Modifier.sharedBounds(
        sharedContentState = state,
        animatedVisibilityScope = animatedVisibilityScope,
        enter = fadeIn(animationSpec = fadeSpec),
        exit = fadeOut(animationSpec = fadeSpec),
        boundsTransform = { _, _ ->
            tween(durationMillis = CHAT_HISTORY_TRANSITION_MS, easing = FastOutSlowInEasing)
        },
    )
}
