package com.wrongbook.app.sync

import com.wrongbook.app.model.FollowUpChat
import com.wrongbook.app.model.Question
import com.wrongbook.app.model.ReviewEvent
import com.wrongbook.app.model.SyncStatus
import com.wrongbook.app.review.ReviewService

data class SyncMergeOutcome(
    val questions: List<Question>,
    val replayedLocalChanges: Int,
    val removedLocalRecords: Int
)

/**
 * Applies a complete server snapshot and replays only edits made after the upload snapshot.
 * Deletion is an independent event and wins over ordinary activity; only restoredAt can revive it.
 */
object SyncSnapshotMerger {

    fun merge(
        uploadSnapshot: List<Question>,
        currentLocal: List<Question>,
        remoteSnapshot: List<Question>
    ): SyncMergeOutcome {
        val baseById = uploadSnapshot.associateBy(Question::id)
        val currentById = currentLocal.associateBy(Question::id)
        val remoteById = remoteSnapshot.associateBy(Question::id)
        val result = remoteById.mapValues { (_, question) ->
            question.copy(syncStatus = SyncStatus.SYNCED)
        }.toMutableMap()
        var replayed = 0

        currentById.forEach { (id, current) ->
            val base = baseById[id]
            if (base == null || current != base) {
                val replayedQuestion = replayDelta(base, current, result[id])
                if (replayedQuestion == null) {
                    result.remove(id)
                } else {
                    result[id] = replayedQuestion
                    if (base == null || replayedQuestion.syncStatus != SyncStatus.SYNCED) {
                        replayed += 1
                    }
                }
            }
        }

        val removed = currentById.keys.count { id ->
            id !in result && currentById[id] == baseById[id]
        }
        return SyncMergeOutcome(
            questions = result.values.sortedWith(
                compareByDescending<Question> { it.updatedAt }.thenBy(Question::id)
            ),
            replayedLocalChanges = replayed,
            removedLocalRecords = removed
        )
    }

    private fun replayDelta(base: Question?, current: Question, remote: Question?): Question? {
        if (remote == null) {
            if (base == null) return current
            val explicitRestore = base.deleted &&
                !current.deleted &&
                current.restoredAt != base.restoredAt &&
                (current.restoredAt ?: Long.MIN_VALUE) > (base.deletedAt ?: Long.MIN_VALUE)
            return current.takeIf { explicitRestore }
        }

        val latestDeleteAt = listOfNotNull(current.deletedAt, remote.deletedAt).maxOrNull()
        val latestRestoreAt = listOfNotNull(current.restoredAt, remote.restoredAt).maxOrNull()
        if (latestDeleteAt != null && (latestRestoreAt == null || latestRestoreAt <= latestDeleteAt)) {
            val tombstone = listOf(current, remote)
                .filter { it.deletedAt == latestDeleteAt }
                .maxByOrNull(Question::updatedAt)
                ?: remote
            return tombstone.copy(
                deleted = true,
                deletedAt = latestDeleteAt,
                restoredAt = latestRestoreAt,
                syncStatus = if (tombstone === current) current.syncStatus else SyncStatus.SYNCED
            )
        }

        if (base == null) {
            return current.copy(
                deleted = false,
                restoredAt = latestRestoreAt,
                updatedAt = maxOf(current.updatedAt, remote.updatedAt)
            )
        }

        val localExplicitRestore = base.deleted &&
            !current.deleted &&
            current.restoredAt != base.restoredAt &&
            (current.restoredAt ?: Long.MIN_VALUE) > (base.deletedAt ?: Long.MIN_VALUE) &&
            current.restoredAt == latestRestoreAt
        var merged = (if (localExplicitRestore) current else remote).copy(
            deleted = false,
            restoredAt = latestRestoreAt,
            syncStatus = current.syncStatus,
            updatedAt = maxOf(current.updatedAt, remote.updatedAt)
        )

        if (contentChanged(base, current)) {
            merged = merged.copy(
                title = current.title,
                category = current.category,
                grade = current.grade,
                questionType = current.questionType,
                source = current.source,
                questionText = current.questionText,
                userAnswer = current.userAnswer,
                correctAnswer = current.correctAnswer,
                errorCause = current.errorCause,
                tags = current.tags,
                contentUpdatedAt = current.contentUpdatedAt
            )
        }
        if (base.imageRefs != current.imageRefs || base.imageRefsUpdatedAt != current.imageRefsUpdatedAt) {
            merged = merged.copy(
                imageRefs = current.imageRefs,
                imageRefsUpdatedAt = current.imageRefsUpdatedAt
            )
        }
        if (base.notes != current.notes || base.notesUpdatedAt != current.notesUpdatedAt) {
            merged = merged.copy(notes = current.notes, notesUpdatedAt = current.notesUpdatedAt)
        }
        if (base.noteImageRefs != current.noteImageRefs ||
            base.noteImagesUpdatedAt != current.noteImagesUpdatedAt
        ) {
            merged = merged.copy(
                noteImageRefs = current.noteImageRefs,
                noteImagesUpdatedAt = current.noteImagesUpdatedAt
            )
        }

        val reviewEvents = unionById(remote.reviewEvents, current.reviewEvents, ReviewEvent::id)
        val localReviewChanged = base.reviewEvents != current.reviewEvents ||
            base.reviewUpdatedAt != current.reviewUpdatedAt ||
            base.nextReviewAt != current.nextReviewAt
        if (reviewEvents != remote.reviewEvents || localReviewChanged) {
            val reviewBase = if (localReviewChanged &&
                (current.reviewUpdatedAt ?: Long.MIN_VALUE) > (merged.reviewUpdatedAt ?: Long.MIN_VALUE)
            ) {
                merged.copy(
                    nextReviewAt = current.nextReviewAt,
                    reviewUpdatedAt = current.reviewUpdatedAt
                )
            } else {
                merged
            }
            merged = ReviewService.deriveFromMergedEvents(
                question = reviewBase,
                events = reviewEvents,
                reviewUpdatedAt = maxOfNullable(remote.reviewUpdatedAt, current.reviewUpdatedAt)
                    ?: merged.updatedAt
            )
        }

        if (base.analysis != current.analysis ||
            base.analysisContentUpdatedAt != current.analysisContentUpdatedAt
        ) {
            merged = merged.copy(
                analysis = current.analysis,
                analysisContentUpdatedAt = current.analysisContentUpdatedAt
            )
        }
        if (base.detailedExplanation != current.detailedExplanation ||
            base.detailedExplanationUpdatedAt != current.detailedExplanationUpdatedAt ||
            base.explanationContentUpdatedAt != current.explanationContentUpdatedAt
        ) {
            merged = merged.copy(
                detailedExplanation = current.detailedExplanation,
                detailedExplanationUpdatedAt = current.detailedExplanationUpdatedAt,
                explanationContentUpdatedAt = current.explanationContentUpdatedAt
            )
        }
        if (base.hint != current.hint ||
            base.hintUpdatedAt != current.hintUpdatedAt ||
            base.hintContentUpdatedAt != current.hintContentUpdatedAt
        ) {
            merged = merged.copy(
                hint = current.hint,
                hintUpdatedAt = current.hintUpdatedAt,
                hintContentUpdatedAt = current.hintContentUpdatedAt
            )
        }

        val followUps = unionById(remote.followUpChats, current.followUpChats, FollowUpChat::id)
        if (followUps != remote.followUpChats) {
            merged = merged.copy(
                followUpChats = followUps.sortedWith(
                    compareBy<FollowUpChat> { it.createdAt }.thenBy(FollowUpChat::id)
                ),
                followUpContentUpdatedAt = maxOfNullable(
                    remote.followUpContentUpdatedAt,
                    current.followUpContentUpdatedAt
                )
            )
        }
        return merged
    }

    private fun contentChanged(base: Question, current: Question): Boolean =
        base.title != current.title ||
            base.category != current.category ||
            base.grade != current.grade ||
            base.questionType != current.questionType ||
            base.source != current.source ||
            base.questionText != current.questionText ||
            base.userAnswer != current.userAnswer ||
            base.correctAnswer != current.correctAnswer ||
            base.errorCause != current.errorCause ||
            base.tags != current.tags ||
            base.contentUpdatedAt != current.contentUpdatedAt

    private fun <T> unionById(first: List<T>, second: List<T>, id: (T) -> String): List<T> =
        (first + second).associateBy(id).values.toList()

    private fun maxOfNullable(first: Long?, second: Long?): Long? =
        listOfNotNull(first, second).maxOrNull()
}
