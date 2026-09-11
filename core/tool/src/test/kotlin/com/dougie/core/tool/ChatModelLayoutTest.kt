package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ChatModelLayoutTest {
    @Test
    fun resolvePrefersStoredWhenPresent() {
        val dir = Files.createTempDirectory("chat-sku").toFile()
        try {
            File(dir, ChatModelLayout.MODEL_FILE).writeText("a")
            File(dir, ChatModelLayout.MINICPM1B_FILE).writeText("b")
            assertEquals(
                ChatModelLayout.MINICPM1B_ID,
                ChatModelLayout.resolveActiveSku(dir, ChatModelLayout.MINICPM1B_ID),
            )
            assertEquals(ChatModelLayout.ID, ChatModelLayout.resolveActiveSku(dir, "chat"))
            assertNull(ChatModelLayout.resolveActiveSku(dir, null))
            assertNull(ChatModelLayout.resolveActiveSku(dir, ""))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun officialFilenamesAreSafe() {
        assertEquals(true, isSafeFileName(ChatModelLayout.MODEL_FILE))
        assertEquals(true, isSafeFileName(ChatModelLayout.MINICPM1B_FILE))
        assertEquals(true, isSafeFileName(ChatModelLayout.MINICPM2B_FILE))
    }

    @Test
    fun matchesGpuOptAsMiniCpm1b() {
        assertEquals(
            true,
            ChatModelLayout.matchesFileName(
                ChatModelLayout.MINICPM1B_FILE,
                ChatModelLayout.MINICPM1B_ID,
            ),
        )
        assertEquals(
            true,
            ChatModelLayout.matchesFileName(
                "MiniCPM5-1B_int4.litertlm",
                ChatModelLayout.MINICPM1B_ID,
            ),
        )
        assertEquals(
            ChatModelLayout.MINICPM1B_ID,
            ChatModelLayout.guessSku("minicpm_wi4b32_wi8_afp32.litertlm", 756L * 1024 * 1024),
        )
        assertEquals(
            ChatModelLayout.MINICPM2B_ID,
            ChatModelLayout.guessSku("weights.litertlm", 1_500L * 1024 * 1024),
        )
        assertEquals(
            ChatModelLayout.ID,
            ChatModelLayout.guessSku("qwen-local.litertlm", 328L * 1024 * 1024),
        )
    }

    @Test
    fun locatePrefersOfficialName() {
        val dir = Files.createTempDirectory("chat-sku-official").toFile()
        try {
            File(dir, "Qwen3-0.6B_previous.litertlm").writeText("old")
            File(dir, ChatModelLayout.MODEL_FILE).writeText("new")
            assertEquals(
                ChatModelLayout.MODEL_FILE,
                ChatModelLayout.locateInDir(dir, ChatModelLayout.ID)?.name,
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun removePrivateStaleDeletesOnlyThatSku() {
        val dest = Files.createTempDirectory("chat-sku-dest").toFile()
        val extra = Files.createTempDirectory("chat-sku-extra").toFile()
        try {
            File(dest, "Qwen3-0.6B_previous.litertlm").writeText("old")
            File(dest, ChatModelLayout.MODEL_FILE).writeText("new")
            File(dest, ChatModelLayout.MINICPM1B_FILE).writeText("keep")
            File(extra, "Qwen3-0.6B_previous.litertlm").writeText("old-extra")
            File(extra, ChatModelLayout.MINICPM2B_FILE).writeText("keep2")
            ChatModelLayout.removePrivateStale(
                ChatModelLayout.ID,
                listOf(dest, extra),
            )
            assertEquals(false, File(dest, "Qwen3-0.6B_previous.litertlm").exists())
            assertEquals(false, File(extra, "Qwen3-0.6B_previous.litertlm").exists())
            assertEquals(true, File(dest, ChatModelLayout.MODEL_FILE).isFile)
            assertEquals(true, File(dest, ChatModelLayout.MINICPM1B_FILE).isFile)
            assertEquals(true, File(extra, ChatModelLayout.MINICPM2B_FILE).isFile)
        } finally {
            dest.deleteRecursively()
            extra.deleteRecursively()
        }
    }

    @Test
    fun officialFileNullWhenOnlyLegacyName() {
        val dir = Files.createTempDirectory("chat-sku-legacy").toFile()
        try {
            File(dir, "Qwen3-0.6B_previous.litertlm").writeText("old")
            assertEquals(null, ChatModelLayout.officialFile(dir, ChatModelLayout.ID))
            assertEquals(true, ChatModelLayout.isPresent(dir, ChatModelLayout.ID))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun locateUsesOddNameWhenSizeMatches() {
        val dir = Files.createTempDirectory("chat-sku-size").toFile()
        try {
            val file = File(dir, "minicpm_wi4b32_wi8_afp32.litertlm")
            file.writeBytes(ByteArray(8))
            assertEquals(
                ChatModelLayout.MINICPM1B_ID,
                ChatModelLayout.guessSku(file.name, file.length()),
            )
            assertEquals(
                ChatModelLayout.MINICPM2B_ID,
                ChatModelLayout.guessSku("weights.litertlm", 1_500L * 1024 * 1024),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun resolveDefaultsWhenOnlyOneInstalled() {
        val dir = Files.createTempDirectory("chat-sku-one").toFile()
        try {
            File(dir, ChatModelLayout.MODEL_FILE).writeText("a")
            assertEquals(ChatModelLayout.ID, ChatModelLayout.resolveActiveSku(dir, null))
        } finally {
            dir.deleteRecursively()
        }
    }
}
