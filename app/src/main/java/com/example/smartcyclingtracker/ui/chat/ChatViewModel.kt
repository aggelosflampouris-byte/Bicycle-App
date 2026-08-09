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
            val greeting = "👋 Hey ${currentUser.name}! I'm VeloCoach, your AI cycling coach powered by Qwen2.5. " +
                "I've analyzed your recent session. Ask me anything about your performance!"
            _uiState.value = _uiState.value.copy(
                messages = listOf(ChatMessage(role = "model", text = greeting))
            )
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _uiState.value.isLoading) return

        val userMessage = ChatMessage(role = "user", text = userText)

        // Build history from confirmed (non-streaming) messages only
        val history = _uiState.value.messages
            .filter { !it.isStreaming }
            .map { Pair(it.role, it.text) }

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isLoading = true,
            error = null
        )

        val systemPrompt = geminiRepository.buildSystemPrompt(currentUser, recentSession)

        viewModelScope.launch {
            try {
                // HF API returns a single response (not streaming).
                // We still use the Flow<String> interface but expect exactly one emission.
                var responseText = ""
                geminiRepository.streamChat(
                    userMessage = userText,
                    systemPrompt = systemPrompt,
                    history = history
                ).collect { chunk ->
                    responseText += chunk
                }

                val responseMessage = ChatMessage(
                    role = "model",
                    text = responseText.ifBlank { "I'm having trouble responding right now. Please try again." },
                    isStreaming = false
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + responseMessage,
                    isLoading = false
                )
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    role = "model",
                    text = "⚠️ **Unexpected error** — ${e.message}\n\nPlease try again.",
                    isStreaming = false
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + errorMessage,
                    isLoading = false
                )
            }
        }
    }

    fun clearChat() {
        _uiState.value = ChatUiState()
        // Re-show greeting after clear
        viewModelScope.launch {
            val greeting = "👋 Hey ${currentUser.name}! I'm VeloCoach, your AI cycling coach powered by Qwen2.5. " +
                "Ask me anything about cycling performance!"
            _uiState.value = _uiState.value.copy(
                messages = listOf(ChatMessage(role = "model", text = greeting))
            )
        }
    }
}
