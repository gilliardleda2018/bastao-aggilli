package br.com.aggilli.bastao

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Anúncio por voz das leituras (celular no bolso, curral barulhento).
 * Fala os 4 últimos dígitos do brinco; "repetido" quando o brinco já está na sessão.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    var enabled = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("pt", "BR")
            ready = true
        }
    }

    fun announce(tag: String, duplicate: Boolean, recovered: Int = 0) {
        if (!enabled || !ready) return
        val text = when {
            recovered > 0 -> "$recovered leituras recuperadas"
            duplicate -> "repetido, ${short(tag)}"
            else -> short(tag)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bastao")
    }

    /** Últimos 4 dígitos sem zeros à esquerda: "076000000001234" -> "1234". */
    private fun short(tag: String): String {
        val tail = tag.takeLast(4).trimStart('0')
        return tail.ifEmpty { "0" }
    }

    fun release() {
        tts?.shutdown()
        tts = null
    }
}
