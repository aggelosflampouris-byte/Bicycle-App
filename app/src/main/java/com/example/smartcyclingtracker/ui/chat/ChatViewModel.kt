package com.example.smartcyclingtracker.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartcyclingtracker.data.local.dao.UserDao
import com.example.smartcyclingtracker.data.local.dao.WorkoutSessionDao
import com.example.smartcyclingtracker.data.local.entity.UserEntity
import com.example.smartcyclingtracker.data.local.entity.WorkoutSessionEntity
import com.example.smartcyclingtracker.data.remote.GeminiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
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
    private val sessionDao: WorkoutSessionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentUser: UserEntity = UserEntity()
    private var recentSession: WorkoutSessionEntity? = null

    init {
        viewModelScope.launch {
            currentUser = userDao.getUser() ?: UserEntity()
            recentSession = sessionDao.getRecentSessions(1).firstOrNull()

            // Add greeting from VeloCoach
            val greeting = "👋 Hey ${currentUser.name}! I'm VeloCoach, your AI cycling coach. " +
                "I've analyzed your recent session. Ask me anything about your performance!"
            _uiState.value = _uiState.value.copy(
                messages = listOf(ChatMessage(role = "model", text = greeting))
            )
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _uiState.value.isLoading) return

        val userMessage = ChatMessage(role = "user", text = userText)
        val history = _uiState.value.messages
            .filter { !it.isStreaming }
            .map { Pair(it.role, it.text) }

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isLoading = true,
            streamingText = "",
            error = null
        )

        val systemPrompt = geminiRepository.buildSystemPrompt(currentUser, recentSession)

        viewModelScope.launch {
            var streamedText = ""
            val streamingMsg = ChatMessage(role = "model", text = "", isStreaming = true)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + streamingMsg
            )

            geminiRepository.streamChat(
                userMessage = userText,
                systemPrompt = systemPrompt,
                history = history
            ).collect { chunk ->
                streamedText += chunk
                // Update the last message (streaming placeholder) with accumulated text
                val updatedMessages = _uiState.value.messages.toMutableList()
                updatedMessages[updatedMessages.lastIndex] = streamingMsg.copy(
                    text = streamedText,
                    isStreaming = true
                )
                _uiState.value = _uiState.value.copy(messages = updatedMessages)
            }

            // Finalize streaming message
            val finalMessages = _uiState.value.messages.toMutableList()
            finalMessages[finalMessages.lastIndex] = ChatMessage(
                role = "model",
                text = streamedText.ifBlank { "I'm having trouble responding right now. Please try again." },
                isStreaming = false
            )
            _uiState.value = _uiState.value.copy(
                messages = finalMessages,
                isLoading = false
            )
        }
    }

    fun clearChat() {
        _uiState.value = ChatUiState()
    }
}
