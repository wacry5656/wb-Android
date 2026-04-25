package com.wrongbook.app.data.local

import com.wrongbook.app.data.mapper.QuestionMapper
import com.wrongbook.app.model.Question
import com.wrongbook.app.model.ReviewStatus
import com.wrongbook.app.model.SyncStatus
import java.util.UUID

object SeedData {

    fun getSampleQuestions(): List<QuestionEntity> {
        val now = System.currentTimeMillis()
        val yesterday = now - 86_400_000L
        val twoDaysAgo = now - 2 * 86_400_000L

        return listOf(
            Question(
                id = UUID.randomUUID().toString(),
                title = "二次函数求最值问题",
                category = "数学",
                questionText = "已知函数 f(x) = x² - 4x + 3，求函数在 [0, 5] 上的最小值和最大值。",
                userAnswer = "最小值为 0，最大值为 8",
                correctAnswer = "f(x) = (x-2)² - 1\n最小值：f(2) = -1\n最大值：f(5) = 8",
                notes = "容易忘记配方法的步骤，需要先确定顶点位置再判断区间内的最值",
                createdAt = twoDaysAgo,
                updatedAt = twoDaysAgo,
                nextReviewAt = now - 3_600_000L,
                reviewStatus = ReviewStatus.NEW,
                syncStatus = SyncStatus.PENDING
            ),
            Question(
                id = UUID.randomUUID().toString(),
                title = "光合作用条件判断",
                category = "生物",
                questionText = "某绿色植物在有光、适宜温度和充足二氧化碳条件下进行光合作用。请说明光合作用的主要场所和产物。",
                userAnswer = "主要场所是线粒体，产物是氧气和水。",
                correctAnswer = "光合作用的主要场所是叶绿体，主要产物是有机物和氧气。线粒体主要参与呼吸作用。",
                notes = "容易把叶绿体和线粒体的功能混淆，复习时要区分光合作用和呼吸作用。",
                createdAt = yesterday,
                updatedAt = yesterday,
                nextReviewAt = now + 86_400_000L,
                reviewStatus = ReviewStatus.NEW,
                syncStatus = SyncStatus.PENDING
            ),
            Question(
                id = UUID.randomUUID().toString(),
                title = "牛顿第二定律应用",
                category = "物理",
                questionText = "一个质量为 2kg 的物体在光滑水平面上受到 10N 的水平推力，求物体的加速度。",
                userAnswer = "a = 10/2 = 5 m/s",
                correctAnswer = "由 F = ma 得 a = F/m = 10/2 = 5 m/s²\n注意加速度的单位是 m/s²，不是 m/s。",
                notes = "计算没问题，但单位写错了。加速度的单位是 m/s² 不是 m/s。",
                createdAt = now - 3_600_000L,
                updatedAt = now - 3_600_000L,
                nextReviewAt = now - 1_800_000L,
                reviewStatus = ReviewStatus.NEW,
                syncStatus = SyncStatus.PENDING
            ),
            Question(
                id = UUID.randomUUID().toString(),
                title = "化学方程式配平",
                category = "化学",
                questionText = "配平以下化学方程式：Fe + O₂ → Fe₃O₄",
                userAnswer = "Fe + O₂ = Fe₃O₄",
                correctAnswer = "3Fe + 2O₂ →(点燃) Fe₃O₄\n铁在氧气中燃烧生成四氧化三铁。",
                notes = "配平时先从原子数最多的开始调系数，注意条件标注。",
                createdAt = now - 7_200_000L,
                updatedAt = now - 7_200_000L,
                nextReviewAt = now + 2 * 86_400_000L,
                reviewStatus = ReviewStatus.NEW,
                syncStatus = SyncStatus.PENDING
            )
        ).map(QuestionMapper::domainToEntity)
    }
}
