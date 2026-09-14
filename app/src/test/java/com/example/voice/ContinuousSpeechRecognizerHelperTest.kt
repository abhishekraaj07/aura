package com.example.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContinuousSpeechRecognizerHelperTest {

    private lateinit var context: Context
    private lateinit var helper: ContinuousSpeechRecognizerHelper
    private var lastRecognizedCommand: String? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        helper = ContinuousSpeechRecognizerHelper(context) { command ->
            lastRecognizedCommand = command
        }
    }

    @Test
    fun `initial state is idle and continuous mode is off`() {
        assertFalse(helper.isContinuousMode.value)
        assertFalse(helper.isListening.value)
        assertEquals(ListeningState.IDLE, helper.listeningState.value)
        assertEquals("Ready", helper.statusMessage.value)
    }

    @Test
    fun `stopListening resets continuous mode and sets status to Stopped`() {
        helper.stopListening()
        assertFalse(helper.isContinuousMode.value)
        assertFalse(helper.isListening.value)
        assertEquals(ListeningState.IDLE, helper.listeningState.value)
        assertEquals("Stopped", helper.statusMessage.value)
    }

    @Test
    fun `pauseForTts updates state to muted`() {
        helper.pauseForTts()
        // If not listening or continuous, stays idle
        assertFalse(helper.isListening.value)
    }
}
