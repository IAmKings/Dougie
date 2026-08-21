package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class ModelImporter {
    fun importFiles(
        pack: ModelPack,
        destRoot: File,
        sources: List<File>,
    ) {
        val dirCanon = preparePackDir(pack, destRoot, requireHttps = false)
        val parts = mutableListOf<File>()
        try {
            val matched = matchSources(pack, sources)
            val staged = pack.files.map { spec ->
                val source = matched.getValue(spec.sha256.lowercase())
                val (target, part) = packTargets(dirCanon, spec.name)
                part.delete()
                parts += part
                source.inputStream().use { input ->
                    part.outputStream().use { output -> input.copyTo(output) }
                }
                val actual = SHA256.hex(part)
                if (actual != spec.sha256.lowercase()) {
                    throw AgentException(UserFacingErrors.MODEL_HASH_MISMATCH)
                }
                target to part
            }
            staged.forEach { (target, part) ->
                commitPart(part, target)
            }
        } catch (e: CancellationException) {
            parts.forEach { it.delete() }
            throw e
        } catch (e: AgentException) {
            parts.forEach { it.delete() }
            throw e
        } catch (_: Exception) {
            parts.forEach { it.delete() }
            throw AgentException(UserFacingErrors.MODEL_IMPORT_FAILED)
        }
    }

    private fun matchSources(pack: ModelPack, sources: List<File>): Map<String, File> {
        val hashed = sources.map { it to SHA256.hex(it) }
        val byHash = hashed.groupBy({ it.second }, { it.first }).mapValues { it.value.first() }
        val specHashes = pack.files.map { it.sha256.lowercase() }.toSet()
        val missing = pack.files.filter { spec -> spec.sha256.lowercase() !in byHash }
            .map { it.name }
            .distinct()
        if (missing.isNotEmpty()) {
            throw AgentException(
                UserFacingErrors.MODEL_IMPORT_FAILED + "缺少：" + missing.joinToString("、"),
            )
        }
        if (byHash.keys.any { it !in specHashes }) {
            throw AgentException(UserFacingErrors.MODEL_IMPORT_FAILED)
        }
        return byHash
    }
}
