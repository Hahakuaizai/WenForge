package com.novelforge.app.data

import java.util.Locale

/** 字数显示：一万以上用「万」，一万以下用原数字 */
object WordCount {

    /** 12345 → "1.2 万"；8000 → "8000" */
    fun short(n: Int): String {
        if (n < 10_000) return n.toString()
        val w = n / 10_000.0
        return if (w >= 100) {
            String.format(Locale.US, "%.0f 万", w)
        } else if (w >= 10) {
            String.format(Locale.US, "%.1f 万", w)
        } else {
            String.format(Locale.US, "%.2f 万", w)
        }
    }

    /** 带「字」后缀：12345 → "1.2 万字"；8000 → "8000 字" */
    fun label(n: Int): String = short(n) + "字"

    /** 已经是「万」单位的量（比如估算全书 12.5 万字） */
    fun wan(n: Double): String = when {
        n >= 100 -> String.format(Locale.US, "%.0f 万", n)
        n >= 10 -> String.format(Locale.US, "%.1f 万", n)
        else -> String.format(Locale.US, "%.2f 万", n)
    }
}
