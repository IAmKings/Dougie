package com.dougie.feature.settings

import com.dougie.core.model.AgentException
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
        jobs[id] = scope.launch {
            patch(id) { it.copy(downloading = true, error = null, downloaded = 0L, total = -1L) }
            try {
                installer.install(
                    pack = offer.pack,
                    destRoot = destRoot,
                    userConfirmed = true,
                    onProgress = { downloaded, total ->
                        patch(id) { row -> row.copy(downloaded = downloaded, total = total) }
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: AgentException) {
                patch(id) { it.copy(error = e.userMessage) }
            } finally {
                jobs.remove(id)
                val installed = offer.isInstalled(destRoot)
                patch(id) {
                    it.copy(
                        downloading = false,
                        installed = installed,
                        downloaded = 0L,
                        total = -1L,
                    )
                }
            }
        }
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
    }

    private fun OfflineModelOffer.toRow() = OfflineModelRowUi(
        id = id,
        title = title,
        sizeLabel = sizeLabel,
        configured = isConfigured(),
        installed = isInstalled(destRoot),
    )

    private fun patch(id: String, transform: (OfflineModelRowUi) -> OfflineModelRowUi) {
        _ui.update { state ->
            state.copy(rows = state.rows.map { row -> if (row.id == id) transform(row) else row })
        }
    }

    companion object {
        const val UNCONFIGURED = "尚未配置下载地址"
    }
}
