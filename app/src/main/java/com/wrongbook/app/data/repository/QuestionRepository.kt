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
import com.wrongbook.app.sync.SyncMergeOutcome
import com.wrongbook.app.sync.SyncSnapshotMerger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class QuestionRepository(
    private val dao: QuestionDao,
    private val deviceId: String? = null
) {

    private val mutationMutex = Mutex()

    fun getAllActive(): Flow<List<Question>> =
        dao.getAllActive().map { list -> list.map(QuestionMapper::entityToDomain) }

    suspend fun getAllRawOnce(): List<Question> =
        dao.getAllRawOnce().map(QuestionMapper::entityToDomain)

    fun getAllDeleted(): Flow<List<Question>> =
        dao.getAllDeleted().map { list -> list.map(QuestionMapper::entityToDomain) }

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
        dao.getAllCategories()

    suspend fun save(question: Question) = mutationMutex.withLock {
        val normalized = question.copy(
            imageRefsUpdatedAt = question.imageRefsUpdatedAt
                ?: question.imageRefs.takeIf { it.isNotEmpty() }?.let { question.contentUpdatedAt }
        )
        dao.insert(QuestionMapper.domainToEntity(normalized))
    }

    /** Captures the exact upload base under the same mutex used by every local mutation. */
    suspend fun captureSyncSnapshot(): List<Question> = mutationMutex.withLock {
        dao.getAllRawOnce().map(QuestionMapper::entityToDomain)
    }

    /**
     * Atomically replaces the local collection with a complete server snapshot, then replays
     * edits created after [uploadSnapshot]. A valid empty remote snapshot clears acknowledged data.
     */
    suspend fun applyCompleteSyncSnapshot(
        uploadSnapshot: List<Question>,
        remoteSnapshot: List<Question>
    ): SyncMergeOutcome = mutationMutex.withLock {
        val current = dao.getAllRawOnce().map(QuestionMapper::entityToDomain)
        val outcome = SyncSnapshotMerger.merge(uploadSnapshot, current, remoteSnapshot)
        dao.replaceAll(outcome.questions.map(QuestionMapper::domainToEntity))
        outcome
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
        val notesChanged = current.notes != notes
        val imageRefsChanged = current.imageRefs != imageRefs
        val noteImagesChanged = current.noteImageRefs != noteImageRefs

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
            notesUpdatedAt = if (notesChanged) now else current.notesUpdatedAt,
            noteImagesUpdatedAt = if (noteImagesChanged) now else current.noteImagesUpdatedAt,
            imageRefsUpdatedAt = if (imageRefsChanged) now else current.imageRefsUpdatedAt,
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
    ): Boolean = mutate(id) { current, now ->
        current.copy(
            detailedExplanation = explanation,
            detailedExplanationUpdatedAt = now,
            explanationContentUpdatedAt = baseContentUpdatedAt
        )
    }

    suspend fun saveHint(
        id: String,
        hint: String,
        baseContentUpdatedAt: Long
    ): Boolean = mutate(id) { current, now ->
        current.copy(
            hint = hint,
            hintUpdatedAt = now,
            hintContentUpdatedAt = baseContentUpdatedAt
        )
    }

    suspend fun saveNotes(id: String, notes: String): Boolean = mutate(id) { current, now ->
        current.copy(
            notes = notes,
            notesUpdatedAt = if (current.notes != notes) now else current.notesUpdatedAt
        )
    }

    suspend fun saveNoteImages(id: String, noteImageRefs: List<ImageRef>): Boolean =
        mutate(id) { current, now ->
            current.copy(
                noteImageRefs = noteImageRefs,
                noteImagesUpdatedAt =
                    if (current.noteImageRefs != noteImageRefs) now else current.noteImagesUpdatedAt
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
        mutate(id) { current, now ->
            ReviewService.completeReview(current, quality, deviceId = deviceId, now = now)
        }

    suspend fun revertLatestReview(id: String): Boolean = mutationMutex.withLock {
        val current = dao.getRawById(id)?.let(QuestionMapper::entityToDomain) ?: return@withLock false
        if (current.deleted) return@withLock false
        val now = maxOf(System.currentTimeMillis(), current.updatedAt + 1L)
        val reverted = ReviewService.revertLatestReview(
            question = current,
            deviceId = deviceId,
            now = now
        ) ?: return@withLock false
        dao.update(
            QuestionMapper.domainToEntity(
                reverted.copy(updatedAt = now, syncStatus = SyncStatus.MODIFIED)
            )
        )
        true
    }

    suspend fun postponeReview(id: String): Boolean =
        mutate(id) { current, now -> ReviewService.postponeReview(current, now) }

    suspend fun softDelete(id: String): Boolean = mutationMutex.withLock {
        val current = dao.getRawById(id)?.let(QuestionMapper::entityToDomain) ?: return@withLock false
        if (current.deleted) return@withLock false

        val now = maxOf(
            System.currentTimeMillis(),
            current.updatedAt + 1L,
            (current.restoredAt ?: 0L) + 1L
        )
        dao.softDelete(
            id = id,
            deletedAt = now,
            updatedAt = now,
            syncStatus = dirtyStatusFor(current.syncStatus).name
        )
        true
    }

    suspend fun restore(id: String): Boolean = mutationMutex.withLock {
        val current = dao.getRawById(id)?.let(QuestionMapper::entityToDomain) ?: return@withLock false
        if (!current.deleted) return@withLock false
        val now = maxOf(
            System.currentTimeMillis(),
            current.updatedAt + 1L,
            (current.deletedAt ?: 0L) + 1L
        )
        dao.update(
            QuestionMapper.domainToEntity(
                current.copy(
                    deleted = false,
                    restoredAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.MODIFIED
                )
            )
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

        val now = maxOf(System.currentTimeMillis(), current.updatedAt + 1L)
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
