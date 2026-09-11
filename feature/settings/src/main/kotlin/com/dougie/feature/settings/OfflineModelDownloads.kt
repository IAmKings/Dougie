package com.dougie.feature.settings

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.ChatModelLayout
import com.dougie.core.tool.IntentModelLayout
import com.dougie.core.tool.ModelImporter
import com.dougie.core.tool.ModelInstaller
import com.dougie.core.tool.OfflineModelOffer
import com.dougie.core.tool.isConfigured
import com.dougie.core.tool.isInstalled
import com.dougie.core.tool.SHA256
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val needsUpdate: Boolean = false,
    val active: Boolean = false,
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
    private val extraRoots: List<File> = emptyList(),
    private val hashDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val jobs = mutableMapOf<String, Job>()
    private val syncedIds = mutableSetOf<String>()
    private val scannedDirs = mutableSetOf<String>()
    private val shaStale = mutableMapOf<String, Boolean>()
    private val shaFingerprint = mutableMapOf<String, Triple<String, Long, Long>>()
    private var hashJob: Job? = null
    private val _ui = MutableStateFlow(
        OfflineModelsUi(rows = offers.map { it.toRow() }).withTree(tree.snapshot()),
    )
    val ui: StateFlow<OfflineModelsUi> = _ui.asStateFlow()

    init {
        verifyOfficialHashes()
    }

    fun request(id: String) {
        val offer = offers.find { it.id == id } ?: return
        val row = _ui.value.rows.find { it.id == id } ?: return
        if (row.downloading || row.probing) return
        if (row.installed && !row.needsUpdate) return
        if (isDirBusy(offer.pack.relativeDir)) {
            patch(id) { it.copy(error = dirBusyMessage(offer)) }
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
            patch(id) { it.copy(error = dirBusyMessage(offer)) }
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
                if (ChatModelLayout.isChatSku(offer.id)) {
                    ChatModelLayout.removePrivateStale(offer.id, chatModelDirs())
                    shaStale[offer.id] = false
                    shaFingerprint.remove(offer.id)
                }
                markOfferSynced(offer.id)
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
        val dirOffers = offers.filter { it.pack.relativeDir == relativeDir }
        val successIds = mutableSetOf<String>()
        try {
            if (relativeDir == ChatModelLayout.DIR) {
                val treeFiles = tree.listPackFiles(relativeDir)
                val localFiles = chatRoots().flatMap { root ->
                    File(root, ChatModelLayout.DIR).listFiles().orEmpty()
                        .filter { it.isFile }
                        .map { ExternalPackFile(it.name, it.length()) }
                }
                val files = treeFiles + localFiles
                val destDir = File(destRoot, ChatModelLayout.DIR)
                for (offer in dirOffers) {
                    val want = offer.pack.files.first().name
                    if (isChatPresent(offer.id)) {
                        successIds += offer.id
                        continue
                    }
                    val hit = files.filter { ChatModelLayout.guessSku(it.name, it.length) == offer.id }
                    if (hit.isEmpty()) continue
                    patch(offer.id) { it.copy(downloading = true, error = null) }
                    try {
                        destDir.mkdirs()
                        tree.copyInto(
                            relativeDir,
                            destDir,
                            mapOf(hit.first().name to want),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: AgentException) {
                    } catch (_: Exception) {
                    }
                    if (isChatPresent(offer.id)) {
                        successIds += offer.id
                    }
                }
            } else {
                var copies = emptyList<File>()
                try {
                    copies = tree.copyPackFiles(relativeDir)
                    for (offer in dirOffers) {
                        if (copies.isEmpty()) break
                        try {
                            importer.importFiles(offer.pack, destRoot, copies)
                            successIds += offer.id
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: AgentException) {
                        }
                    }
                } finally {
                    copies.forEach { it.delete() }
                }
            }
            markDirSynced(relativeDir, successIds)
        } catch (e: CancellationException) {
            throw e
        } catch (_: AgentException) {
            markDirSynced(relativeDir, successIds)
        } catch (_: Exception) {
            markDirSynced(relativeDir, successIds)
        }
    }

    private fun OfflineModelOffer.toRow() = OfflineModelRowUi(
        id = id,
        title = title,
        sizeLabel = sizeLabel,
        configured = isConfigured(),
        installed = isOfferInstalled(this),
        willReplace = willReplace(this),
        needsUpdate = needsUpdate(this),
    )

    private fun chatRoots(): List<File> = listOf(destRoot) + extraRoots

    private fun chatModelDirs(): List<File> = chatRoots().map { File(it, ChatModelLayout.DIR) }

    private fun isChatPresent(skuId: String): Boolean =
        chatModelDirs().any { ChatModelLayout.isPresent(it, skuId) }

    private fun needsUpdate(offer: OfflineModelOffer): Boolean {
        if (!ChatModelLayout.isChatSku(offer.id) || !isChatPresent(offer.id)) return false
        if (ChatModelLayout.officialFile(chatModelDirs(), offer.id) == null) return true
        return shaStale[offer.id] == true
    }

    private fun verifyOfficialHashes() {
        val pending = offers.filter { offer ->
            ChatModelLayout.isChatSku(offer.id) &&
                isChatPresent(offer.id) &&
                ChatModelLayout.officialFile(chatModelDirs(), offer.id) != null
        }
        if (pending.isEmpty()) return
        hashJob?.cancel()
        hashJob = scope.launch {
            val snapshots = pending.mapNotNull { offer ->
                val file = ChatModelLayout.officialFile(chatModelDirs(), offer.id)
                    ?: return@mapNotNull null
                val expected = offer.pack.files.firstOrNull()?.sha256?.lowercase()
                    ?: return@mapNotNull null
                OfficialHashSnap(
                    offer.id,
                    file,
                    expected,
                    Triple(file.absolutePath, file.length(), file.lastModified()),
                )
            }
            val toHash = snapshots.filter { snap ->
                shaFingerprint[snap.id] != snap.key || !shaStale.containsKey(snap.id)
            }
            val hashed = if (toHash.isEmpty()) {
                emptyList()
            } else {
                withContext(hashDispatcher) {
                    toHash.mapNotNull { snap ->
                        val actual = try {
                            SHA256.hex(snap.file)
                        } catch (_: Exception) {
                            return@mapNotNull null
                        }
                        val after = Triple(
                            snap.file.absolutePath,
                            snap.file.length(),
                            snap.file.lastModified(),
                        )
                        if (after != snap.key) return@mapNotNull null
                        Triple(snap.id, actual != snap.expected, after)
                    }
                }
            }
            hashed.forEach { (id, stale, key) ->
                shaStale[id] = stale
                shaFingerprint[id] = key
            }
            if (hashed.isEmpty() && toHash.isEmpty()) return@launch
            _ui.update { state ->
                state.copy(
                    rows = state.rows.map { row ->
                        val offer = offers.find { it.id == row.id } ?: return@map row
                        row.copy(needsUpdate = needsUpdate(offer))
                    },
                )
            }
        }
    }

    private fun isOfferInstalled(offer: OfflineModelOffer): Boolean {
        if (ChatModelLayout.isChatSku(offer.id) && isChatPresent(offer.id)) return true
        if (offer.isInstalled(destRoot)) return true
        if (!tree.snapshot().ready) return false
        return offer.pack.relativeDir in scannedDirs && offer.id in syncedIds
    }

    private fun willReplace(offer: OfflineModelOffer): Boolean {
        if (ChatModelLayout.isChatSku(offer.id)) return false
        return offers.any { other ->
            other.id != offer.id &&
                other.pack.relativeDir == offer.pack.relativeDir &&
                isOfferInstalled(other)
        }
    }

    private fun isDirBusy(relativeDir: String): Boolean =
        jobs.keys.any { jobId ->
            jobId != SCAN_JOB && offers.find { it.id == jobId }?.pack?.relativeDir == relativeDir
        }

    private fun markOfferSynced(id: String) {
        val offer = offers.find { it.id == id } ?: return
        scannedDirs += offer.pack.relativeDir
        syncedIds += id
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
                        needsUpdate = needsUpdate(offer),
                        downloaded = 0L,
                        total = -1L,
                        error = if (row.id == failedId) failure else row.error,
                    )
                },
            ).withTree(tree.snapshot())
        }
        verifyOfficialHashes()
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
                        needsUpdate = needsUpdate(offer),
                        downloaded = 0L,
                        total = -1L,
                        error = if (row.id == failedId) failure else row.error,
                    )
                },
            ).withTree(tree.snapshot())
        }
        verifyOfficialHashes()
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
        const val CHAT_DIR_BUSY = "正在下载另一份对话模型，请稍候"
        const val TREE_MISSING = UserFacingErrors.MODEL_TREE_MISSING
        const val TREE_RESELECT = UserFacingErrors.MODEL_TREE_RESELECT
        private const val SCAN_JOB = "_scan"

        private fun treeError(status: ExternalModelTreeStatus): String =
            if (status.needsReselect) TREE_RESELECT else TREE_MISSING

        private fun dirBusyMessage(offer: OfflineModelOffer): String =
            if (ChatModelLayout.isChatSku(offer.id)) CHAT_DIR_BUSY else DIR_BUSY

        private fun probeTimeoutMs(id: String): Long =
            if (id == IntentModelLayout.ID || ChatModelLayout.isChatSku(id)) {
                180_000L
            } else {
                90_000L
            }
    }
}

private data class OfficialHashSnap(
    val id: String,
    val file: File,
    val expected: String,
    val key: Triple<String, Long, Long>,
)

private fun OfflineModelsUi.withTree(status: ExternalModelTreeStatus) = copy(
    treeReady = status.ready,
    treeNeedsReselect = status.needsReselect,
    treeLabel = status.label,
)
