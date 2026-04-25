package com.wrongbook.app.data.mapper

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wrongbook.app.data.local.QuestionEntity
import com.wrongbook.app.model.FollowUpChat
import com.wrongbook.app.model.ImageRef
import com.wrongbook.app.model.Question
import com.wrongbook.app.model.QuestionAnalysis
import com.wrongbook.app.model.ReviewStatus
import com.wrongbook.app.model.SubjectCatalog
import com.wrongbook.app.model.SyncStatus
import java.util.UUID

object QuestionMapper {

    private val gson = Gson()

    fun entityToDomain(entity: QuestionEntity): Question {
        return Question(
            id = entity.id,
            title = entity.title,
            category = SubjectCatalog.normalize(entity.category),
            grade = entity.grade,
            questionType = entity.questionType,
            source = entity.source,
            questionText = entity.questionText.orEmpty(),
            userAnswer = entity.userAnswer.orEmpty(),
            correctAnswer = entity.correctAnswer.orEmpty(),
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
            syncStatus = runCatching { SyncStatus.valueOf(entity.syncStatus) }.getOrDefault(SyncStatus.PENDING),
            contentUpdatedAt = entity.contentUpdatedAt,
            reviewCount = entity.reviewCount,
            lastReviewedAt = entity.lastReviewedAt,
            nextReviewAt = entity.nextReviewAt,
            reviewStatus = when (entity.reviewStatus.uppercase()) {
                "REVIEWING", "MASTERED" -> ReviewStatus.REVIEWING
                else -> ReviewStatus.NEW
            },
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
            followUpChats = entity.followUpChats?.let {
                runCatching {
                    gson.fromJson<List<FollowUpChat>>(it, object : TypeToken<List<FollowUpChat>>() {}.type)
                }.getOrNull()?.map { chat ->
                    // 兼容旧数据：如果 id 为 null 则补生成
                    @Suppress("SENSELESS_COMPARISON")
                    if (chat.id == null) chat.copy(id = UUID.randomUUID().toString()) else chat
                }
            } ?: emptyList(),
            followUpContentUpdatedAt = entity.followUpContentUpdatedAt,
            imageRefs = entity.imageRefs?.let {
                runCatching {
                    gson.fromJson<List<ImageRef>>(it, object : TypeToken<List<ImageRef>>() {}.type)
                }.getOrNull()
            } ?: emptyList(),
            noteImageRefs = entity.noteImageRefs?.let {
                runCatching {
                    gson.fromJson<List<ImageRef>>(it, object : TypeToken<List<ImageRef>>() {}.type)
                }.getOrNull()
            } ?: emptyList()
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
            syncStatus = question.syncStatus.name,
            contentUpdatedAt = question.contentUpdatedAt,
            reviewCount = question.reviewCount,
            lastReviewedAt = question.lastReviewedAt,
            nextReviewAt = question.nextReviewAt,
            reviewStatus = question.reviewStatus.name,
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
}
