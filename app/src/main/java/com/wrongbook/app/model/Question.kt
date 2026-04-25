package com.wrongbook.app.model

data class Question(
    val id: String,
    val title: String,
    val category: String = "",
    val grade: String = "",
    val questionType: String = "",
    val source: String = "",
    val questionText: String? = null,
    val userAnswer: String? = null,
    val correctAnswer: String? = null,
    val notes: String? = null,
    val errorCause: String = "",
    val tags: List<String> = emptyList(),
    val masteryLevel: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deletedAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val contentUpdatedAt: Long = updatedAt,
    val reviewCount: Int = 0,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long? = null,
    val reviewStatus: ReviewStatus = ReviewStatus.NEW,
    val analysis: QuestionAnalysis? = null,
    val analysisContentUpdatedAt: Long? = null,
    val detailedExplanation: String? = null,
    val explanationContentUpdatedAt: Long? = null,
    val hint: String? = null,
    val hintContentUpdatedAt: Long? = null,
    val followUpChats: List<FollowUpChat> = emptyList(),
    val followUpContentUpdatedAt: Long? = null,
    val imageRefs: List<ImageRef> = emptyList(),
    val noteImageRefs: List<ImageRef> = emptyList()
) {
    val isAnalysisStale: Boolean
        get() = analysis != null && analysisContentUpdatedAt != contentUpdatedAt

    val isDetailedExplanationStale: Boolean
        get() = !detailedExplanation.isNullOrBlank() && explanationContentUpdatedAt != contentUpdatedAt

    val isHintStale: Boolean
        get() = !hint.isNullOrBlank() && hintContentUpdatedAt != contentUpdatedAt

    val isFollowUpStale: Boolean
        get() = followUpChats.isNotEmpty() && followUpContentUpdatedAt != contentUpdatedAt

    val hasAnyStaleAiContent: Boolean
        get() = isAnalysisStale || isDetailedExplanationStale || isHintStale || isFollowUpStale
}
