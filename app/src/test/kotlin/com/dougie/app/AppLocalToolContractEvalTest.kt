package com.dougie.app

import com.dougie.core.llm.LocalToolContractEval
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppLocalToolContractEvalTest {
    @Test
    fun messageIsCountsPathAndAdbOnly() {
        val dir = kotlin.io.path.createTempDirectory("contract-msg").toFile()
        try {
            val file = File(dir, "predictions.jsonl")
            val items = LocalToolContractEval.loadResource()
            LocalToolContractEval.writePredictions(
                file,
                items.associate { it.id to it.expectTool },
            )
            val message = AppLocalToolContractEval.last(file, "com.dougie.app")
            assertTrue(message!!.contains("local-tool n=14 correct=14 passed=true"))
            assertTrue(message.contains("eval/local-tool/predictions.jsonl"))
            assertTrue(message.contains("adb exec-out run-as com.dougie.app cat files/eval/local-tool/predictions.jsonl"))
            assertFalse(message.contains("已达标"))
            assertFalse(message.contains("现在几点"))
            assertFalse(message.contains("你是谁"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
