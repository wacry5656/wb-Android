package com.wrongbook.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wrongbook.app.WrongBookApp
import com.wrongbook.app.data.repository.QuestionRepository
import com.wrongbook.app.model.Question
import com.wrongbook.app.sync.QuestionSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val dueReviewCount: Int = 0,
    val totalCount: Int = 0,
    val analyzedCount: Int = 0,
    val reviewedCount: Int = 0,
    val recentQuestions: List<Question> = emptyList(),
    val categories: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null
)

class HomeViewModel(
    private val repository: QuestionRepository,
    private val syncService: QuestionSyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    private val _syncState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getDueForReviewCount(System.currentTimeMillis()),
                repository.getAllActive(),
                repository.getRecentlyAdded(5),
                repository.getAllCategories(),
                _syncState
            ) { count, allQuestions, recent, categories, syncState ->
                HomeUiState(
                    dueReviewCount = count,
                    totalCount = allQuestions.size,
                    analyzedCount = allQuestions.count { it.analysis != null },
                    reviewedCount = allQuestions.count { it.reviewCount > 0 },
                    recentQuestions = recent,
                    categories = categories,
                    isLoading = false,
                    isSyncing = syncState.isSyncing,
                    syncMessage = syncState.message
                )
            }.collect { _uiState.value = it }
        }
    }

    fun syncNow() {
        if (_syncState.value.isSyncing) return
        viewModelScope.launch {
            _syncState.update { it.copy(isSyncing = true, message = "正在同步...") }
            try {
                val local = repository.getAllRawOnce()
                val remote = syncService.sync(local)
                repository.saveSyncedQuestions(remote)
                _syncState.update {
                    it.copy(isSyncing = false, message = "同步完成：${remote.size} 题")
                }
            } catch (e: Exception) {
                _syncState.update {
                    it.copy(isSyncing = false, message = "同步失败：${e.message}")
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = WrongBookApp.instance
                return HomeViewModel(app.repository, app.questionSyncService) as T
            }
        }
    }
}

private data class SyncUiState(
    val isSyncing: Boolean = false,
    val message: String? = null
)
