package com.example.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechHelper(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var onSpeechDoneCallback: (() -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            isInitialized = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale.US
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    onSpeechDoneCallback?.invoke()
                }

                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    onSpeechDoneCallback?.invoke()
                }
            })
        } else {
            isInitialized = false
        }
    }

    fun speak(text: String, languageCode: String = "en", onDone: (() -> Unit)? = null) {
        onSpeechDoneCallback = onDone
        if (!isInitialized || tts == null) {
            onDone?.invoke()
            return
        }

        try {
            if (languageCode == "hi" || languageCode == "hinglish") {
                val hindiLocale = Locale("hi", "IN")
                if (tts?.isLanguageAvailable(hindiLocale) == TextToSpeech.LANG_AVAILABLE) {
                    tts?.language = hindiLocale
                }
            } else {
                tts?.language = Locale.US
            }

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_ASSISTANT_RESPONSE")
        } catch (_: Exception) {
            onDone?.invoke()
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
        onSpeechDoneCallback?.invoke()
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {}
        isInitialized = false
    }
}
