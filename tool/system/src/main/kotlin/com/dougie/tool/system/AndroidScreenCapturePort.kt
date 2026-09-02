package com.dougie.tool.system

import android.content.Context
import android.content.Intent
import android.os.Build
import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.CapturedScreen
import com.dougie.core.tool.ScreenCapturePort
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object ScreenCaptureBridge {
    @Volatile
    var pending: CompletableFuture<CapturedScreen>? = null
}

class AndroidScreenCapturePort(
    context: Context,
    private val isForeground: () -> Boolean,
    private val onUsed: () -> Unit = {},
) : ScreenCapturePort {
    private val appContext = context.applicationContext

    override fun isAppForeground(): Boolean = isForeground()

    override fun hasProjectionConsent(): Boolean = ScreenCaptureConsentStore.hasToken()

    override suspend fun capture(): CapturedScreen {
        onUsed()
        val future = CompletableFuture<CapturedScreen>()
        ScreenCaptureBridge.pending = future
        val intent = Intent(appContext, ScreenCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
        return try {
            suspendCancellableCoroutine { cont ->
                future.whenComplete { frame, error -> resumeCapture(cont, frame, error) }
                cont.invokeOnCancellation {
                    future.cancel(true)
                    appContext.stopService(intent)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            throw e
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.TOOL_FAILED)
        } finally {
            if (ScreenCaptureBridge.pending === future) {
                ScreenCaptureBridge.pending = null
            }
        }
    }

    private fun resumeCapture(
        cont: CancellableContinuation<CapturedScreen>,
        captured: CapturedScreen?,
        error: Throwable?,
    ) {
        if (!cont.isActive) return
        when {
            error != null -> cont.resumeWithException(
                (error as? AgentException) ?: AgentException(UserFacingErrors.TOOL_FAILED),
            )
            captured != null -> cont.resume(captured)
            else -> cont.resumeWithException(AgentException(UserFacingErrors.TOOL_FAILED))
        }
    }
}
