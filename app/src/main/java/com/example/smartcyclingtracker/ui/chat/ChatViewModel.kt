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
    private val chatSessionDao: ChatSessionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentUser: UserEntity = UserEntity()
    private var recentSession: WorkoutSessionEntity? = null
    private var messageJob: Job? = null

    init {
        viewModelScope.launch {
            currentUser = userDao.getUser() ?: UserEntity()
            recentSession = sessionDao.getRecentSessions(1).firstOrNull()

            chatSessionDao.getAllSessions().collect { sessions ->
                _uiState.value = _uiState.value.copy(sessions = sessions)
                
                if (sessions.isEmpty()) {
                    createNewChatSession()
                } else if (_uiState.value.activeSessionId == null) {
                    switchSession(sessions.first().id)
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
            val dateStr = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val title = "Chat on $dateStr"
            val newSession = ChatSessionEntity(title = title)
            val id = chatSessionDao.insertSession(newSession)
            
            val greeting = "👋 Hey ${currentUser.name}! I'm VeloCoach, your AI cycling coach powered by Qwen2.5-72B. " +
                "I've analyzed your recent session. Ask me anything about your performance!"
            chatDao.insertMessage(ChatMessageEntity(role = "model", text = greeting, sessionId = id))
            
            switchSession(id)
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
        val sessionId = _uiState.value.activeSessionId ?: return
        if (userText.isBlank() || _uiState.value.isLoading) return

        // 1. Insert user message into DB. Flow collection will automatically update the UI list.
        viewModelScope.launch {
            chatDao.insertMessage(ChatMessageEntity(role = "user", text = userText, sessionId = sessionId))
            
            // Build history from current messages
            val history = _uiState.value.messages
                .filter { !it.isStreaming }
                .map { Pair(it.role, it.text) }

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            val systemPrompt = geminiRepository.buildSystemPrompt(currentUser, recentSession)

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
            val sessionId = _uiState.value.activeSessionId ?: return@launch
            chatDao.clearHistory(sessionId)
            _uiState.value = _uiState.value.copy(messages = emptyList(), error = null)
        }
    }

    fun shareRideHistory() {
        viewModelScope.launch {
            val sessions = sessionDao.getRecentSessions(5)
            if (sessions.isEmpty()) {
                sendMessage("I don't have any saved ride history yet. What should I focus on for my first ride?")
                return@launch
            }

            val sb = java.lang.StringBuilder()
            sb.append("Here is my recent ride history. Can you analyze my progress and give me some tips?\n\n")
            sessions.forEachIndexed { index, session ->
                val dist = "%.1f".format(session.totalDistanceMeters / 1000.0)
                val speed = "%.1f".format(session.avgSpeedKmh)
                val elev = "%.0f".format(session.elevationGainMeters)
                sb.append("Ride ${index + 1}: ${dist}km at ${speed}km/h, ${elev}m elevation gain.\n")
            }

            sendMessage(sb.toString().trim())
        }
    }
}
