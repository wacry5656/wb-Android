package com.wrongbook.app.data.mapper

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wrongbook.app.data.local.QuestionEntity
import com.wrongbook.app.model.FollowUpChat
import com.wrongbook.app.model.FollowUpChatIds
import com.wrongbook.app.model.ImageRef
import com.wrongbook.app.model.Question
import com.wrongbook.app.model.QuestionAnalysis
import com.wrongbook.app.model.ReviewStatus
import com.wrongbook.app.model.ReviewEvent
import com.wrongbook.app.model.SubjectCatalog
import com.wrongbook.app.model.SyncStatus

object QuestionMapper {

    private val gson = Gson()

    fun entityToDomain(entity: QuestionEntity): Question {
        val parsedImageRefs = entity.imageRefs?.let {
            runCatching {
                gson.fromJson<List<ImageRef>>(it, object : TypeToken<List<ImageRef>>() {}.type)
            }.getOrNull()
        } ?: emptyList()
        val parsedNoteImageRefs = entity.noteImageRefs?.let {
            runCatching {
                gson.fromJson<List<ImageRef>>(it, object : TypeToken<List<ImageRef>>() {}.type)
            }.getOrNull()
        } ?: emptyList()
        val parsedReviewEvents = entity.reviewEvents?.let {
            runCatching {
                gson.fromJson<List<ReviewEvent>>(it, object : TypeToken<List<ReviewEvent>>() {}.type)
            }.getOrNull()
        }.orEmpty().ifEmpty {
            legacyReviewEvents(
                questionId = entity.id,
                reviewCount = entity.reviewCount,
                reviewedAt = entity.lastReviewedAt ?: entity.reviewUpdatedAt ?: entity.updatedAt
            )
        }

        return Question(
            id = entity.id,
            title = entity.title,
            category = SubjectCatalog.normalize(entity.category),
            grade = entity.grade,
            questionType = entity.questionType,
            source = entity.source,
            questionText = entity.questionText,
            userAnswer = entity.userAnswer,
            correctAnswer = entity.correctAnswer,
            notes = entity.notes.orEmpty(),
            errorCause = entity.errorCause,
            tags = entity.tags?.let {
                runCatching {
                    gson.fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type)
                }.getOrNull()
            } ?: emptyList(),
            masteryLevel = entity.masteryLevel,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deleted = entity.deleted,
            deletedAt = entity.deletedAt,
            restoredAt = entity.restoredAt,
            syncStatus = runCatching { SyncStatus.valueOf(entity.syncStatus) }.getOrDefault(SyncStatus.PENDING),
            contentUpdatedAt = entity.contentUpdatedAt,
            imageRefsUpdatedAt = entity.imageRefsUpdatedAt
                ?: parsedImageRefs.takeIf { it.isNotEmpty() }?.let { entity.contentUpdatedAt },
            reviewCount = successfulReviewCount(parsedReviewEvents),
            lastReviewedAt = entity.lastReviewedAt,
            nextReviewAt = entity.nextReviewAt,
            reviewStatus = when (entity.reviewStatus.uppercase()) {
                "REVIEWING", "MASTERED" -> ReviewStatus.REVIEWING
                else -> ReviewStatus.NEW
            },
            notesUpdatedAt = entity.notesUpdatedAt
                ?: entity.notes?.takeIf { it.isNotBlank() }?.let { entity.updatedAt },
            noteImagesUpdatedAt = entity.noteImagesUpdatedAt
                ?: parsedNoteImageRefs.takeIf { it.isNotEmpty() }?.let { entity.updatedAt },
            reviewUpdatedAt = entity.reviewUpdatedAt
                ?: if (entity.reviewCount > 0 || entity.lastReviewedAt != null) {
                    entity.lastReviewedAt ?: entity.updatedAt
                } else {
                    null
                },
            reviewEvents = parsedReviewEvents,
            analysis = entity.analysis?.let {
                runCatching { gson.fromJson(it, QuestionAnalysis::class.java) }.getOrNull()
            },
            analysisContentUpdatedAt = entity.analysisContentUpdatedAt,
            detailedExplanation = entity.detailedExplanation,
            detailedExplanationUpdatedAt = entity.detailedExplanationUpdatedAt,
            explanationContentUpdatedAt = entity.explanationContentUpdatedAt,
            hint = entity.hint,
            hintUpdatedAt = entity.hintUpdatedAt,
            hintContentUpdatedAt = entity.hintContentUpdatedAt,
            followUpChats = entity.followUpChats?.let { serialized ->
                runCatching {
                    gson.fromJson<List<FollowUpChat>>(
                        serialized,
                        object : TypeToken<List<FollowUpChat>>() {}.type
                    )
                }.getOrNull()?.let { chats ->
                    FollowUpChatIds.ensureStable(
                        questionId = entity.id,
                        chats = chats,
                        fallbackCreatedAt = entity.createdAt
                    )
                }
            } ?: emptyList(),
            followUpContentUpdatedAt = entity.followUpContentUpdatedAt,
            imageRefs = parsedImageRefs,
            noteImageRefs = parsedNoteImageRefs
        )
    }

    fun domainToEntity(question: Question): QuestionEntity {
        return QuestionEntity(
            id = question.id,
            title = question.title,
            category = SubjectCatalog.normalize(question.category),
            grade = question.grade,
            questionType = question.questionType,
            source = question.source,
            questionText = question.questionText,
            userAnswer = question.userAnswer,
            correctAnswer = question.correctAnswer,
            notes = question.notes,
            errorCause = question.errorCause,
            tags = if (question.tags.isNotEmpty()) gson.toJson(question.tags) else null,
            masteryLevel = question.masteryLevel,
            createdAt = question.createdAt,
            updatedAt = question.updatedAt,
            deleted = question.deleted,
            deletedAt = question.deletedAt,
            restoredAt = question.restoredAt,
            syncStatus = question.syncStatus.name,
            contentUpdatedAt = question.contentUpdatedAt,
            imageRefsUpdatedAt = question.imageRefsUpdatedAt,
            reviewCount = question.reviewCount,
            lastReviewedAt = question.lastReviewedAt,
            nextReviewAt = question.nextReviewAt,
            reviewStatus = question.reviewStatus.name,
            notesUpdatedAt = question.notesUpdatedAt,
            noteImagesUpdatedAt = question.noteImagesUpdatedAt,
            reviewUpdatedAt = question.reviewUpdatedAt,
            reviewEvents = if (question.reviewEvents.isNotEmpty()) gson.toJson(question.reviewEvents) else null,
            analysis = question.analysis?.let { gson.toJson(it) },
            analysisContentUpdatedAt = question.analysisContentUpdatedAt,
            detailedExplanation = question.detailedExplanation,
            detailedExplanationUpdatedAt = question.detailedExplanationUpdatedAt,
            explanationContentUpdatedAt = question.explanationContentUpdatedAt,
            hint = question.hint,
            hintUpdatedAt = question.hintUpdatedAt,
            hintContentUpdatedAt = question.hintContentUpdatedAt,
            followUpChats = if (question.followUpChats.isNotEmpty()) gson.toJson(question.followUpChats) else null,
            followUpContentUpdatedAt = question.followUpContentUpdatedAt,
            imageRefs = if (question.imageRefs.isNotEmpty()) gson.toJson(question.imageRefs) else null,
            noteImageRefs = if (question.noteImageRefs.isNotEmpty()) gson.toJson(question.noteImageRefs) else null
        )
    }

    private fun legacyReviewEvents(
        questionId: String,
        reviewCount: Int,
        reviewedAt: Long
    ): List<ReviewEvent> =
        (1..reviewCount.coerceAtLeast(0)).map { index ->
            ReviewEvent(
                id = "legacy-review:$questionId:$index",
                kind = ReviewEvent.KIND_REVIEW,
                reviewedAt = reviewedAt,
                quality = 2
            )
        }

    private fun successfulReviewCount(events: List<ReviewEvent>): Int {
        val revertedIds = events.asSequence()
            .filter(ReviewEvent::isRevert)
            .mapNotNull(ReviewEvent::targetEventId)
            .toSet()
        return events.count { event ->
            event.isReview && event.id !in revertedIds && (event.quality ?: 2) > 0
        }
    }
}
