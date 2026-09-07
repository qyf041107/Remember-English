package com.qyf.rememberenglish.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** 单词发音（系统 TTS，美音） */
@Singleton
class TtsPlayer @Inject constructor(
    @ApplicationContext context: Context,
) : TextToSpeech.OnInitListener {
    @Volatile
    private var ready = false

    private val tts = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts.language = Locale.US
    }

    fun speak(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "remember_english_$text")
    }

    fun shutdown() {
        tts.shutdown()
    }
}
