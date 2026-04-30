package com.nomad.travel.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object NomadHaptics {
    fun lightTick(context: Context) {
        vibrate(context, durationMs = 8, amplitude = 35)
    }

    fun answerComplete(context: Context) {
        vibrate(context, durationMs = 28, amplitude = 105)
    }

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context, durationMs: Long, amplitude: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (!vibrator.hasVibrator()) return

        runCatching {
            val effect = VibrationEffect.createOneShot(
                durationMs,
                amplitude.coerceIn(1, 255)
            )
            vibrator.vibrate(effect)
        }
    }
}
