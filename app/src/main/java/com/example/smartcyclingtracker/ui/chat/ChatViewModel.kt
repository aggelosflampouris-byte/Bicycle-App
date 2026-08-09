package com.example.smartcyclingtracker.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartcyclingtracker.data.local.dao.ChatMessageDao
import com.example.smartcyclingtracker.data.local.dao.UserDao
import com.example.smartcyclingtracker.data.local.dao.WorkoutSessionDao
import com.example.smartcyclingtracker.data.local.entity.ChatMessageEntity
import com.example.smartcyclingtracker.data.local.entity.UserEntity
import com.example.smartcyclingtracker.data.local.entity.WorkoutSessionEntity
import com.example.smartcyclingtracker.data.remote.GeminiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val streamingText: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val userDao: UserDao,
    private val sessionDao: WorkoutSessionDao,
    private val chatDao: ChatMessageDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentUser: UserEntity = UserEntity()
    private var recentSession: WorkoutSessionEntity? = null

    init {
        viewModelScope.launch {
            currentUser = userDao.getUser() ?: UserEntity()
            recentSession = sessionDao.getRecentSessions(1).firstOrNull()

            // Collect history from DB
            chatDao.getAllMessages().collect { dbMessages ->
                if (dbMessages.isEmpty()) {
                    // Insert greeting if DB is empty
                    val greeting = "👋 Hey ${currentUser.name}! I'm VeloCoach, your AI cycling coach powered by Phi-3.5. " +
                        "I've analyzed your recent session. Ask me anything about your performance!"
                    chatDao.insertMessage(ChatMessageEntity(role = "model", text = greeting))
                } else {
                    _uiState.value = _uiState.value.copy(
                        messages = dbMessages.map { ChatMessage(role = it.role, text = it.text, isStreaming = false) }
                    )
                }
            }
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _uiState.value.isLoading) return

        // 1. Insert user message into DB. Flow collection will automatically update the UI list.
        viewModelScope.launch {
            chatDao.insertMessage(ChatMessageEntity(role = "user", text = userText))
            
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
                chatDao.insertMessage(ChatMessageEntity(role = "model", text = finalResponse))
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
            chatDao.clearHistory()
            _uiState.value = _uiState.value.copy(messages = emptyList(), error = null)
        }
    }
}
