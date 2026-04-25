package com.wrongbook.app.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wrongbook.app.WrongBookApp
import com.wrongbook.app.data.repository.QuestionRepository
import com.wrongbook.app.model.Question
import com.wrongbook.app.model.SubjectCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class QuestionListUiState(
    val questions: List<Question> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val sortOrder: SortOrder = SortOrder.UPDATED_DESC,
    val categories: List<String> = emptyList(),
    val isLoading: Boolean = true
)

enum class SortOrder { CREATED_DESC, UPDATED_DESC, DIFFICULTY_DESC, MASTERY_ASC }

class QuestionListViewModel(
    private val repository: QuestionRepository,
    initialCategory: String? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow(initialCategory?.takeIf(SubjectCatalog::isSupported))
    private val _sortOrder = MutableStateFlow(SortOrder.UPDATED_DESC)

    val uiState: StateFlow<QuestionListUiState> = combine(
        repository.getAllActive(),
        _searchQuery,
        _selectedCategory,
        _sortOrder,
        repository.getAllCategories()
    ) { questions, query, category, sort, categories ->
        var filtered = questions
        if (!category.isNullOrEmpty()) {
            filtered = filtered.filter { it.category == category }
        }
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true) ||
                    it.grade.contains(query, ignoreCase = true) ||
                    it.questionType.contains(query, ignoreCase = true) ||
                    it.source.contains(query, ignoreCase = true) ||
                    it.errorCause.contains(query, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(query, ignoreCase = true) } ||
                    it.questionText?.contains(query, ignoreCase = true) == true ||
                    it.userAnswer?.contains(query, ignoreCase = true) == true ||
                    it.correctAnswer?.contains(query, ignoreCase = true) == true ||
                    it.notes?.contains(query, ignoreCase = true) == true ||
                    it.analysis?.knowledgePoints?.any { point -> point.contains(query, ignoreCase = true) } == true ||
                    it.analysis?.commonMistakes?.any { mistake -> mistake.contains(query, ignoreCase = true) } == true ||
                    it.analysis?.notices?.any { notice -> notice.contains(query, ignoreCase = true) } == true ||
                    it.analysis?.solutionMethods?.any { method -> method.contains(query, ignoreCase = true) } == true
            }
        }
        filtered = when (sort) {
            SortOrder.CREATED_DESC -> filtered.sortedByDescending { it.createdAt }
            SortOrder.UPDATED_DESC -> filtered.sortedByDescending { it.updatedAt }
            SortOrder.DIFFICULTY_DESC -> filtered.sortedByDescending { it.analysis?.difficultyScore ?: 0 }
            SortOrder.MASTERY_ASC -> filtered.sortedBy { it.masteryLevel }
        }
        QuestionListUiState(
            questions = filtered,
            searchQuery = query,
            selectedCategory = category,
            sortOrder = sort,
            categories = categories,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = QuestionListUiState()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = category?.takeIf(SubjectCatalog::isSupported)
    }

    fun onSortOrderChange(order: SortOrder) {
        _sortOrder.value = order
    }

    class Factory(private val initialCategory: String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QuestionListViewModel(WrongBookApp.instance.repository, initialCategory) as T
        }
    }
}
