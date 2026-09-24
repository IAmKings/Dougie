package com.dougie.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyCopyTest {
    @Test
    fun privacyCopyMatchesShippedEgressBehavior() {
        val text = PRIVACY_PARAGRAPHS.joinToString("\n")
        assertTrue(text.contains("默认不把对话发到云端"))
        assertTrue(text.contains("允许显式数据出站"))
        assertTrue(text.contains("点保存"))
        assertTrue(text.contains("你的输入"))
        assertTrue(text.contains("组装后的上下文"))
        assertTrue(text.contains("工具结果"))
        assertTrue(text.contains("非截屏图片"))
        assertTrue(text.contains("截屏像素留在本机"))
        assertTrue(text.contains("录音只在本机转成文字"))
        assertTrue(text.contains("音频不会上传"))
        assertTrue(text.contains("密钥存在本机的加密存储里"))
        assertTrue(text.contains("不会写进日志"))
        assertFalse(text.contains("绝不离开"))
        assertFalse(text.contains("已上架"))
        assertFalse(PRIVACY_TITLE.isBlank())
    }
}
