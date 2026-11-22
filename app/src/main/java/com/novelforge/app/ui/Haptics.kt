package com.novelforge.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 统一的触感反馈。
 *
 * 系统那套 HapticFeedbackType 在国产 ROM 上经常被削得很轻，甚至没反应，
 * 所以这里直接用 Vibrator 控制时长和振幅，手感更实在。
 */
object Haptics {

    /** 普通点击：短促一下 */
    fun tap(ctx: Context) = buzz(ctx, 26L, 110)

    /** 选中 / 翻页：比点击再轻一点，避免连续操作时发麻 */
    fun select(ctx: Context) = buzz(ctx, 18L, 80)

    /** 长按：明显更重更长 */
    fun longPress(ctx: Context) = buzz(ctx, 55L, 180)

    /** 吸附 / 边界回弹：两下连击 */
    fun tick(ctx: Context) {
        val v = vibrator(ctx) ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 14, 40, 18), -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 14, 40, 18), -1)
        }
    }

    private fun vibrator(ctx: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }

    private fun buzz(ctx: Context, ms: Long, amplitude: Int) {
        val v = vibrator(ctx) ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }
}
