package com.wrongbook.app.ui.screens.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wrongbook.app.WrongBookApp
import com.wrongbook.app.data.repository.QuestionRepository
import com.wrongbook.app.model.Question
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrashUiState(
    val questions: List<Question> = emptyList(),
    val restoringIds: Set<String> = emptySet(),
    val message: String? = null,
    val isLoading: Boolean = true
)

private data class TrashLocalState(
    val restoringIds: Set<String> = emptySet(),
    val message: String? = null
)

class TrashViewModel(private val repository: QuestionRepository) : ViewModel() {
    private val localState = MutableStateFlow(TrashLocalState())

    val uiState: StateFlow<TrashUiState> = combine(
        repository.getAllDeleted(),
        localState
    ) { deleted, local ->
        TrashUiState(
            questions = deleted.sortedByDescending { it.deletedAt ?: it.updatedAt },
            restoringIds = local.restoringIds,
            message = local.message,
            isLoading = false
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TrashUiState()
    )

    fun restore(id: String) {
        if (id in localState.value.restoringIds) return
        viewModelScope.launch {
            localState.update { it.copy(restoringIds = it.restoringIds + id, message = null) }
            val restored = runCatching { repository.restore(id) }.getOrDefault(false)
            localState.update {
                it.copy(
                    restoringIds = it.restoringIds - id,
                    message = if (restored) "已恢复，下一次同步会同步到其他设备" else "恢复失败，记录可能已变化"
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TrashViewModel(WrongBookApp.instance.repository) as T
        }
    }
}
