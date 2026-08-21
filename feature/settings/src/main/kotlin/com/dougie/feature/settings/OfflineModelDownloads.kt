package com.dougie.feature.settings

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.IntentModelLayout
import com.dougie.core.tool.ModelImporter
import com.dougie.core.tool.ModelInstaller
import com.dougie.core.tool.OfflineModelOffer
import com.dougie.core.tool.isConfigured
import com.dougie.core.tool.isInstalled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

data class ProbeResult(
    val ok: Boolean,
    val message: String,
)

fun interface OfflineModelProbe {
    suspend fun probe(id: String): ProbeResult
}

data class OfflineModelRowUi(
    val id: String,
    val title: String,
    val sizeLabel: String,
    val configured: Boolean,
    val installed: Boolean,
    val downloading: Boolean = false,
    val probing: Boolean = false,
    val downloaded: Long = 0L,
    val total: Long = -1L,
    val error: String? = null,
    val probeMessage: String? = null,
    val probeOk: Boolean? = null,
    val willReplace: Boolean = false,
)

data class OfflineModelsUi(
    val rows: List<OfflineModelRowUi>,
    val pendingConfirmId: String? = null,
    val treeReady: Boolean = false,
    val treeNeedsReselect: Boolean = false,
    val treeLabel: String = "未选择",
    val scanning: Boolean = false,
) {
    val pending: OfflineModelRowUi?
        get() = pendingConfirmId?.let { id -> rows.find { it.id == id } }
}

class OfflineModelDownloads(
    private val installer: ModelInstaller,
    private val destRoot: File,
    private val cacheRoot: File,
    private val offers: List<OfflineModelOffer>,
    private val scope: CoroutineScope,
    private val importer: ModelImporter = ModelImporter(),
    private val probe: OfflineModelProbe = OfflineModelProbe {
        ProbeResult(ok = false, message = UserFacingErrors.TOOL_FAILED)
    },
    private val tree: ExternalModelTree = NoExternalModelTree,
) {
    private val jobs = mutableMapOf<String, Job>()
    private val syncedIds = mutableSetOf<String>()
    private val scannedDirs = mutableSetOf<String>()
    private val _ui = MutableStateFlow(
        OfflineModelsUi(rows = offers.map { it.toRow() }).withTree(tree.snapshot()),
    )
    val ui: StateFlow<OfflineModelsUi> = _ui.asStateFlow()

    fun request(id: String) {
        val offer = offers.find { it.id == id } ?: return
        val row = _ui.value.rows.find { it.id == id } ?: return
        if (row.downloading || row.probing || row.installed) return
        if (isDirBusy(offer.pack.relativeDir)) {
            patch(id) { it.copy(error = DIR_BUSY) }
            return
        }
        val status = tree.snapshot()
        applyTreeStatus(status)
        if (!status.ready) {
            patch(id) { it.copy(error = treeError(status)) }
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
        val status = tree.snapshot()
        applyTreeStatus(status)
        if (!status.ready) {
            patch(id) { it.copy(error = treeError(status)) }
            return
        }
        if (!offer.isConfigured()) {
            patch(id) { it.copy(error = UNCONFIGURED) }
            return
        }
        if (jobs.containsKey(id)) return
        if (isDirBusy(offer.pack.relativeDir)) {
            patch(id) { it.copy(error = DIR_BUSY) }
            return
        }
        val scanJob = jobs.remove(SCAN_JOB)
        jobs[id] = scope.launch {
            scanJob?.cancel()
            scanJob?.join()
            patch(id) { it.copy(downloading = true, error = null, downloaded = 0L, total = -1L) }
            val cachePackRoot = File(cacheRoot, "dl-$id")
            cachePackRoot.deleteRecursively()
            var failure: String? = null
            try {
                installer.install(
                    pack = offer.pack,
                    destRoot = cachePackRoot,
                    userConfirmed = true,
                    onProgress = { downloaded, total ->
                        patch(id) { row -> row.copy(downloaded = downloaded, total = total) }
                    },
                )
                val layoutDir = File(cachePackRoot, offer.pack.relativeDir)
                val layoutFiles = offer.pack.files.map { spec ->
                    spec.name to File(layoutDir, spec.name)
                }
                tree.writeLayoutFiles(offer.pack.relativeDir, layoutFiles)
                importer.importFiles(offer.pack, destRoot, layoutFiles.map { it.second })
                markDirSynced(offer.pack.relativeDir, setOf(offer.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AgentException) {
                failure = e.userMessage
            } catch (_: Exception) {
                failure = UserFacingErrors.MODEL_DOWNLOAD_FAILED
            } finally {
                cachePackRoot.deleteRecursively()
                jobs.remove(id)
                refreshDir(offer.pack.relativeDir, failedId = id, failure = failure)
            }
        }
    }

    fun scan() {
        applyTreeStatus()
        if (jobs.isNotEmpty()) return
        jobs[SCAN_JOB] = scope.launch {
            _ui.update { it.copy(scanning = true) }
            try {
                val status = tree.snapshot()
                applyTreeStatus(status)
                if (!status.ready) {
                    syncedIds.clear()
                    scannedDirs.clear()
                } else {
                    offers.map { it.pack.relativeDir }.distinct().forEach { relativeDir ->
                        scanRelativeDir(relativeDir)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                jobs.remove(SCAN_JOB)
                refreshAll(failedId = null, failure = null)
                _ui.update { it.copy(scanning = false) }
                applyTreeStatus()
            }
        }
    }

    fun probe(id: String) {
        val row = _ui.value.rows.find { it.id == id } ?: return
        if (!row.installed || row.downloading || row.probing || row.probeOk == true) return
        if (_ui.value.rows.any { it.probing }) return
        if (jobs.containsKey(id)) return
        patch(id) { it.copy(probing = true, probeMessage = null, probeOk = null, error = null) }
        jobs[id] = scope.launch {
            val result = try {
                withTimeout(probeTimeoutMs(id)) {
                    probe.probe(id)
                }
            } catch (_: TimeoutCancellationException) {
                ProbeResult(ok = false, message = UserFacingErrors.MODEL_PROBE_TIMEOUT)
            } catch (e: CancellationException) {
                jobs.remove(id)
                patch(id) { it.copy(probing = false, probeOk = null, probeMessage = null) }
                throw e
            } catch (e: AgentException) {
                ProbeResult(ok = false, message = e.userMessage)
            } catch (_: Exception) {
                ProbeResult(ok = false, message = UserFacingErrors.TOOL_FAILED)
            }
            jobs.remove(id)
            patch(id) {
                it.copy(
                    probing = false,
                    probeOk = result.ok,
                    probeMessage = result.message,
                )
            }
        }
    }

    fun cancel(id: String) {
        jobs[id]?.cancel()
    }

    private suspend fun scanRelativeDir(relativeDir: String) {
        var copies = emptyList<File>()
        try {
            copies = tree.copyPackFiles(relativeDir)
            val successIds = mutableSetOf<String>()
            for (offer in offers.filter { it.pack.relativeDir == relativeDir }) {
                if (copies.isEmpty()) break
                try {
                    importer.importFiles(offer.pack, destRoot, copies)
                    successIds += offer.id
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (_: AgentException) {
                    // Incomplete or wrong hash: try the sibling offer, else leave uninstalled.
                }
            }
            markDirSynced(relativeDir, successIds)
        } catch (e: CancellationException) {
            throw e
        } catch (_: AgentException) {
            markDirSynced(relativeDir, emptySet())
        } catch (_: Exception) {
            markDirSynced(relativeDir, emptySet())
        } finally {
            copies.forEach { it.delete() }
        }
    }

    private fun OfflineModelOffer.toRow() = OfflineModelRowUi(
        id = id,
        title = title,
        sizeLabel = sizeLabel,
        configured = isConfigured(),
        installed = isOfferInstalled(this),
        willReplace = willReplace(this),
    )

    private fun isOfferInstalled(offer: OfflineModelOffer): Boolean {
        if (!tree.snapshot().ready) return offer.isInstalled(destRoot)
        if (offer.pack.relativeDir in scannedDirs) return offer.id in syncedIds
        return offer.isInstalled(destRoot)
    }

    private fun willReplace(offer: OfflineModelOffer): Boolean =
        offers.any { other ->
            other.id != offer.id &&
                other.pack.relativeDir == offer.pack.relativeDir &&
                isOfferInstalled(other)
        }

    private fun isDirBusy(relativeDir: String): Boolean =
        jobs.keys.any { jobId ->
            jobId != SCAN_JOB && offers.find { it.id == jobId }?.pack?.relativeDir == relativeDir
        }

    private fun markDirSynced(relativeDir: String, successIds: Set<String>) {
        scannedDirs += relativeDir
        offers.filter { it.pack.relativeDir == relativeDir }.forEach { offer ->
            if (offer.id in successIds) syncedIds.add(offer.id) else syncedIds.remove(offer.id)
        }
    }

    private fun refreshAll(failedId: String?, failure: String?) {
        _ui.update { state ->
            state.copy(
                rows = state.rows.map { row ->
                    val offer = offers.find { it.id == row.id } ?: return@map row
                    row.copy(
                        downloading = false,
                        probing = false,
                        installed = isOfferInstalled(offer),
                        willReplace = willReplace(offer),
                        downloaded = 0L,
                        total = -1L,
                        error = if (row.id == failedId) failure else row.error,
                    )
                },
            ).withTree(tree.snapshot())
        }
    }

    private fun refreshDir(relativeDir: String, failedId: String, failure: String?) {
        _ui.update { state ->
            state.copy(
                rows = state.rows.map { row ->
                    val offer = offers.find { it.id == row.id } ?: return@map row
                    if (offer.pack.relativeDir != relativeDir) return@map row
                    row.copy(
                        downloading = false,
                        probing = false,
                        installed = isOfferInstalled(offer),
                        willReplace = willReplace(offer),
                        downloaded = 0L,
                        total = -1L,
                        error = if (row.id == failedId) failure else row.error,
                    )
                },
            ).withTree(tree.snapshot())
        }
    }

    private fun applyTreeStatus(status: ExternalModelTreeStatus = tree.snapshot()) {
        _ui.update { it.withTree(status) }
    }

    private fun patch(id: String, transform: (OfflineModelRowUi) -> OfflineModelRowUi) {
        _ui.update { state ->
            state.copy(rows = state.rows.map { row -> if (row.id == id) transform(row) else row })
        }
    }

    companion object {
        const val UNCONFIGURED = "尚未配置下载地址"
        const val DIR_BUSY = "正在下载另一份意图模型，请稍候"
        const val TREE_MISSING = UserFacingErrors.MODEL_TREE_MISSING
        const val TREE_RESELECT = UserFacingErrors.MODEL_TREE_RESELECT
        private const val SCAN_JOB = "_scan"

        private fun treeError(status: ExternalModelTreeStatus): String =
            if (status.needsReselect) TREE_RESELECT else TREE_MISSING

        private fun probeTimeoutMs(id: String): Long =
            if (id == IntentModelLayout.ID) {
                180_000L
            } else {
                90_000L
            }
    }
}

private fun OfflineModelsUi.withTree(status: ExternalModelTreeStatus) = copy(
    treeReady = status.ready,
    treeNeedsReselect = status.needsReselect,
    treeLabel = status.label,
)
