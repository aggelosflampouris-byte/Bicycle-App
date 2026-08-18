package com.fitnessapp.tracker.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.fitnessapp.tracker.data.local.CoachLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    object Recognizing : VoiceState()
    data class Success(val recognizedText: String) : VoiceState()
    data class Error(val errorMessage: String) : VoiceState()
}

class VoiceInteractionManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(language: CoachLanguage) {
        if (!isAvailable()) {
            _voiceState.value = VoiceState.Error("Speech recognition is not available on this device.")
            return
        }

        stopListening()

        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            Log.e("VoiceInteractionManager", "Error creating speech recognizer", e)
            _voiceState.value = VoiceState.Error("Failed to initialize voice recognition")
            return
        }
        speechRecognizer = recognizer

        val bcp47 = when (language) {
            CoachLanguage.GREEK -> "el-GR"
            CoachLanguage.GERMAN -> "de-DE"
            CoachLanguage.FRENCH -> "fr-FR"
            CoachLanguage.RUSSIAN -> "ru-RU"
            CoachLanguage.ENGLISH -> "en-US"
            CoachLanguage.AUTO -> Locale.getDefault().toLanguageTag()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcp47)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, bcp47)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _voiceState.value = VoiceState.Listening
            }

            override fun onBeginningOfSpeech() {
                _voiceState.value = VoiceState.Listening
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _voiceState.value = VoiceState.Recognizing
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client recognition error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission required"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
                    else -> "Recognition failed ($error)"
                }
                Log.w("VoiceInteractionManager", "Speech error: $message")
                _voiceState.value = VoiceState.Error(message)
                cleanup()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (!text.isNullOrEmpty()) {
                    _voiceState.value = VoiceState.Success(text)
                } else {
                    _voiceState.value = VoiceState.Error("Could not recognize speech")
                }
                cleanup()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            recognizer.startListening(intent)
            _voiceState.value = VoiceState.Listening
        } catch (e: Exception) {
            Log.e("VoiceInteractionManager", "Failed to start listening", e)
            _voiceState.value = VoiceState.Error(e.message ?: "Failed to start voice recognition")
            cleanup()
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w("VoiceInteractionManager", "Error stopping recognizer", e)
        } finally {
            cleanup()
            if (_voiceState.value is VoiceState.Listening || _voiceState.value is VoiceState.Recognizing) {
                _voiceState.value = VoiceState.Idle
            }
        }
    }

    fun resetState() {
        _voiceState.value = VoiceState.Idle
    }

    private fun cleanup() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("VoiceInteractionManager", "Error destroying speech recognizer", e)
        }
        speechRecognizer = null
    }
}
