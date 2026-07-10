package com.example.detectionapplication

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Wraps Android's built-in TextToSpeech engine.
 *
 * WHY ANDROID TTS?
 *  - Fully offline — works with no internet connection
 *  - Built into every Android device (API 4+), zero extra dependencies
 *  - Zero network latency — speaks instantly
 *  - Free — no API keys or quotas
 *
 * COOLDOWN:
 *  Prevents the same phrase from being repeated within [cooldownMs] milliseconds.
 *  e.g. if a person is detected in every frame, TTS only fires once every 4 seconds.
 */
class TtsManager(context: Context) {

    // Cooldown per spoken phrase — prevents spam when object is detected every frame
    private val cooldownMs = 4000L

    // Tracks the last time each phrase was spoken  (phrase → timestamp)
    private val lastSpokenAt = mutableMapOf<String, Long>()

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                isReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED
                if (isReady) {
                    tts?.setSpeechRate(0.95f)   // slightly slower = clearer for alerts
                    tts?.setPitch(1.0f)
                    Log.i("TtsManager", "TTS ready")
                } else {
                    Log.w("TtsManager", "TTS language not supported")
                }
            } else {
                Log.e("TtsManager", "TTS initialisation failed: $status")
            }
        }
    }

    /**
     * Speak [phrase] if:
     *  1. TTS engine is ready
     *  2. The same phrase hasn't been spoken within [cooldownMs]
     *
     * Uses QUEUE_FLUSH so a new alert interrupts any currently playing speech,
     * keeping announcements timely rather than queuing up a backlog.
     */
    fun speak(phrase: String) {
        if (!isReady) return

        val now  = System.currentTimeMillis()
        val last = lastSpokenAt[phrase] ?: 0L

        if (now - last < cooldownMs) return   // still in cooldown

        lastSpokenAt[phrase] = now
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, phrase)
        Log.d("TtsManager", "Speaking: $phrase")
    }

    /** Call from Activity.onDestroy() to release TTS resources. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        Log.i("TtsManager", "TTS shut down")
    }
}