package com.example.smartcyclingtracker.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartcyclingtracker.data.local.dao.UserDao
import com.example.smartcyclingtracker.data.local.dao.WorkoutSessionDao
import com.example.smartcyclingtracker.data.local.entity.UserEntity
import com.example.smartcyclingtracker.data.local.entity.WorkoutSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryUiState(
    val session: WorkoutSessionEntity? = null,
    val user: UserEntity = UserEntity(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val sessionDao: WorkoutSessionDao,
    private val userDao: UserDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: -1L

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            val user = userDao.getUser() ?: UserEntity()
            val session = if (sessionId > 0) {
                sessionDao.getSessionById(sessionId)
            } else {
                // Load most recent session (after a workout completes)
                sessionDao.getRecentSessions(1).firstOrNull()
            }
            _uiState.value = SummaryUiState(
                session = session,
                user = user,
                isLoading = false,
                error = if (session == null) "No session found" else null
            )
        }
    }
}
