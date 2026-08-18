package com.dougie.feature.settings

import com.dougie.core.model.AgentException
import com.dougie.core.tool.IntentModelLayout
import com.dougie.core.tool.ModelInstaller
import com.dougie.core.tool.OfflineModelOffer
import com.dougie.core.tool.isConfigured
import com.dougie.core.tool.isInstalled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

data class OfflineModelRowUi(
    val id: String,
    val title: String,
    val sizeLabel: String,
    val configured: Boolean,
    val installed: Boolean,
    val downloading: Boolean = false,
    val downloaded: Long = 0L,
    val total: Long = -1L,
    val error: String? = null,
    val willReplace: Boolean = false,
)

data class OfflineModelsUi(
    val rows: List<OfflineModelRowUi>,
    val pendingConfirmId: String? = null,
) {
    val pending: OfflineModelRowUi?
        get() = pendingConfirmId?.let { id -> rows.find { it.id == id } }
}

class OfflineModelDownloads(
    private val installer: ModelInstaller,
    private val destRoot: File,
    private val offers: List<OfflineModelOffer>,
    private val scope: CoroutineScope,
) {
    private val jobs = mutableMapOf<String, Job>()
    private val _ui = MutableStateFlow(
        OfflineModelsUi(rows = offers.map { it.toRow() }),
    )
    val ui: StateFlow<OfflineModelsUi> = _ui.asStateFlow()

    fun request(id: String) {
        val offer = offers.find { it.id == id } ?: return
        val row = _ui.value.rows.find { it.id == id } ?: return
        if (row.downloading || row.installed) return
        if (isDirBusy(offer.pack.relativeDir)) {
            patch(id) { it.copy(error = DIR_BUSY) }
            return
        }
        if (!offer.isConfigured()) {
            patch(id) { it.copy(error = UNCONFIGURED) }
            return
        }
        patch(id) { it.copy(error = null) }
        _ui.update { it.copy(pendingConfirmId = id) }
    }

    fun dismissConfirm() {
        _ui.update { it.copy(pendingConfirmId = null) }
    }

    fun confirm() {
        val id = _ui.value.pendingConfirmId ?: return
        val offer = offers.find { it.id == id } ?: return
        _ui.update { it.copy(pendingConfirmId = null) }
        if (!offer.isConfigured()) {
            patch(id) { it.copy(error = UNCONFIGURED) }
            return
        }
        if (jobs.containsKey(id)) return
        if (isDirBusy(offer.pack.relativeDir)) {
            patch(id) { it.copy(error = DIR_BUSY) }
            return
        }
        jobs[id] = scope.launch {
            patch(id) { it.copy(downloading = true, error = null, downloaded = 0L, total = -1L) }
            var failure: String? = null
            try {
                installer.install(
                    pack = offer.pack,
                    destRoot = destRoot,
                    userConfirmed = true,
                    onProgress = { downloaded, total ->
                        patch(id) { row -> row.copy(downloaded = downloaded, total = total) }
                    },
                )
                if (offer.id == IntentModelLayout.Q4_ID || offer.id == IntentModelLayout.Q8_ID) {
                    IntentModelLayout.writeQuantId(
                        File(destRoot, offer.pack.relativeDir),
                        offer.id,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AgentException) {
                failure = e.userMessage
            } finally {
                jobs.remove(id)
                refreshDir(offer.pack.relativeDir, failedId = id, failure = failure)
            }
        }
    }

    fun cancel(id: String) {
        jobs[id]?.cancel()
    }

    private fun OfflineModelOffer.toRow() = OfflineModelRowUi(
        id = id,
        title = title,
        sizeLabel = sizeLabel,
        configured = isConfigured(),
        installed = isInstalled(destRoot),
        willReplace = willReplace(this),
    )

    private fun willReplace(offer: OfflineModelOffer): Boolean =
        offers.any { other ->
            other.id != offer.id &&
                other.pack.relativeDir == offer.pack.relativeDir &&
                other.isInstalled(destRoot)
        }

    private fun isDirBusy(relativeDir: String): Boolean =
        jobs.keys.any { jobId -> offers.find { it.id == jobId }?.pack?.relativeDir == relativeDir }

    private fun refreshDir(relativeDir: String, failedId: String, failure: String?) {
        _ui.update { state ->
            state.copy(
                rows = state.rows.map { row ->
                    val offer = offers.find { it.id == row.id } ?: return@map row
                    if (offer.pack.relativeDir != relativeDir) return@map row
                    row.copy(
                        downloading = false,
                        installed = offer.isInstalled(destRoot),
                        willReplace = willReplace(offer),
                        downloaded = 0L,
                        total = -1L,
                        error = if (row.id == failedId) failure else row.error,
                    )
                },
            )
        }
    }

    private fun patch(id: String, transform: (OfflineModelRowUi) -> OfflineModelRowUi) {
        _ui.update { state ->
            state.copy(rows = state.rows.map { row -> if (row.id == id) transform(row) else row })
        }
    }

    companion object {
        const val UNCONFIGURED = "尚未配置下载地址"
        const val DIR_BUSY = "正在下载另一份意图模型，请稍候"
    }
}
