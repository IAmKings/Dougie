package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAppEntriesTest {
    @Test
    fun matchStripsOpenPrefixExactly() {
        val entries = listOf(OpenAppEntry(alias = "微信", packageName = "com.example.wechat"))
        assertEquals(entries.single(), OpenAppEntries.match("打开微信", entries))
        assertEquals(entries.single(), OpenAppEntries.match("帮我打开微信", entries))
        assertNull(OpenAppEntries.match("打开微信看看", entries))
        assertNull(OpenAppEntries.match("打开", entries))
    }

    @Test
    fun upsertReplacesSamePackage() {
        val first = OpenAppEntries.upsert(emptyList(), "微信", "com.example.wechat")!!
        val renamed = OpenAppEntries.upsert(first, "WeChat", "com.example.wechat")!!
        assertEquals(1, renamed.size)
        assertEquals("WeChat", renamed.single().alias)
    }

    @Test
    fun roundTripJson() {
        val list = listOf(OpenAppEntry("微信", "com.example.wechat"))
        assertEquals(list, OpenAppEntries.parse(OpenAppEntries.encode(list)))
        assertEquals(emptyList<OpenAppEntry>(), OpenAppEntries.parse(""))
        assertEquals(emptyList<OpenAppEntry>(), OpenAppEntries.parse("not-json"))
    }

    @Test
    fun parseKeepsFirstDuplicatePackage() {
        val raw = """[{"alias":"先","package":"com.example.wechat"},{"alias":"后","package":"com.example.wechat"}]"""
        assertEquals(
            listOf(OpenAppEntry("先", "com.example.wechat")),
            OpenAppEntries.parse(raw),
        )
    }
}
