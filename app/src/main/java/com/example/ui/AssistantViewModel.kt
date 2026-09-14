package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AssistantDatabase
import com.example.data.model.AlarmEntity
import com.example.data.model.CalendarEventEntity
import com.example.data.model.CommandHistoryEntity
import com.example.data.model.CommandStatus
import com.example.data.model.ContactEntity
import com.example.data.model.ReminderEntity
import com.example.data.model.TimerEntity
import com.example.data.repository.AssistantRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.UserSettings
import com.example.engine.ConversationManager
import com.example.engine.IntentType
import com.example.engine.NlpEngine
import com.example.engine.ParsedIntent
import com.example.engine.PendingAction
import com.example.service.ActionResult
import com.example.service.DeviceActionHandler
import com.example.service.NotificationHelper
import com.example.service.SystemAlarmScheduler
import com.example.voice.ContinuousSpeechRecognizerHelper
import com.example.voice.SpeechRecognizerHelper
import com.example.voice.TextToSpeechHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val intentName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val actionResult: ActionResult? = null
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    val database = AssistantDatabase.getDatabase(application)
    val repository = AssistantRepository(database)
    val settingsRepository = SettingsRepository(application)
    private val alarmScheduler = SystemAlarmScheduler(application)
    val actionHandler = DeviceActionHandler(application, repository, alarmScheduler)
    private val conversationManager = ConversationManager()

    // Speech & TTS
    val continuousSpeechRecognizer = ContinuousSpeechRecognizerHelper(application) { recognizedText ->
        processUserInput(recognizedText)
    }
    val speechRecognizer = continuousSpeechRecognizer
    val tts = TextToSpeechHelper(application)

    // UI States
    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                isUser = false,
                text = "Hi! I'm Aura, your AI voice assistant. Try saying 'Set an alarm for 7 AM', 'Start a 15 minute timer', or 'Kal subah reminder laga do'."
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<Pair<String, ParsedIntent>?>(null)
    val pendingConfirmation: StateFlow<Pair<String, ParsedIntent>?> = _pendingConfirmation.asStateFlow()

    private val _ambiguousContacts = MutableStateFlow<List<ContactEntity>?>(null)
    val ambiguousContacts: StateFlow<List<ContactEntity>?> = _ambiguousContacts.asStateFlow()

    private val _currentDestination = MutableStateFlow("home")
    val currentDestination: StateFlow<String> = _currentDestination.asStateFlow()

    private var lastUserQuery: String = ""

    // Room Database Flows
    val allAlarms: StateFlow<List<AlarmEntity>> = repository.allAlarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTimers: StateFlow<List<TimerEntity>> = repository.allTimers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTimer: StateFlow<TimerEntity?> = repository.activeTimer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allReminders: StateFlow<List<ReminderEntity>> = repository.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingReminders: StateFlow<List<ReminderEntity>> = repository.pendingReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEvents: StateFlow<List<CalendarEventEntity>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allContacts: StateFlow<List<ContactEntity>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteContacts: StateFlow<List<ContactEntity>> = repository.favoriteContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentHistory: StateFlow<List<CommandHistoryEntity>> = repository.getRecentHistory(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<UserSettings> = settingsRepository.settings

    private var timerTickerJob: Job? = null

    init {
        NotificationHelper.createNotificationChannels(application)
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }
        viewModelScope.launch {
            tts.isSpeaking.collect { isSpeaking ->
                if (!isSpeaking && continuousSpeechRecognizer.isContinuousMode.value) {
                    continuousSpeechRecognizer.resumeAfterTts()
                }
            }
        }
        startTimerCountdownLoop()
    }

    fun setDestination(dest: String) {
        _currentDestination.value = dest
    }

    fun processUserInput(text: String) {
        if (text.isBlank()) return
        lastUserQuery = text

        // Add user message to conversation
        addMessage(ChatMessage(isUser = true, text = text))

        viewModelScope.launch {
            // Check if there is active pending conversation context
            val followUp = conversationManager.processFollowUp(text)
            if (followUp.second != null) {
                // Conversational feedback from manager
                val reply = followUp.second!!
                addMessage(ChatMessage(isUser = false, text = reply))
                speakIfNeeded(reply)
                return@launch
            }

            val parsedIntent = followUp.first ?: NlpEngine.parse(text)

            // Handle ambiguous contacts or confirmation
            val result = actionHandler.execute(parsedIntent)
            handleActionResult(result, parsedIntent)
        }
    }

    private fun handleActionResult(result: ActionResult, intent: ParsedIntent) {
        when (result) {
            is ActionResult.Success -> {
                _pendingConfirmation.value = null
                _ambiguousContacts.value = null
                conversationManager.clear()
                addMessage(
                    ChatMessage(
                        isUser = false,
                        text = result.responseMessage,
                        intentName = intent.intent.name,
                        actionResult = result
                    )
                )
                speakIfNeeded(result.responseMessage)
            }
            is ActionResult.RequiresConfirmation -> {
                _pendingConfirmation.value = Pair(result.prompt, result.pendingIntent)
                conversationManager.setPendingAction(
                    PendingAction(
                        intent = intent.intent,
                        partialIntent = result.pendingIntent,
                        awaitingConfirmation = true,
                        confirmationPrompt = result.prompt
                    )
                )
                addMessage(
                    ChatMessage(
                        isUser = false,
                        text = result.prompt,
                        intentName = intent.intent.name,
                        actionResult = result
                    )
                )
                speakIfNeeded(result.prompt)
            }
            is ActionResult.AmbiguousContacts -> {
                _ambiguousContacts.value = result.matchedContacts
                addMessage(
                    ChatMessage(
                        isUser = false,
                        text = result.prompt,
                        intentName = intent.intent.name,
                        actionResult = result
                    )
                )
                speakIfNeeded(result.prompt)
            }
            is ActionResult.MissingInformation -> {
                conversationManager.setPendingAction(
                    PendingAction(
                        intent = intent.intent,
                        partialIntent = intent,
                        awaitingSlot = result.missingSlot
                    )
                )
                addMessage(
                    ChatMessage(
                        isUser = false,
                        text = result.prompt,
                        intentName = intent.intent.name,
                        actionResult = result
                    )
                )
                speakIfNeeded(result.prompt)
            }
            is ActionResult.Error -> {
                conversationManager.clear()
                addMessage(
                    ChatMessage(
                        isUser = false,
                        text = result.errorMessage,
                        intentName = intent.intent.name,
                        actionResult = result
                    )
                )
                speakIfNeeded(result.errorMessage)
            }
        }
    }

    fun confirmAction() {
        val pending = _pendingConfirmation.value ?: return
        val confirmedIntent = pending.second.copy(requiresConfirmation = false)
        _pendingConfirmation.value = null
        conversationManager.clear()

        viewModelScope.launch {
            val result = actionHandler.execute(confirmedIntent)
            handleActionResult(result, confirmedIntent)
        }
    }

    fun cancelPendingAction() {
        _pendingConfirmation.value = null
        _ambiguousContacts.value = null
        conversationManager.clear()
        val msg = "Cancelled."
        addMessage(ChatMessage(isUser = false, text = msg))
        speakIfNeeded(msg)
    }

    fun selectAmbiguousContact(contact: ContactEntity) {
        _ambiguousContacts.value = null
        viewModelScope.launch {
            val res = actionHandler.initiateRealCall(contact.name, contact.phoneNumber, "Call ${contact.name}")
            when (res) {
                is ActionResult.Success -> {
                    addMessage(ChatMessage(isUser = false, text = res.responseMessage, actionResult = res))
                    speakIfNeeded(res.responseMessage)
                }
                is ActionResult.Error -> {
                    addMessage(ChatMessage(isUser = false, text = res.errorMessage, actionResult = res))
                    speakIfNeeded(res.errorMessage)
                }
                else -> {}
            }
        }
    }

    fun retryLastCommand() {
        if (lastUserQuery.isNotBlank()) {
            processUserInput(lastUserQuery)
        }
    }

    private fun addMessage(msg: ChatMessage) {
        _messages.value = _messages.value + msg
    }

    private fun speakIfNeeded(text: String) {
        if (settings.value.voiceOutputEnabled && settings.value.autoSpeakResponses) {
            continuousSpeechRecognizer.pauseForTts()
            tts.speak(text, settings.value.language)
        }
    }

    // ==========================================
    // ALARM CRUD
    // ==========================================
    fun toggleAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = !alarm.isEnabled)
            repository.updateAlarm(updated)
            if (updated.isEnabled) {
                alarmScheduler.scheduleAlarm(updated)
            } else {
                alarmScheduler.cancelAlarm(alarm.id)
            }
        }
    }

    fun saveAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            if (alarm.id == 0L) {
                val id = repository.insertAlarm(alarm)
                alarmScheduler.scheduleAlarm(alarm.copy(id = id))
            } else {
                repository.updateAlarm(alarm)
                if (alarm.isEnabled) {
                    alarmScheduler.scheduleAlarm(alarm)
                } else {
                    alarmScheduler.cancelAlarm(alarm.id)
                }
            }
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm(alarm.id)
            repository.deleteAlarm(alarm)
        }
    }

    // ==========================================
    // TIMER CRUD & TICKER
    // ==========================================
    fun startNewTimer(seconds: Long, label: String = "Timer") {
        viewModelScope.launch {
            val timer = TimerEntity(
                label = label,
                totalSeconds = seconds,
                remainingSeconds = seconds,
                isRunning = true,
                isPaused = false
            )
            repository.insertTimer(timer)
        }
    }

    fun pauseTimer(timer: TimerEntity) {
        viewModelScope.launch {
            repository.updateTimer(timer.copy(isRunning = false, isPaused = true))
        }
    }

    fun resumeTimer(timer: TimerEntity) {
        viewModelScope.launch {
            repository.updateTimer(timer.copy(isRunning = true, isPaused = false))
        }
    }

    fun resetTimer(timer: TimerEntity) {
        viewModelScope.launch {
            repository.updateTimer(timer.copy(remainingSeconds = timer.totalSeconds, isRunning = false, isPaused = false))
        }
    }

    fun deleteTimer(timer: TimerEntity) {
        viewModelScope.launch {
            repository.deleteTimer(timer)
        }
    }

    private fun startTimerCountdownLoop() {
        timerTickerJob?.cancel()
        timerTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val currentTimers = repository.allTimers.first()
                for (timer in currentTimers) {
                    if (timer.isRunning && !timer.isPaused && timer.remainingSeconds > 0) {
                        val newRemaining = timer.remainingSeconds - 1
                        if (newRemaining == 0L) {
                            repository.updateTimer(timer.copy(remainingSeconds = 0, isRunning = false, isPaused = false))
                            NotificationHelper.showNotification(
                                getApplication(),
                                NotificationHelper.CHANNEL_TIMERS,
                                timer.id.toInt(),
                                "Timer Finished!",
                                "${timer.label} has finished countdown."
                            )
                            val alertMsg = "${timer.label} has finished!"
                            addMessage(ChatMessage(isUser = false, text = alertMsg))
                            speakIfNeeded(alertMsg)
                        } else {
                            repository.updateTimer(timer.copy(remainingSeconds = newRemaining))
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // REMINDER CRUD
    // ==========================================
    fun saveReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            if (reminder.id == 0L) {
                repository.insertReminder(reminder)
            } else {
                repository.updateReminder(reminder)
            }
        }
    }

    fun toggleReminderComplete(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.updateReminder(reminder.copy(isCompleted = !reminder.isCompleted))
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
        }
    }

    // ==========================================
    // CALENDAR CRUD
    // ==========================================
    fun saveEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            if (event.id == 0L) {
                repository.insertEvent(event)
            } else {
                repository.updateEvent(event)
            }
        }
    }

    fun deleteEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            repository.deleteEvent(event)
        }
    }

    // ==========================================
    // CONTACTS & CALLING
    // ==========================================
    fun callContact(contact: ContactEntity) {
        viewModelScope.launch {
            val res = actionHandler.initiateRealCall(contact.name, contact.phoneNumber, "Call ${contact.name}")
            val parsed = ParsedIntent(
                intent = IntentType.CALL_CONTACT,
                rawQuery = "Call ${contact.name}",
                contactName = contact.name,
                phoneNumber = contact.phoneNumber
            )
            handleActionResult(res, parsed)
        }
    }

    fun callNumber(phoneNumber: String) {
        viewModelScope.launch {
            val res = actionHandler.initiateRealCall(phoneNumber, phoneNumber, "Call $phoneNumber")
            val parsed = ParsedIntent(
                intent = IntentType.CALL_NUMBER,
                rawQuery = "Call $phoneNumber",
                phoneNumber = phoneNumber
            )
            handleActionResult(res, parsed)
        }
    }

    fun saveContact(contact: ContactEntity) {
        viewModelScope.launch {
            if (contact.id == 0L) {
                repository.insertContact(contact)
            } else {
                repository.updateContact(contact)
            }
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }

    fun toggleFavoriteContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.updateContact(contact.copy(isFavorite = !contact.isFavorite))
        }
    }

    fun syncDeviceContacts() {
        viewModelScope.launch {
            val count = repository.importDeviceContacts(getApplication())
            val msg = if (count > 0) "Synced $count contacts from your device." else "No new contacts synced from device."
            addMessage(ChatMessage(isUser = false, text = msg))
            speakIfNeeded(msg)
        }
    }

    // ==========================================
    // HISTORY & SETTINGS
    // ==========================================
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _messages.value = listOf(
                ChatMessage(
                    isUser = false,
                    text = "History cleared. How can I help you next?"
                )
            )
        }
    }

    fun toggleContinuousListening() {
        val lang = if (settings.value.language == "hi" || settings.value.language == "hinglish") "hi-IN" else "en-US"
        if (continuousSpeechRecognizer.isContinuousMode.value) {
            continuousSpeechRecognizer.stopListening()
        } else {
            continuousSpeechRecognizer.startContinuousListening(lang)
        }
    }

    override fun onCleared() {
        super.onCleared()
        continuousSpeechRecognizer.destroy()
        tts.shutdown()
        timerTickerJob?.cancel()
    }
}
