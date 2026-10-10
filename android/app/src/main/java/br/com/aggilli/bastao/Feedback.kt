package br.com.aggilli.bastao

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator

/** Retorno sonoro e vibratório do celular a cada leitura. */
class Feedback(context: Context) {

    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)

    @Suppress("DEPRECATION")
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    /** Leitura nova. */
    fun ok() {
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        vibrate(60)
    }

    /** Leitura repetida na sessão. */
    fun duplicate() {
        tone.startTone(ToneGenerator.TONE_PROP_NACK, 200)
        vibrate(200)
    }

    private fun vibrate(ms: Long) {
        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun release() {
        tone.release()
    }
}
