package com.wrongbook.app.review

import com.wrongbook.app.model.Question
import com.wrongbook.app.model.ReviewStatus

/**
 * 集中管理复习逻辑。
 * 简单间隔重复：第1次+1天，第2次+3天，第3次+7天，第4次+14天，之后+30天。
 */
object ReviewService {

    private fun getNextIntervalDays(reviewCount: Int): Int {
        return when (reviewCount) {
            0 -> 1
            1 -> 3
            2 -> 7
            3 -> 14
            else -> 30
        }
    }

    /** 完成一次复习，quality: 0=不会，1=模糊，2=会，3=熟练。 */
    fun completeReview(question: Question, quality: Int = 2): Question {
        val now = System.currentTimeMillis()
        val normalizedQuality = quality.coerceIn(0, 3)
        val newCount = if (normalizedQuality == 0) question.reviewCount else question.reviewCount + 1
        val baseIntervalDays = getNextIntervalDays(newCount)
        val nextReview = when (normalizedQuality) {
            0 -> now + 10L * 60 * 1000
            1 -> now + 24L * 60 * 60 * 1000
            2 -> now + baseIntervalDays * 24L * 60 * 60 * 1000
            else -> now + (baseIntervalDays * 2L) * 24L * 60 * 60 * 1000
        }
        val nextMastery = when (normalizedQuality) {
            0 -> 0
            1 -> maxOf(question.masteryLevel, 1)
            2 -> maxOf(question.masteryLevel, 3)
            else -> 5
        }

        return question.copy(
            reviewCount = newCount,
            lastReviewedAt = now,
            nextReviewAt = nextReview,
            reviewStatus = if (newCount > 0) ReviewStatus.REVIEWING else ReviewStatus.NEW,
            masteryLevel = nextMastery,
            reviewUpdatedAt = now,
            updatedAt = now
        )
    }

    /** 延后复习（4小时后再提醒） */
    fun postponeReview(question: Question): Question {
        val now = System.currentTimeMillis()
        val nextReview = now + 4L * 60 * 60 * 1000

        return question.copy(
            nextReviewAt = nextReview,
            reviewUpdatedAt = now,
            updatedAt = now
        )
    }

    /** 初始化新建题目的复习字段（明天开始复习） */
    fun initializeReview(createdAt: Long): Pair<Long, ReviewStatus> {
        val nextReview = createdAt + 24L * 60 * 60 * 1000
        return Pair(nextReview, ReviewStatus.NEW)
    }
}
