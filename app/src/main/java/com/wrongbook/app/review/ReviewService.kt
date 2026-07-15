package com.wrongbook.app.review

import com.wrongbook.app.model.Question
import com.wrongbook.app.model.ReviewEvent
import com.wrongbook.app.model.ReviewStatus
import java.util.UUID

/** Review scheduling derived from append-only, mergeable events. */
object ReviewService {

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** Successful review number 1/2/3/4/5+ maps to 1/3/7/14/30 days. */
    fun getNextIntervalDays(successfulReviewCount: Int): Int = when (successfulReviewCount) {
        1 -> 1
        2 -> 3
        3 -> 7
        4 -> 14
        else -> 30
    }

    /** quality: 0=不会，1=模糊，2=会，3=熟练。 */
    fun completeReview(
        question: Question,
        quality: Int = 2,
        deviceId: String? = null,
        now: Long = System.currentTimeMillis()
    ): Question {
        val normalizedQuality = quality.coerceIn(0, 3)
        val events = ensureLegacyEvents(question) + ReviewEvent(
            id = UUID.randomUUID().toString(),
            kind = ReviewEvent.KIND_REVIEW,
            reviewedAt = now,
            quality = normalizedQuality,
            deviceId = deviceId?.takeIf(String::isNotBlank)
        )
        return deriveReviewState(question, events, mutationTime = now)
    }

    /** Adds a revert event, so concurrent devices can merge the undo without decrement races. */
    fun revertLatestReview(
        question: Question,
        deviceId: String? = null,
        now: Long = System.currentTimeMillis()
    ): Question? {
        val events = ensureLegacyEvents(question)
        val revertedIds = events.asSequence()
            .filter(ReviewEvent::isRevert)
            .mapNotNull(ReviewEvent::targetEventId)
            .toSet()
        val target = events.asSequence()
            .filter(ReviewEvent::isReview)
            .filter { (it.quality ?: 2) > 0 }
            .filterNot { it.id in revertedIds }
            .maxWithOrNull(compareBy<ReviewEvent> { it.reviewedAt }.thenBy { it.id })
            ?: return null
        val nextEvents = events + ReviewEvent(
            id = UUID.randomUUID().toString(),
            kind = ReviewEvent.KIND_REVERT,
            reviewedAt = now,
            targetEventId = target.id,
            deviceId = deviceId?.takeIf(String::isNotBlank)
        )
        return deriveReviewState(question, nextEvents, mutationTime = now)
    }

    fun postponeReview(
        question: Question,
        now: Long = System.currentTimeMillis()
    ): Question = question.copy(
        nextReviewAt = now + DAY_MS,
        reviewUpdatedAt = now,
        updatedAt = now
    )

    fun initializeReview(createdAt: Long): Pair<Long, ReviewStatus> =
        Pair(createdAt + DAY_MS, ReviewStatus.NEW)

    fun successfulReviewCount(events: List<ReviewEvent>): Int =
        activeReviews(events).count { (it.quality ?: 2) > 0 }

    fun deriveFromMergedEvents(
        question: Question,
        events: List<ReviewEvent>,
        reviewUpdatedAt: Long
    ): Question {
        val derived = deriveReviewState(question, events, mutationTime = reviewUpdatedAt)
        val latestEventAt = events.maxOfOrNull(ReviewEvent::reviewedAt) ?: Long.MIN_VALUE
        val postponeUpdatedAt = question.reviewUpdatedAt
        return if (postponeUpdatedAt != null && postponeUpdatedAt > latestEventAt) {
            derived.copy(
                nextReviewAt = question.nextReviewAt,
                reviewUpdatedAt = postponeUpdatedAt,
                updatedAt = maxOf(question.updatedAt, derived.updatedAt)
            )
        } else {
            derived.copy(updatedAt = maxOf(question.updatedAt, derived.updatedAt))
        }
    }

    private fun deriveReviewState(
        question: Question,
        events: List<ReviewEvent>,
        mutationTime: Long
    ): Question {
        val active = activeReviews(events)
        var successfulCount = 0
        var nextReviewAt: Long? = null
        var masteryLevel = 0
        active.forEach { event ->
            val quality = (event.quality ?: 2).coerceIn(0, 3)
            if (quality > 0) successfulCount += 1
            val intervalDays = getNextIntervalDays(successfulCount.coerceAtLeast(1))
            nextReviewAt = when (quality) {
                0 -> event.reviewedAt + 10L * 60L * 1000L
                1 -> event.reviewedAt + DAY_MS
                2 -> event.reviewedAt + intervalDays * DAY_MS
                else -> event.reviewedAt + intervalDays * 2L * DAY_MS
            }
            masteryLevel = when (quality) {
                0 -> 0
                1 -> maxOf(masteryLevel, 1)
                2 -> maxOf(masteryLevel, 3)
                else -> 5
            }
        }

        return question.copy(
            reviewEvents = events.distinctBy(ReviewEvent::id),
            reviewCount = successfulCount,
            lastReviewedAt = active.lastOrNull()?.reviewedAt,
            nextReviewAt = nextReviewAt ?: initializeReview(question.createdAt).first,
            reviewStatus = if (active.isEmpty()) ReviewStatus.NEW else ReviewStatus.REVIEWING,
            masteryLevel = masteryLevel,
            reviewUpdatedAt = mutationTime,
            updatedAt = mutationTime
        )
    }

    private fun activeReviews(events: List<ReviewEvent>): List<ReviewEvent> {
        val revertedIds = events.asSequence()
            .filter(ReviewEvent::isRevert)
            .mapNotNull(ReviewEvent::targetEventId)
            .toSet()
        return events.asSequence()
            .filter(ReviewEvent::isReview)
            .filterNot { it.id in revertedIds }
            .sortedWith(compareBy<ReviewEvent> { it.reviewedAt }.thenBy { it.id })
            .toList()
    }

    private fun ensureLegacyEvents(question: Question): List<ReviewEvent> {
        if (question.reviewEvents.isNotEmpty() || question.reviewCount <= 0) {
            return question.reviewEvents
        }
        val reviewedAt = question.lastReviewedAt ?: question.reviewUpdatedAt ?: question.updatedAt
        return (1..question.reviewCount).map { index ->
            ReviewEvent(
                id = "legacy-review:${question.id}:$index",
                kind = ReviewEvent.KIND_REVIEW,
                reviewedAt = reviewedAt,
                quality = 2
            )
        }
    }
}
