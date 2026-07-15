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
                var uploadSnapshot = repository.captureSyncSnapshot()
                var result = syncService.sync(uploadSnapshot) { progress ->
                    _syncState.update { it.copy(message = progress.message) }
                }
                var outcome = repository.applyCompleteSyncSnapshot(
                    uploadSnapshot = uploadSnapshot,
                    remoteSnapshot = result.records
                )
                if (outcome.replayedLocalChanges > 0) {
                    _syncState.update { it.copy(message = "正在补传同步期间产生的本地修改…") }
                    uploadSnapshot = repository.captureSyncSnapshot()
                    result = syncService.sync(uploadSnapshot) { progress ->
                        _syncState.update { it.copy(message = "补传：${progress.message}") }
                    }
                    outcome = repository.applyCompleteSyncSnapshot(
                        uploadSnapshot = uploadSnapshot,
                        remoteSnapshot = result.records
                    )
                }
                syncService.cleanOrphanedImages(repository.getAllRawOnce())
                val protocol = if (result.protocolVersion == 2) "v2" else "v1 兼容模式"
                val replayMessage = outcome.replayedLocalChanges.takeIf { it > 0 }
                    ?.let { "；又产生了 $it 项本地修改，已安全保留，待下次同步" }.orEmpty()
                _syncState.update {
                    it.copy(
                        isSyncing = false,
                        message = "同步完成（$protocol）：${result.downloadedCount} 题$replayMessage"
                    )
                }
            } catch (e: Exception) {
                _syncState.update {
                    it.copy(isSyncing = false, message = "同步失败：${e.message?.take(80) ?: "未知错误"}")
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
