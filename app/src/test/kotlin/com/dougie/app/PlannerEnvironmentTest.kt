package com.dougie.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerEnvironmentTest {

    @Test
    fun buildsAliasListAndScreenHint() {
        val json = """[{"alias":"Anchor","package":"fm.anchor.android"},{"alias":"微信","package":"com.tencent.mm"}]"""
        val block = PlannerEnvironment.build(json, 1080, 2340)
        assertTrue(block.contains("Anchor=fm.anchor.android"))
        assertTrue(block.contains("微信=com.tencent.mm"))
        assertTrue(block.contains("1080x2340"))
        assertTrue(block.contains("底部 Tab"))
        assertTrue(block.contains("必须用其包名构造 package: URI"))
        assertTrue(block.contains("不要改用链接"))
        assertTrue(block.length <= PlannerEnvironment.MAX_LENGTH)
    }

    @Test
    fun omitsSectionsWhenInputsEmpty() {
        assertEquals("", PlannerEnvironment.build("[]", 0, 0))
        val screenOnly = PlannerEnvironment.build("[]", 1080, 2340)
        assertTrue(screenOnly.contains("1080x2340"))
        assertTrue(!screenOnly.contains("别名=包名"))
        val appsOnly = PlannerEnvironment.build("""[{"alias":"微信","package":"com.tencent.mm"}]""", 0, 0)
        assertTrue(appsOnly.contains("微信=com.tencent.mm"))
        assertTrue(!appsOnly.contains("屏幕分辨率"))
    }

    @Test
    fun capsEntriesAtFifteen() {
        val json = (1..20).joinToString(",", "[", "]") {
            """{"alias":"app$it","package":"com.example.app$it"}"""
        }
        val block = PlannerEnvironment.build(json, 1080, 2340)
        assertTrue(block.contains("app15"))
        assertTrue(!block.contains("app16"))
    }
}
