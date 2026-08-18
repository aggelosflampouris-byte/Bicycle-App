package com.fitnessapp.tracker.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.fitnessapp.tracker.data.local.CoachLanguage
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingLanguage: Locale = Locale.US
    private var pendingSpeech: String? = null
    private var pendingFlush: Boolean = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            applyLocale(pendingLanguage)
            isInitialized = true
            pendingSpeech?.let { text ->
                val mode = if (pendingFlush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts?.speak(text, mode, null, null)
                pendingSpeech = null
            }
        } else {
            Log.e("TtsManager", "TTS Initialization failed with status $status")
        }
    }

    fun setCoachLanguage(language: CoachLanguage) {
        val locale = when (language) {
            CoachLanguage.GREEK -> Locale.forLanguageTag("el-GR")
            CoachLanguage.GERMAN -> Locale.GERMAN
            CoachLanguage.FRENCH -> Locale.FRENCH
            CoachLanguage.RUSSIAN -> Locale.forLanguageTag("ru-RU")
            CoachLanguage.ENGLISH -> Locale.US
            CoachLanguage.AUTO -> Locale.getDefault()
        }
        pendingLanguage = locale
        if (isInitialized) {
            applyLocale(locale)
        }
    }

    private fun applyLocale(locale: Locale) {
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("TtsManager", "Language $locale not supported by TTS engine, falling back to default")
            tts?.setLanguage(Locale.US)
        }
    }

    fun speak(text: String, flushQueue: Boolean = false) {
        if (isInitialized) {
            val queueMode = if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(text, queueMode, null, null)
        } else {
            pendingSpeech = text
            pendingFlush = flushQueue
            Log.d("TtsManager", "TTS initializing, buffered speech: $text")
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
