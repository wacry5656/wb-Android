package com.wrongbook.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val grade: String,
    val questionType: String,
    val source: String,
    val questionText: String?,
    val userAnswer: String?,
    val correctAnswer: String?,
    val notes: String?,
    val errorCause: String,
    val tags: String?,               // JSON serialized List<String>
    val masteryLevel: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val deletedAt: Long?,
    val syncStatus: String,
    val contentUpdatedAt: Long,
    val reviewCount: Int,
    val lastReviewedAt: Long?,
    val nextReviewAt: Long?,
    val reviewStatus: String,
    val notesUpdatedAt: Long?,
    val noteImagesUpdatedAt: Long?,
    val reviewUpdatedAt: Long?,
    val analysis: String?,           // JSON serialized QuestionAnalysis
    val analysisContentUpdatedAt: Long?,
    val detailedExplanation: String?,
    val detailedExplanationUpdatedAt: Long?,
    val explanationContentUpdatedAt: Long?,
    val hint: String?,
    val hintUpdatedAt: Long?,
    val hintContentUpdatedAt: Long?,
    val followUpChats: String?,      // JSON serialized List<FollowUpChat>
    val followUpContentUpdatedAt: Long?,
    val imageRefs: String?,          // JSON serialized List<ImageRef>
    val noteImageRefs: String?       // JSON serialized List<ImageRef>
)
