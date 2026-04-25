package com.wrongbook.app.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wrongbook.app.WrongBookApp
import com.wrongbook.app.data.repository.QuestionRepository
import com.wrongbook.app.model.Question
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUiState(
    val reviewQuestions: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    val completedCount: Int = 0,
    val isLoading: Boolean = true,
    val isCompleted: Boolean = false,
    val showAnswer: Boolean = false,
    val totalCount: Int = 0,
    val sortOrder: ReviewSortOrder = ReviewSortOrder.DUE_FIRST
)

enum class ReviewSortOrder(val label: String) {
    DUE_FIRST("待复习优先"),
    DIFFICULTY_DESC("难度优先"),
    MASTERY_ASC("薄弱优先"),
    RECENT_ADDED("最近添加")
}

class ReviewViewModel(
    private val repository: QuestionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        loadReviewQuestions()
    }

    private fun loadReviewQuestions() {
        viewModelScope.launch {
            val questions = repository.getDueForReviewOnce(System.currentTimeMillis())
            _uiState.update {
                it.copy(
                    reviewQuestions = sortQuestions(questions, it.sortOrder),
                    totalCount = questions.size,
                    isLoading = false,
                    isCompleted = questions.isEmpty()
                )
            }
        }
    }

    fun completeReview(quality: Int = 2) {
        val state = _uiState.value
        val question = state.reviewQuestions.getOrNull(state.currentIndex) ?: return
        viewModelScope.launch {
            if (repository.completeReview(question.id, quality)) {
                removeCurrentQuestion(question.id)
            }
        }
    }

    fun postponeReview() {
        val state = _uiState.value
        val question = state.reviewQuestions.getOrNull(state.currentIndex) ?: return
        viewModelScope.launch {
            if (repository.postponeReview(question.id)) {
                removeCurrentQuestion(question.id)
            }
        }
    }

    fun toggleShowAnswer() {
        _uiState.update { it.copy(showAnswer = !it.showAnswer) }
    }

    fun onSortOrderChange(order: ReviewSortOrder) {
        _uiState.update {
            it.copy(
                sortOrder = order,
                reviewQuestions = sortQuestions(it.reviewQuestions, order),
                currentIndex = 0,
                showAnswer = false
            )
        }
    }

    fun previousQuestion() {
        _uiState.update {
            if (it.currentIndex <= 0) it else it.copy(currentIndex = it.currentIndex - 1, showAnswer = false)
        }
    }

    fun nextQuestion() {
        _uiState.update {
            if (it.currentIndex >= it.reviewQuestions.lastIndex) {
                it
            } else {
                it.copy(currentIndex = it.currentIndex + 1, showAnswer = false)
            }
        }
    }

    private fun removeCurrentQuestion(questionId: String) {
        _uiState.update { state ->
            val updatedQuestions = state.reviewQuestions.filterNot { it.id == questionId }
            if (updatedQuestions.isEmpty()) {
                state.copy(
                    reviewQuestions = emptyList(),
                    currentIndex = 0,
                    completedCount = state.completedCount + 1,
                    isCompleted = true,
                    showAnswer = false
                )
            } else {
                state.copy(
                    reviewQuestions = updatedQuestions,
                    currentIndex = state.currentIndex.coerceAtMost(updatedQuestions.lastIndex),
                    completedCount = state.completedCount + 1,
                    showAnswer = false
                )
            }
        }
    }

    private fun sortQuestions(questions: List<Question>, order: ReviewSortOrder): List<Question> =
        when (order) {
            ReviewSortOrder.DUE_FIRST -> questions.sortedBy { it.nextReviewAt ?: Long.MAX_VALUE }
            ReviewSortOrder.DIFFICULTY_DESC -> questions.sortedByDescending { it.analysis?.difficultyScore ?: 0 }
            ReviewSortOrder.MASTERY_ASC -> questions.sortedBy { it.masteryLevel }
            ReviewSortOrder.RECENT_ADDED -> questions.sortedByDescending { it.createdAt }
        }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReviewViewModel(WrongBookApp.instance.repository) as T
            }
        }
    }
}
