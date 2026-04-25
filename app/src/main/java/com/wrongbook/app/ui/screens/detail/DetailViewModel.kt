package com.wrongbook.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wrongbook.app.WrongBookApp
import com.wrongbook.app.ai.AiService
import com.wrongbook.app.data.repository.QuestionRepository
import com.wrongbook.app.model.FollowUpChat
import com.wrongbook.app.model.ImageRef
import com.wrongbook.app.model.Question
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private sealed interface DetailQuestionState {
    data object Loading : DetailQuestionState
    data object Missing : DetailQuestionState
    data object Deleted : DetailQuestionState
    data class Active(val question: Question) : DetailQuestionState
}

data class DetailUiState(
    val question: Question? = null,
    val isLoading: Boolean = true,
    val unavailableMessage: String? = null,
    val notesInput: String = "",
    val isSavingNotes: Boolean = false,
    val isSavingNoteImages: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isGeneratingExplanation: Boolean = false,
    val isGeneratingHint: Boolean = false,
    val followUpInput: String = "",
    val isSendingFollowUp: Boolean = false,
    val isDeleted: Boolean = false,
    val message: String? = null
)

private data class DetailLocalState(
    val notesInput: String? = null,
    val isSavingNotes: Boolean = false,
    val isSavingNoteImages: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isGeneratingExplanation: Boolean = false,
    val isGeneratingHint: Boolean = false,
    val followUpInput: String = "",
    val isSendingFollowUp: Boolean = false,
    val isDeleted: Boolean = false,
    val message: String? = null
)

class DetailViewModel(
    private val questionId: String,
    private val repository: QuestionRepository,
    private val aiService: AiService
) : ViewModel() {

    private val _localState = MutableStateFlow(DetailLocalState())

    private val questionState: StateFlow<DetailQuestionState> =
        repository.getRawByIdFlow(questionId)
            .map { question ->
                when {
                    question == null -> DetailQuestionState.Missing
                    question.deleted -> DetailQuestionState.Deleted
                    else -> DetailQuestionState.Active(question)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = DetailQuestionState.Loading
            )

    val uiState: StateFlow<DetailUiState> = combine(
        questionState,
        _localState
    ) { question, local ->
        val activeQuestion = (question as? DetailQuestionState.Active)?.question
        DetailUiState(
            question = activeQuestion,
            isLoading = question is DetailQuestionState.Loading,
            unavailableMessage = when (question) {
                DetailQuestionState.Deleted -> "题目已删除"
                DetailQuestionState.Missing -> "题目不存在"
                else -> null
            },
            notesInput = local.notesInput ?: activeQuestion?.notes.orEmpty(),
            isSavingNotes = local.isSavingNotes,
            isSavingNoteImages = local.isSavingNoteImages,
            isAnalyzing = local.isAnalyzing,
            isGeneratingExplanation = local.isGeneratingExplanation,
            isGeneratingHint = local.isGeneratingHint,
            followUpInput = local.followUpInput,
            isSendingFollowUp = local.isSendingFollowUp,
            isDeleted = local.isDeleted,
            message = local.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DetailUiState()
    )

    fun onNotesInputChange(input: String) {
        _localState.update { it.copy(notesInput = input) }
    }

    fun saveNotes() {
        val question = uiState.value.question ?: return
        val notes = _localState.value.notesInput ?: question.notes.orEmpty()
        viewModelScope.launch {
            _localState.update { it.copy(isSavingNotes = true) }
            try {
                val saved = repository.saveNotes(question.id, notes.trim().ifBlank { null })
                _localState.update {
                    it.copy(
                        notesInput = if (saved) null else notes,
                        isSavingNotes = false,
                        message = if (saved) "笔记已保存" else "题目已不存在或已删除"
                    )
                }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(
                        notesInput = notes,
                        isSavingNotes = false,
                        message = "笔记保存失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun addNoteImageRef(imageRef: ImageRef) {
        val question = uiState.value.question ?: return
        saveNoteImages(question.noteImageRefs + imageRef)
    }

    fun removeNoteImageRef(imageId: String) {
        val question = uiState.value.question ?: return
        saveNoteImages(question.noteImageRefs.filterNot { it.id == imageId })
    }

    private fun saveNoteImages(noteImageRefs: List<ImageRef>) {
        val question = uiState.value.question ?: return
        viewModelScope.launch {
            _localState.update { it.copy(isSavingNoteImages = true) }
            try {
                val saved = repository.saveNoteImages(question.id, noteImageRefs)
                _localState.update {
                    it.copy(
                        isSavingNoteImages = false,
                        message = if (saved) "图片笔记已保存" else "题目已不存在或已删除"
                    )
                }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(
                        isSavingNoteImages = false,
                        message = "图片笔记保存失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun analyze() {
        val question = uiState.value.question ?: return
        viewModelScope.launch {
            _localState.update { it.copy(isAnalyzing = true) }
            try {
                val analysis = aiService.analyze(question)
                val saved = repository.saveAnalysis(
                    id = question.id,
                    analysis = analysis,
                    baseContentUpdatedAt = question.contentUpdatedAt
                )
                _localState.update {
                    it.copy(
                        isAnalyzing = false,
                        message = if (saved) "AI 分析完成" else "题目已不存在或已删除"
                    )
                }
            } catch (e: Exception) {
                _localState.update { it.copy(isAnalyzing = false, message = "分析失败: ${e.message}") }
            }
        }
    }

    fun generateExplanation() {
        val question = uiState.value.question ?: return
        viewModelScope.launch {
            _localState.update { it.copy(isGeneratingExplanation = true) }
            try {
                val explanation = aiService.generateDetailedExplanation(question)
                val saved = repository.saveDetailedExplanation(
                    id = question.id,
                    explanation = explanation,
                    baseContentUpdatedAt = question.contentUpdatedAt
                )
                _localState.update {
                    it.copy(
                        isGeneratingExplanation = false,
                        message = if (saved) "详解生成完成" else "题目已不存在或已删除"
                    )
                }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(
                        isGeneratingExplanation = false,
                        message = "生成失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun generateHint() {
        val question = uiState.value.question ?: return
        viewModelScope.launch {
            _localState.update { it.copy(isGeneratingHint = true) }
            try {
                val hint = aiService.generateHint(question)
                val saved = repository.saveHint(
                    id = question.id,
                    hint = hint,
                    baseContentUpdatedAt = question.contentUpdatedAt
                )
                _localState.update {
                    it.copy(
                        isGeneratingHint = false,
                        message = if (saved) "思路指引生成完成" else "题目已不存在或已删除"
                    )
                }
            } catch (e: Exception) {
                _localState.update { it.copy(isGeneratingHint = false, message = "生成失败: ${e.message}") }
            }
        }
    }

    fun sendFollowUp() {
        val input = _localState.value.followUpInput.trim()
        if (input.isEmpty()) return
        val question = uiState.value.question ?: return

        viewModelScope.launch {
            _localState.update { it.copy(isSendingFollowUp = true) }
            try {
                val userChat = FollowUpChat(
                    role = "user",
                    content = input,
                    createdAt = System.currentTimeMillis()
                )
                val aiResponse = aiService.followUp(question, input)
                val saved = repository.appendFollowUpChats(
                    id = question.id,
                    chats = listOf(userChat, aiResponse),
                    baseContentUpdatedAt = question.contentUpdatedAt
                )
                _localState.update {
                    it.copy(
                        isSendingFollowUp = false,
                        followUpInput = if (saved) "" else input,
                        message = if (saved) null else "题目已不存在或已删除"
                    )
                }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(
                        isSendingFollowUp = false,
                        followUpInput = input,
                        message = "发送失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun softDelete() {
        viewModelScope.launch {
            val deleted = repository.softDelete(questionId)
            _localState.update {
                it.copy(
                    isDeleted = deleted,
                    message = if (deleted) it.message else "题目已不存在或已删除"
                )
            }
        }
    }

    fun markReviewed() {
        viewModelScope.launch {
            val reviewed = repository.completeReview(questionId)
            _localState.update {
                it.copy(message = if (reviewed) "已标记复习完成" else "题目已不存在或已删除")
            }
        }
    }

    fun onFollowUpInputChange(input: String) {
        _localState.update { it.copy(followUpInput = input) }
    }

    fun clearMessage() {
        _localState.update { it.copy(message = null) }
    }

    fun showMessage(message: String) {
        _localState.update { it.copy(message = message) }
    }

    class Factory(private val questionId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = WrongBookApp.instance
            return DetailViewModel(questionId, app.repository, app.aiService) as T
        }
    }
}
