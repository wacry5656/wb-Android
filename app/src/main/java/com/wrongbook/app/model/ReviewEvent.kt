package com.wrongbook.app.model

data class ReviewEvent(
    val id: String,
    val kind: String,
    val reviewedAt: Long,
    val quality: Int? = null,
    val targetEventId: String? = null,
    val deviceId: String? = null
) {
    val isReview: Boolean
        get() = kind == KIND_REVIEW

    val isRevert: Boolean
        get() = kind == KIND_REVERT

    companion object {
        const val KIND_REVIEW = "review"
        const val KIND_REVERT = "revert"
    }
}
