package com.dougie.app

internal enum class AppRoute { Chat, Settings, Memory, Permissions, History, Debug, OpenApps }

internal data class AppNavState(
    val route: AppRoute,
    val previewOpen: Boolean,
)

/** Null means do not intercept: Chat with no preview leaves the Activity as today. */
internal fun consumeBack(state: AppNavState): AppNavState? {
    if (state.route == AppRoute.Chat && state.previewOpen) {
        return state.copy(previewOpen = false)
    }
    return when (state.route) {
        AppRoute.OpenApps, AppRoute.Debug -> AppNavState(AppRoute.Settings, previewOpen = false)
        AppRoute.Settings, AppRoute.Permissions, AppRoute.Memory, AppRoute.History ->
            AppNavState(AppRoute.Chat, previewOpen = false)
        AppRoute.Chat -> null
    }
}
