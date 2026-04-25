package com.wrongbook.app.ui.screens.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wrongbook.app.WrongBookApp
import com.wrongbook.app.data.repository.QuestionRepository
import com.wrongbook.app.model.ImageRef
import com.wrongbook.app.model.Question
import com.wrongbook.app.model.SubjectCatalog
import com.wrongbook.app.model.SyncStatus
import com.wrongbook.app.review.ReviewService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AddEditUiState(
    val isEdit: Boolean = false,
    val isLoading: Boolean = false,
    val isUnavailable: Boolean = false,
    val unavailableMessage: String? = null,
    val title: String = "",
    val category: String = SubjectCatalog.defaultSubject,
    val grade: String = "",
    val questionType: String = "",
    val source: String = "",
    val questionText: String = "",
    val userAnswer: String = "",
    val correctAnswer: String = "",
    val notes: String = "",
    val errorCause: String = "",
    val tagsText: String = "",
    val imageRefs: List<ImageRef> = emptyList(),
    val noteImageRefs: List<ImageRef> = emptyList(),
    val categories: List<String> = SubjectCatalog.subjects,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)

class AddEditViewModel(
    private val repository: QuestionRepository,
    private val questionId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddEditUiState(
            isEdit = questionId != null,
            isLoading = questionId != null
        )
    )
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    private var existingQuestionId: String? = null

    init {
        if (questionId != null) {
            loadQuestion(questionId)
        }
        loadCategories()
    }

    private fun loadQuestion(id: String) {
        viewModelScope.launch {
            val question = repository.getRawById(id)
            if (question == null || question.deleted) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isUnavailable = true,
                        unavailableMessage = "题目不存在或已删除"
                    )
                }
                return@launch
            }

            existingQuestionId = question.id
            _uiState.update {
                it.copy(
                    isLoading = false,
                    title = question.title,
                    category = SubjectCatalog.normalize(question.category),
                    grade = question.grade,
                    questionType = question.questionType,
                    source = question.source,
                    questionText = question.questionText.orEmpty(),
                    userAnswer = question.userAnswer.orEmpty(),
                    correctAnswer = question.correctAnswer.orEmpty(),
                    notes = question.notes.orEmpty(),
                    errorCause = question.errorCause,
                    tagsText = question.tags.joinToString("，"),
                    imageRefs = question.imageRefs,
                    noteImageRefs = question.noteImageRefs
                )
            }
        }
    }

    private fun loadCategories() {
        _uiState.update { it.copy(categories = SubjectCatalog.subjects) }
    }

    fun onTitleChange(value: String) { _uiState.update { it.copy(title = value) } }
    fun onCategoryChange(value: String) {
        if (SubjectCatalog.isSupported(value)) {
            _uiState.update { it.copy(category = SubjectCatalog.normalize(value)) }
        }
    }
    fun onGradeChange(value: String) { _uiState.update { it.copy(grade = value) } }
    fun onQuestionTypeChange(value: String) { _uiState.update { it.copy(questionType = value) } }
    fun onSourceChange(value: String) { _uiState.update { it.copy(source = value) } }
    fun onQuestionTextChange(value: String) { _uiState.update { it.copy(questionText = value) } }
    fun onUserAnswerChange(value: String) { _uiState.update { it.copy(userAnswer = value) } }
    fun onCorrectAnswerChange(value: String) { _uiState.update { it.copy(correctAnswer = value) } }
    fun onNotesChange(value: String) { _uiState.update { it.copy(notes = value) } }
    fun onErrorCauseChange(value: String) { _uiState.update { it.copy(errorCause = value) } }
    fun onTagsTextChange(value: String) { _uiState.update { it.copy(tagsText = value) } }
    fun addImageRef(imageRef: ImageRef) {
        _uiState.update { it.copy(imageRefs = it.imageRefs + imageRef) }
    }

    fun removeImageRef(imageId: String) {
        _uiState.update { it.copy(imageRefs = it.imageRefs.filterNot { ref -> ref.id == imageId }) }
    }

    fun addNoteImageRef(imageRef: ImageRef) {
        _uiState.update { it.copy(noteImageRefs = it.noteImageRefs + imageRef) }
    }

    fun removeNoteImageRef(imageId: String) {
        _uiState.update { it.copy(noteImageRefs = it.noteImageRefs.filterNot { ref -> ref.id == imageId }) }
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "标题不能为空") }
            return
        }
        if (state.imageRefs.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请先添加题目图片") }
            return
        }
        val category = SubjectCatalog.normalize(state.category)
        if (!SubjectCatalog.isSupported(category)) {
            _uiState.update { it.copy(errorMessage = "请选择数学、物理、化学或生物") }
            return
        }
        if (state.isUnavailable) {
            _uiState.update { it.copy(errorMessage = "原题已不存在，不能继续保存") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val now = System.currentTimeMillis()
                val tags = parseTags(state.tagsText)

                val saved = if (questionId != null) {
                    val editId = existingQuestionId
                    if (editId == null) {
                        false
                    } else {
                        repository.updateQuestionContent(
                            id = editId,
                            title = state.title,
                            category = category,
                            grade = state.grade,
                            questionType = state.questionType,
                            source = state.source,
                            questionText = state.questionText,
                            userAnswer = state.userAnswer,
                            correctAnswer = state.correctAnswer,
                            notes = state.notes,
                            errorCause = state.errorCause,
                            tags = tags,
                            imageRefs = state.imageRefs,
                            noteImageRefs = state.noteImageRefs
                        )
                    }
                } else {
                    val (nextReview, reviewStatus) = ReviewService.initializeReview(now)
                    val question = Question(
                        id = UUID.randomUUID().toString(),
                        title = state.title,
                        category = category,
                        grade = state.grade,
                        questionType = state.questionType,
                        source = state.source,
                        questionText = state.questionText,
                        userAnswer = state.userAnswer,
                        correctAnswer = state.correctAnswer,
                        notes = state.notes,
                        errorCause = state.errorCause,
                        tags = tags,
                        createdAt = now,
                        updatedAt = now,
                        contentUpdatedAt = now,
                        nextReviewAt = nextReview,
                        reviewStatus = reviewStatus,
                        syncStatus = SyncStatus.PENDING,
                        imageRefs = state.imageRefs,
                        noteImageRefs = state.noteImageRefs
                    )
                    repository.save(question)
                    true
                }

                if (!saved) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isUnavailable = questionId != null,
                            unavailableMessage = if (questionId != null) "题目不存在或已删除" else it.unavailableMessage,
                            errorMessage = "保存失败：题目不存在或已删除"
                        )
                    }
                } else {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "保存失败: ${e.message}") }
            }
        }
    }

    class Factory(private val questionId: String? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = WrongBookApp.instance
            return AddEditViewModel(
                repository = app.repository,
                questionId = questionId
            ) as T
        }
    }

    private fun parseTags(input: String): List<String> =
        input.split(',', '，', '、', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}
