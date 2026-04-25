package com.wrongbook.app.data.repository

import com.wrongbook.app.data.local.QuestionDao
import com.wrongbook.app.data.mapper.QuestionMapper
import com.wrongbook.app.model.FollowUpChat
import com.wrongbook.app.model.ImageRef
import com.wrongbook.app.model.Question
import com.wrongbook.app.model.QuestionAnalysis
import com.wrongbook.app.model.SubjectCatalog
import com.wrongbook.app.model.SyncStatus
import com.wrongbook.app.review.ReviewService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class QuestionRepository(private val dao: QuestionDao) {

    private val mutationMutex = Mutex()

    fun getAllActive(): Flow<List<Question>> =
        dao.getAllActive().map { list -> list.map(QuestionMapper::entityToDomain) }

    suspend fun getAllRawOnce(): List<Question> =
        dao.getAllRawOnce().map(QuestionMapper::entityToDomain)

    fun getByCategory(category: String): Flow<List<Question>> =
        dao.getByCategory(category).map { list -> list.map(QuestionMapper::entityToDomain) }

    fun searchByTitle(keyword: String): Flow<List<Question>> =
        dao.searchByTitle(keyword).map { list -> list.map(QuestionMapper::entityToDomain) }

    suspend fun getById(id: String): Question? =
        dao.getById(id)?.let(QuestionMapper::entityToDomain)

    suspend fun getRawById(id: String): Question? =
        dao.getRawById(id)?.let(QuestionMapper::entityToDomain)

    fun getByIdFlow(id: String): Flow<Question?> =
        dao.getByIdFlow(id).map { it?.let(QuestionMapper::entityToDomain) }

    fun getRawByIdFlow(id: String): Flow<Question?> =
        dao.getRawByIdFlow(id).map { it?.let(QuestionMapper::entityToDomain) }

    fun getDueForReview(timestamp: Long): Flow<List<Question>> =
        dao.getDueForReview(timestamp).map { list -> list.map(QuestionMapper::entityToDomain) }

    suspend fun getDueForReviewOnce(timestamp: Long): List<Question> =
        dao.getDueForReviewOnce(timestamp).map(QuestionMapper::entityToDomain)

    fun getDueForReviewCount(timestamp: Long): Flow<Int> =
        dao.getDueForReviewCount(timestamp)

    fun getRecentlyAdded(limit: Int = 5): Flow<List<Question>> =
        dao.getRecentlyAdded(limit).map { list -> list.map(QuestionMapper::entityToDomain) }

    fun getAllCategories(): Flow<List<String>> =
        flowOf(SubjectCatalog.subjects)

    suspend fun save(question: Question) {
        val entity = QuestionMapper.domainToEntity(question)
        dao.insert(entity)
    }

    suspend fun saveSyncedQuestions(questions: List<Question>) = mutationMutex.withLock {
        questions.forEach { question ->
            dao.insert(QuestionMapper.domainToEntity(question.copy(syncStatus = SyncStatus.SYNCED)))
        }
    }

    suspend fun updateQuestionContent(
        id: String,
        title: String,
        category: String,
        grade: String,
        questionType: String,
        source: String,
        questionText: String,
        userAnswer: String,
        correctAnswer: String,
        notes: String,
        errorCause: String,
        tags: List<String>,
        imageRefs: List<ImageRef>,
        noteImageRefs: List<ImageRef>
    ): Boolean = mutate(id) { current, now ->
        val contentChanged =
            current.title != title ||
                current.category != category ||
                current.grade != grade ||
                current.questionType != questionType ||
                current.source != source ||
                current.questionText != questionText ||
                current.userAnswer != userAnswer ||
                current.correctAnswer != correctAnswer ||
                current.errorCause != errorCause ||
                current.tags != tags ||
                current.imageRefs != imageRefs

        current.copy(
            title = title,
            category = category,
            grade = grade,
            questionType = questionType,
            source = source,
            questionText = questionText,
            userAnswer = userAnswer,
            correctAnswer = correctAnswer,
            notes = notes,
            errorCause = errorCause,
            tags = tags,
            imageRefs = imageRefs,
            noteImageRefs = noteImageRefs,
            contentUpdatedAt = if (contentChanged) now else current.contentUpdatedAt
        )
    }

    suspend fun saveAnalysis(
        id: String,
        analysis: QuestionAnalysis,
        baseContentUpdatedAt: Long
    ): Boolean = mutate(id) { current, _ ->
        current.copy(
            analysis = analysis,
            analysisContentUpdatedAt = baseContentUpdatedAt
        )
    }

    suspend fun saveDetailedExplanation(
        id: String,
        explanation: String,
        baseContentUpdatedAt: Long
    ): Boolean = mutate(id) { current, _ ->
        current.copy(
            detailedExplanation = explanation,
            detailedExplanationUpdatedAt = System.currentTimeMillis(),
            explanationContentUpdatedAt = baseContentUpdatedAt
        )
    }

    suspend fun saveHint(
        id: String,
        hint: String,
        baseContentUpdatedAt: Long
    ): Boolean = mutate(id) { current, _ ->
        current.copy(
            hint = hint,
            hintUpdatedAt = System.currentTimeMillis(),
            hintContentUpdatedAt = baseContentUpdatedAt
        )
    }

    suspend fun saveNotes(id: String, notes: String): Boolean = mutate(id) { current, _ ->
        current.copy(
            notes = notes
        )
    }

    suspend fun saveNoteImages(id: String, noteImageRefs: List<ImageRef>): Boolean =
        mutate(id) { current, _ ->
            current.copy(
                noteImageRefs = noteImageRefs
            )
        }

    suspend fun appendFollowUpChats(
        id: String,
        chats: List<FollowUpChat>,
        baseContentUpdatedAt: Long
    ): Boolean = mutate(id) { current, _ ->
        current.copy(
            followUpChats = current.followUpChats + chats,
            followUpContentUpdatedAt = baseContentUpdatedAt
        )
    }

    suspend fun completeReview(id: String, quality: Int = 2): Boolean =
        mutate(id) { current, _ -> ReviewService.completeReview(current, quality) }

    suspend fun postponeReview(id: String): Boolean =
        mutate(id) { current, _ -> ReviewService.postponeReview(current) }

    suspend fun softDelete(id: String): Boolean = mutationMutex.withLock {
        val current = dao.getRawById(id)?.let(QuestionMapper::entityToDomain) ?: return@withLock false
        if (current.deleted) return@withLock false

        val now = System.currentTimeMillis()
        dao.softDelete(
            id = id,
            deletedAt = now,
            updatedAt = now,
            syncStatus = dirtyStatusFor(current.syncStatus).name
        )
        true
    }

    suspend fun count(): Int = dao.count()

    private suspend fun mutate(
        id: String,
        transform: (Question, Long) -> Question
    ): Boolean = mutationMutex.withLock {
        val current = dao.getRawById(id)?.let(QuestionMapper::entityToDomain) ?: return@withLock false
        if (current.deleted) return@withLock false

        val now = System.currentTimeMillis()
        val next = transform(current, now).copy(
            updatedAt = now,
            syncStatus = dirtyStatusFor(current.syncStatus)
        )
        dao.update(QuestionMapper.domainToEntity(next))
        true
    }

    private fun dirtyStatusFor(current: SyncStatus): SyncStatus =
        SyncStatus.MODIFIED
}
