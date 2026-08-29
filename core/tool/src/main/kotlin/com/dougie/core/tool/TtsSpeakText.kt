package com.dougie.core.tool

/**
 * VITS lexicon usually has 零一二… not ASCII digits, so "2026年8月" is spoken as
 * "年月日" with the numbers dropped. Expand digits before offline TTS only;
 * Chat still shows the original `finalAnswer`.
 */
object TtsSpeakText {
    private val digits = charArrayOf('零', '一', '二', '三', '四', '五', '六', '七', '八', '九')
    private val clock = Regex("""(\d{1,2})[:：](\d{2})""")
    private val number = Regex("""\d+""")

    fun forOffline(text: String): String {
        val withClocks = clock.replace(text) { match ->
            val hour = match.groupValues[1].toInt()
            val minuteRaw = match.groupValues[2]
            val minute = minuteRaw.toInt()
            twoDigit(hour) + "点" + minuteSpeech(minute, minuteRaw)
        }
        return number.replace(withClocks) { numberToChinese(it.value) }
    }

    private fun minuteSpeech(value: Int, raw: String): String {
        if (raw.length == 2 && raw[0] == '0') {
            return if (value == 0) "零" else "零" + digits[value]
        }
        return twoDigit(value)
    }

    private fun numberToChinese(raw: String): String {
        if (raw.length >= 3) {
            return raw.map { ch -> digits[ch - '0'] }.joinToString("")
        }
        if (raw.length == 2 && raw[0] == '0') {
            val value = raw.toInt()
            return if (value == 0) "零" else "零" + digits[value]
        }
        return twoDigit(raw.toInt())
    }

    private fun twoDigit(value: Int): String {
        if (value <= 9) return digits[value].toString()
        if (value == 10) return "十"
        val tens = value / 10
        val ones = value % 10
        val head = if (tens == 1) "十" else digits[tens] + "十"
        return if (ones == 0) head else head + digits[ones]
    }
}
