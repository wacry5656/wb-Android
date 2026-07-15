package com.wrongbook.app.review

import com.wrongbook.app.model.Question
import com.wrongbook.app.model.ReviewEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewServiceTest {
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun `successful intervals match one three seven fourteen thirty days`() {
        assertEquals(1, ReviewService.getNextIntervalDays(1))
        assertEquals(3, ReviewService.getNextIntervalDays(2))
        assertEquals(7, ReviewService.getNextIntervalDays(3))
        assertEquals(14, ReviewService.getNextIntervalDays(4))
        assertEquals(30, ReviewService.getNextIntervalDays(5))
        assertEquals(30, ReviewService.getNextIntervalDays(20))
    }

    @Test
    fun `first successful review is due in one day`() {
        val now = 2_000_000L
        val reviewed = ReviewService.completeReview(question(), quality = 2, now = now)

        assertEquals(1, reviewed.reviewCount)
        assertEquals(now + day, reviewed.nextReviewAt)
        assertEquals(3, reviewed.masteryLevel)
        assertEquals(1, reviewed.reviewEvents.size)
    }

    @Test
    fun `wrong answer resets mastery and is not selected by undo`() {
        val first = ReviewService.completeReview(question(), quality = 2, now = 2_000_000L)
        val wrong = ReviewService.completeReview(first, quality = 0, now = 3_000_000L)

        assertEquals(1, wrong.reviewCount)
        assertEquals(0, wrong.masteryLevel)
        assertEquals(3_000_000L + 10L * 60L * 1000L, wrong.nextReviewAt)

        val reverted = ReviewService.revertLatestReview(wrong, now = 4_000_000L)
        assertNotNull(reverted)
        val revertEvent = reverted!!.reviewEvents.last()
        assertEquals(ReviewEvent.KIND_REVERT, revertEvent.kind)
        assertEquals(first.reviewEvents.single().id, revertEvent.targetEventId)
        assertEquals(0, reverted.reviewCount)
        assertEquals(0, reverted.masteryLevel)
    }

    @Test
    fun `concurrent review events count independently and revert by id`() {
        val base = question()
        val one = ReviewService.completeReview(base, quality = 2, deviceId = "a", now = 2_000_000L)
        val two = ReviewService.completeReview(one, quality = 3, deviceId = "b", now = 3_000_000L)
        assertEquals(2, two.reviewCount)
        assertEquals(3_000_000L + 6L * day, two.nextReviewAt)

        val reverted = ReviewService.revertLatestReview(two, deviceId = "a", now = 4_000_000L)
        assertNotNull(reverted)
        assertEquals(1, reverted!!.reviewCount)
        assertTrue(reverted.reviewEvents.any { it.kind == ReviewEvent.KIND_REVERT })
    }

    private fun question() = Question(
        id = "q1",
        title = "题目",
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L
    )
}
