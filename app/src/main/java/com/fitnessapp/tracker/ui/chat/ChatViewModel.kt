package com.fitnessapp.tracker.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.tracker.data.local.dao.ChatMessageDao
import com.fitnessapp.tracker.data.local.dao.UserDao
import com.fitnessapp.tracker.data.local.dao.WorkoutSessionDao
import com.fitnessapp.tracker.data.local.entity.ChatMessageEntity
import com.fitnessapp.tracker.data.local.entity.ChatSessionEntity
import com.fitnessapp.tracker.data.local.entity.UserEntity
import com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity
import com.fitnessapp.tracker.data.local.dao.ChatSessionDao
import com.fitnessapp.tracker.data.local.SettingsRepository
import com.fitnessapp.tracker.data.remote.GeminiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,  // "user" or "model"
    val text: String,
    val isStreaming: Boolean = false
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val streamingText: String = "",
    val activeSessionId: Long? = null,
    val sessions: List<ChatSessionEntity> = emptyList(),
    val showSessionPicker: Boolean = false,
    val availableSessions: List<WorkoutSessionEntity> = emptyList(),
    val coachPersona: com.fitnessapp.tracker.data.local.CoachPersona = com.fitnessapp.tracker.data.local.CoachPersona.SUPPORTIVE,
    val coachLanguage: com.fitnessapp.tracker.data.local.CoachLanguage = com.fitnessapp.tracker.data.local.CoachLanguage.AUTO,
    val voiceCoachingEnabled: Boolean = true
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val userDao: UserDao,
    private val sessionDao: WorkoutSessionDao,
    private val chatDao: ChatMessageDao,
    private val chatSessionDao: ChatSessionDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentUser: UserEntity = UserEntity()
    private var recentSession: WorkoutSessionEntity? = null
    private var messageJob: Job? = null
    private var currentActivityType: String = "CYCLING"

    init {
        viewModelScope.launch {
            currentUser = userDao.getUser() ?: UserEntity()
            recentSession = sessionDao.getRecentSessions(1).firstOrNull()
            currentActivityType = settingsRepository.activityType.first()

            launch {
                settingsRepository.activityType.flatMapLatest { type ->
                    currentActivityType = type
                    // Reset active chat when switching modes
                    _uiState.update { it.copy(activeSessionId = null, messages = emptyList()) }
                    chatSessionDao.getSessionsByActivity(type)
                }.collect { sessions ->
                    _uiState.update { it.copy(sessions = sessions) }
                    
                    if (_uiState.value.activeSessionId == null && _uiState.value.messages.isEmpty()) {
                        createNewChatSession()
                    }
                }
            }

            launch {
                combine(
                    settingsRepository.coachPersona,
                    settingsRepository.coachLanguage,
                    settingsRepository.isVoiceCoachingEnabled
                ) { persona, language, voiceEnabled ->
                    Triple(persona, language, voiceEnabled)
                }.collect { (persona, language, voiceEnabled) ->
                    _uiState.update { it.copy(
                        coachPersona = persona,
                        coachLanguage = language,
                        voiceCoachingEnabled = voiceEnabled
                    ) }
                }
            }
        }
    }

    fun switchSession(sessionId: Long) {
        if (_uiState.value.activeSessionId == sessionId) return
        _uiState.update { it.copy(activeSessionId = sessionId, messages = emptyList()) }
        
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            chatDao.getMessagesForSession(sessionId).collect { dbMessages ->
                _uiState.update { state ->
                    state.copy(
                        messages = dbMessages.map { ChatMessage(role = it.role, text = it.text, isStreaming = false) }
                    )
                }
            }
        }
    }

    fun createNewChatSession() {
        viewModelScope.launch {
            val activityName = when (currentActivityType) {
                "WALKING" -> "walking"
                "JOGGING" -> "jogging"
                else -> "cycling"
            }
            val greeting = "👋 Hey ${currentUser.name}! I'm your AI Coach, here to help you improve your $activityName performance. " +
                "I've analyzed your recent session. Ask me anything about your performance!"
            
            messageJob?.cancel()
            _uiState.update { it.copy(
                activeSessionId = null,
                messages = listOf(
                    ChatMessage(role = "model", text = greeting, isStreaming = false)
                ),
                error = null
            ) }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatSessionDao.deleteSession(sessionId)
            _uiState.update { state ->
                if (state.activeSessionId == sessionId) {
                    state.copy(activeSessionId = null)
                } else {
                    state
                }
            }
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _uiState.value.isLoading) return

        viewModelScope.launch {
            var sessionId = _uiState.value.activeSessionId
            var isNewSession = false
            
            if (sessionId == null) {
                val dateStr = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                sessionId = chatSessionDao.insertSession(ChatSessionEntity(title = "Chat on $dateStr", activityType = currentActivityType))
                isNewSession = true
                
                val initialGreeting = _uiState.value.messages.firstOrNull()?.text ?: ""
                if (initialGreeting.isNotEmpty()) {
                    chatDao.insertMessage(ChatMessageEntity(role = "model", text = initialGreeting, sessionId = sessionId))
                }
            }

            chatDao.insertMessage(ChatMessageEntity(role = "user", text = userText, sessionId = sessionId))
            
            if (isNewSession) {
                _uiState.update { it.copy(activeSessionId = sessionId) }
                messageJob?.cancel()
                messageJob = viewModelScope.launch {
                    chatDao.getMessagesForSession(sessionId).collect { dbMessages ->
                        _uiState.update { state ->
                            state.copy(
                                messages = dbMessages.map { ChatMessage(role = it.role, text = it.text, isStreaming = false) }
                            )
                        }
                    }
                }
            }

            // Build history from current messages
            val history = _uiState.value.messages
                .filter { !it.isStreaming }
                .map { Pair(it.role, it.text) }

            _uiState.update { it.copy(
                isLoading = true,
                error = null
            ) }

            val persona = _uiState.value.coachPersona
            val language = _uiState.value.coachLanguage
            val systemPrompt = geminiRepository.buildSystemPrompt(currentUser, recentSession, currentActivityType, persona, language)

            try {
                var responseText = ""
                geminiRepository.streamChat(
                    userMessage = userText,
                    systemPrompt = systemPrompt,
                    history = history
                ).collect { chunk ->
                    responseText += chunk
                }

                val finalResponse = responseText.ifBlank { "I'm having trouble responding right now. Please try again." }
                
                // Abort if the user cleared or switched the session while we were generating the response
                if (_uiState.value.activeSessionId != sessionId) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                
                // 2. Insert bot response into DB
                chatDao.insertMessage(ChatMessageEntity(role = "model", text = finalResponse, sessionId = sessionId))
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                // Abort if the session was deleted to prevent polluting the new session with old errors
                if (_uiState.value.activeSessionId != sessionId) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                
                val errorMessage = "⚠️ **Unexpected error** — ${e.message}\n\nPlease try again."
                // Only show error visually, don't persist network failures to history
                val errorMsgObj = ChatMessage(role = "model", text = errorMessage, isStreaming = false)
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + errorMsgObj,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun triggerFatigueAnalysis() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            val sessions = sessionDao.getRecentSessionsByType(currentActivityType, 30)
            val persona = settingsRepository.coachPersona.first()
            
            // Insert user query
            val queryText = "📈 Please analyze my multi-week fatigue level, recovery status, and milestone progression."
            sendMessage(queryText)
        }
    }

    fun triggerPreRideBriefing(
        targetDistanceKm: Double,
        weatherCondition: String,
        tempC: Double,
        windSpeedKmh: Double,
        windDirection: String
    ) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            val prompt = "🌦️ Pre-Ride Briefing Request: Target distance is ${"%.1f".format(targetDistanceKm)} km. Weather is $weatherCondition, ${"%.0f".format(tempC)}°C, wind ${"%.1f".format(windSpeedKmh)} km/h from $windDirection. Give me a pacing and nutrition/hydration strategy."
            sendMessage(prompt)
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            val sessionId = _uiState.value.activeSessionId
            if (sessionId != null) {
                chatDao.clearHistory(sessionId)
                chatSessionDao.deleteSession(sessionId)
            }
            createNewChatSession()
        }
    }

    fun openSessionPicker() {
        viewModelScope.launch {
            val sessions = sessionDao.getRecentSessionsByType(currentActivityType, 10)
            if (sessions.isEmpty()) {
                val activityName = when (currentActivityType) {
                    "WALKING" -> "walk"
                    "JOGGING" -> "run"
                    else -> "ride"
                }
                sendMessage("I don't have any saved $activityName history yet. What should I focus on for my first $activityName?")
            } else {
                _uiState.update { it.copy(availableSessions = sessions, showSessionPicker = true) }
            }
        }
    }

    fun dismissSessionPicker() {
        _uiState.update { it.copy(showSessionPicker = false) }
    }

    fun shareSelectedSessions(selectedIds: Set<Long>) {
        viewModelScope.launch {
            val sessionsToShare = _uiState.value.availableSessions
                .filter { it.id in selectedIds }
                .sortedBy { it.startTime }
            _uiState.update { it.copy(showSessionPicker = false) }
            
            if (sessionsToShare.isEmpty()) return@launch

            val activityName = when (currentActivityType) {
                "WALKING" -> "walk"
                "JOGGING" -> "run"
                else -> "ride"
            }

            val sb = java.lang.StringBuilder()
            sb.append("Here are some of my recent $activityName sessions. Can you analyze my progress and give me some tips?\n\n")
            sessionsToShare.forEachIndexed { index, session ->
                val dist = "%.1f".format(session.totalDistanceMeters / 1000.0)
                val speed = "%.1f".format(session.avgSpeedKmh)
                val elev = "%.0f".format(session.elevationGainMeters)
                sb.append("${activityName.replaceFirstChar { it.uppercase() }} ${index + 1}: ${dist}km at ${speed}km/h, ${elev}m elevation gain.\n")
            }

            sendMessage(sb.toString().trim())
        }
    }

    fun setCoachPersona(persona: com.fitnessapp.tracker.data.local.CoachPersona) {
        viewModelScope.launch { settingsRepository.setCoachPersona(persona) }
    }

    fun setCoachLanguage(language: com.fitnessapp.tracker.data.local.CoachLanguage) {
        viewModelScope.launch { settingsRepository.setCoachLanguage(language) }
    }

    fun setVoiceCoachingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVoiceCoachingEnabled(enabled) }
    }
}
