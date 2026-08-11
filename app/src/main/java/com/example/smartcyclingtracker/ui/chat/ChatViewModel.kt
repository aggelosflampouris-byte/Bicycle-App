package com.example.smartcyclingtracker.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartcyclingtracker.data.local.dao.ChatMessageDao
import com.example.smartcyclingtracker.data.local.dao.UserDao
import com.example.smartcyclingtracker.data.local.dao.WorkoutSessionDao
import com.example.smartcyclingtracker.data.local.entity.ChatMessageEntity
import com.example.smartcyclingtracker.data.local.entity.ChatSessionEntity
import com.example.smartcyclingtracker.data.local.entity.UserEntity
import com.example.smartcyclingtracker.data.local.entity.WorkoutSessionEntity
import com.example.smartcyclingtracker.data.local.dao.ChatSessionDao
import com.example.smartcyclingtracker.data.local.SettingsRepository
import com.example.smartcyclingtracker.data.remote.GeminiRepository
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
    val sessions: List<ChatSessionEntity> = emptyList()
)

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
                settingsRepository.activityType.collect { type ->
                    currentActivityType = type
                }
            }

            chatSessionDao.getAllSessions().collect { sessions ->
                _uiState.value = _uiState.value.copy(sessions = sessions)
                
                if (_uiState.value.activeSessionId == null && _uiState.value.messages.isEmpty()) {
                    createNewChatSession()
                }
            }
        }
    }

    fun switchSession(sessionId: Long) {
        if (_uiState.value.activeSessionId == sessionId) return
        _uiState.value = _uiState.value.copy(activeSessionId = sessionId, messages = emptyList())
        
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            chatDao.getMessagesForSession(sessionId).collect { dbMessages ->
                _uiState.value = _uiState.value.copy(
                    messages = dbMessages.map { ChatMessage(role = it.role, text = it.text, isStreaming = false) }
                )
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
            val greeting = "👋 Hey ${currentUser.name}! I'm VeloCoach, your AI $activityName coach powered by Qwen2.5-72B. " +
                "I've analyzed your recent session. Ask me anything about your performance!"
            
            messageJob?.cancel()
            _uiState.value = _uiState.value.copy(
                activeSessionId = null,
                messages = listOf(
                    ChatMessage(role = "model", text = greeting, isStreaming = false)
                ),
                error = null
            )
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatSessionDao.deleteSession(sessionId)
            if (_uiState.value.activeSessionId == sessionId) {
                _uiState.value = _uiState.value.copy(activeSessionId = null)
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
                sessionId = chatSessionDao.insertSession(ChatSessionEntity(title = "Chat on $dateStr"))
                isNewSession = true
                
                val initialGreeting = _uiState.value.messages.firstOrNull()?.text ?: ""
                if (initialGreeting.isNotEmpty()) {
                    chatDao.insertMessage(ChatMessageEntity(role = "model", text = initialGreeting, sessionId = sessionId))
                }
            }

            chatDao.insertMessage(ChatMessageEntity(role = "user", text = userText, sessionId = sessionId))
            
            if (isNewSession) {
                _uiState.value = _uiState.value.copy(activeSessionId = sessionId)
                messageJob?.cancel()
                messageJob = viewModelScope.launch {
                    chatDao.getMessagesForSession(sessionId).collect { dbMessages ->
                        _uiState.value = _uiState.value.copy(
                            messages = dbMessages.map { ChatMessage(role = it.role, text = it.text, isStreaming = false) }
                        )
                    }
                }
            }

            // Build history from current messages
            val history = _uiState.value.messages
                .filter { !it.isStreaming }
                .map { Pair(it.role, it.text) }

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            val systemPrompt = geminiRepository.buildSystemPrompt(currentUser, recentSession, currentActivityType)

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
                
                // 2. Insert bot response into DB
                chatDao.insertMessage(ChatMessageEntity(role = "model", text = finalResponse, sessionId = sessionId))
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                val errorMessage = "⚠️ **Unexpected error** — ${e.message}\n\nPlease try again."
                // Only show error visually, don't persist network failures to history
                val errorMsgObj = ChatMessage(role = "model", text = errorMessage, isStreaming = false)
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + errorMsgObj,
                    isLoading = false
                )
            }
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

    fun shareRideHistory() {
        viewModelScope.launch {
            val activityName = when (currentActivityType) {
                "WALKING" -> "walk"
                "JOGGING" -> "run"
                else -> "ride"
            }
            val sessions = sessionDao.getRecentSessions(5)
            if (sessions.isEmpty()) {
                sendMessage("I don't have any saved $activityName history yet. What should I focus on for my first $activityName?")
                return@launch
            }

            val sb = java.lang.StringBuilder()
            sb.append("Here is my recent $activityName history. Can you analyze my progress and give me some tips?\n\n")
            sessions.forEachIndexed { index, session ->
                val dist = "%.1f".format(session.totalDistanceMeters / 1000.0)
                val speed = "%.1f".format(session.avgSpeedKmh)
                val elev = "%.0f".format(session.elevationGainMeters)
                sb.append("${activityName.replaceFirstChar { it.uppercase() }} ${index + 1}: ${dist}km at ${speed}km/h, ${elev}m elevation gain.\n")
            }

            sendMessage(sb.toString().trim())
        }
    }
}
