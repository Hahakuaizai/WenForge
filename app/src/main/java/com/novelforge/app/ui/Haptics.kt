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
 * 所以这里直接用 Vibrator 控制时长和振幅。强度可在「设置 → 触感反馈」
 * 里关掉或调轻重，由 AppViewModel 把设置同步进来（enabled / strengthScale）。
 */
object Haptics {

    /** 设置同步进来：开关 + 强度倍率（0.5 轻 / 1.0 标准 / 1.5 强） */
    @Volatile
    var enabled: Boolean = true

    @Volatile
    var strengthScale: Float = 1f

    fun configure(on: Boolean, scale: Float) {
        enabled = on
        strengthScale = scale.coerceIn(0.2f, 2f)
    }

    // 轻反馈全局节流：连续滑动 / 连点时别叠成一片嗡嗡声
    @Volatile
    private var lastLightMs: Long = 0

    private fun lightGate(): Boolean {
        if (!enabled) return false
        val now = System.currentTimeMillis()
        if (now - lastLightMs < 45) return false
        lastLightMs = now
        return true
    }

    /** 普通点击：短促干脆 */
    fun tap(ctx: Context) {
        if (lightGate()) buzz(ctx, 16, 90)
    }

    /** 选中 / 翻页：再轻一点，连续操作不发麻 */
    fun select(ctx: Context) {
        if (lightGate()) buzz(ctx, 12, 60)
    }

    /** 书架跨槽：两下极轻的点，像指尖拨过一道道刻度 */
    fun tick(ctx: Context) {
        if (!lightGate()) return
        wave(ctx, longArrayOf(0, 10, 30, 12), intArrayOf(0, 70, 0, 45))
    }

    /** 长按：明显更重更长 */
    fun longPress(ctx: Context) {
        if (enabled) buzz(ctx, 45, 170)
    }

    /** 操作成功（导入完成 / 准备安装）：轻快两下，第二下更实 */
    fun confirm(ctx: Context) {
        if (!enabled) return
        wave(ctx, longArrayOf(0, 16, 55, 24), intArrayOf(0, 110, 0, 150))
    }

    /** 出错 / 警告：两下重击 */
    fun warn(ctx: Context) {
        if (!enabled) return
        wave(ctx, longArrayOf(0, 28, 45, 28), intArrayOf(0, 190, 0, 190))
    }

    private fun amp(base: Int): Int =
        (base * strengthScale).toInt().coerceIn(1, 255)

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
                // 没有振幅控制的机器只能退回系统默认强度，靠时长区分轻重
                val a = if (v.hasAmplitudeControl()) amp(amplitude)
                else VibrationEffect.DEFAULT_AMPLITUDE
                v.vibrate(VibrationEffect.createOneShot(ms, a))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }

    private fun wave(ctx: Context, timings: LongArray, amps: IntArray) {
        val v = vibrator(ctx) ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (v.hasAmplitudeControl()) {
                    val scaled = IntArray(amps.size) { i ->
                        if (amps[i] == 0) 0 else amp(amps[i])
                    }
                    v.vibrate(VibrationEffect.createWaveform(timings, scaled, -1))
                } else {
                    v.vibrate(VibrationEffect.createWaveform(timings, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(timings, -1)
            }
        } catch (_: Exception) {
        }
    }
}
