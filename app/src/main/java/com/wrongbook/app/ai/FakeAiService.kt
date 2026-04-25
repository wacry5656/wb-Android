package com.wrongbook.app.ai

import com.wrongbook.app.model.FollowUpChat
import com.wrongbook.app.model.Question
import com.wrongbook.app.model.QuestionAnalysis
import kotlinx.coroutines.delay
import kotlin.math.abs

class FakeAiService : AiService {

    override suspend fun analyze(question: Question): QuestionAnalysis {
        delay(800)
        val category = question.category.ifEmpty { "综合" }
        val seed = abs(question.title.hashCode())
        val score = (seed % 5) + 1
        val difficulty = when {
            score <= 2 -> "简单"
            score <= 4 -> "中等"
            else -> "困难"
        }

        return QuestionAnalysis(
            difficulty = difficulty,
            difficultyScore = score,
            knowledgePoints = buildList {
                add("${category}核心概念理解")
                add("基础知识灵活运用")
                if (score >= 3) add("综合分析与推理能力")
                if (score >= 4) add("多知识点融合应用")
            },
            commonMistakes = buildList {
                add("审题不仔细，遗漏关键条件或隐含信息")
                add("基本概念混淆，导致解题方向偏差")
                if (!question.userAnswer.isNullOrBlank() && !question.correctAnswer.isNullOrBlank()) {
                    add("你的作答与正确答案的主要差异在于关键步骤的处理")
                }
                add("计算或推导过程中出现低级错误")
            },
            solutionMethods = listOf("先审题标条件", "建立对应模型", "分步骤推导并回代检验"),
            notices = listOf(
                "做题前先通读题目，标记关键词和已知条件",
                "解题过程中保持步骤清晰，方便回头检查",
                "完成后将结果代入原题验证合理性"
            ),
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun generateDetailedExplanation(question: Question): String {
        delay(1000)
        val category = question.category.ifEmpty { "该科目" }

        return buildString {
            appendLine("## 详细解析")
            appendLine()
            appendLine("### 一、题目分析")
            appendLine("本题属于${category}的典型题目。需要从题意出发，明确已知条件与求解目标，再选择合适的方法逐步求解。")
            appendLine()

            if (!question.questionText.isNullOrBlank()) {
                appendLine("### 二、题目要点")
                appendLine("题干核心内容：${question.questionText.take(100)}")
                appendLine("解题时需要特别关注题目中的限定条件和关键数据。")
                appendLine()
            }

            appendLine("### 三、解题步骤")
            appendLine("**第一步：审题**")
            appendLine("仔细阅读题目，提取所有已知条件，明确求解目标。")
            appendLine()
            appendLine("**第二步：分析**")
            appendLine("根据题意判断应使用的知识点和方法，建立解题思路。")
            appendLine()
            appendLine("**第三步：求解**")
            appendLine("按照建立的思路，严格按步骤推导或计算，确保每一步都有依据。")
            appendLine()
            appendLine("**第四步：验证**")
            appendLine("将所得结果代回原题进行检验，确认结果的合理性与完整性。")
            appendLine()

            if (!question.correctAnswer.isNullOrBlank()) {
                appendLine("### 四、正确答案解读")
                appendLine(question.correctAnswer)
                appendLine()
            }

            if (!question.userAnswer.isNullOrBlank() && !question.correctAnswer.isNullOrBlank()) {
                appendLine("### 五、你的作答分析")
                appendLine("你的答案：${question.userAnswer}")
                appendLine()
                appendLine("与正确答案对比后，建议关注以下方面：")
                appendLine("1. 检查解题思路是否与标准解法一致")
                appendLine("2. 排查是否存在概念理解偏差")
                appendLine("3. 检查计算过程是否有疏漏")
                appendLine()
            }

            appendLine("### 总结")
            appendLine("这类题目的关键在于对${category}基础概念的透彻理解和规范的解题流程。建议在后续复习中多做同类型题目的变式训练，巩固解题方法。")
        }
    }

    override suspend fun generateHint(question: Question): String {
        delay(600)
        val category = question.category.ifEmpty { "这道题" }

        return buildString {
            appendLine("💡 思路指引")
            appendLine()
            appendLine("先不要急着动笔，按下面的顺序想一想：")
            appendLine()
            appendLine("1. 这道题考的是${category}的哪个知识点？你能用一句话概括吗？")
            appendLine()
            appendLine("2. 题目给出了哪些已知条件？逐一列出来。有没有隐藏的条件？")
            appendLine()
            appendLine("3. 求解目标是什么？需要得到一个什么样的结果？")
            appendLine()
            appendLine("4. 你之前做过类似的题吗？当时用了什么方法？能不能迁移过来？")
            appendLine()
            appendLine("5. 如果正面思路走不通，能否尝试：")
            appendLine("   - 从特殊情况入手，先用简单的数字试试")
            appendLine("   - 画图或列表来整理条件之间的关系")
            appendLine("   - 逆向思考，从结论反推需要什么条件")
            appendLine()
            appendLine("如果以上都尝试过了还是没有思路，可以试着把题目拆解成更小的子问题，逐个击破。")
        }
    }

    override suspend fun followUp(question: Question, userMessage: String): FollowUpChat {
        delay(700)
        val response = when {
            userMessage.contains("不懂") || userMessage.contains("不理解") || userMessage.contains("不明白") ->
                buildString {
                    appendLine("我理解你的困惑，让我换个角度解释。")
                    appendLine()
                    appendLine("这道题的核心其实可以简化为一个关键步骤：找到已知条件之间的联系。")
                    appendLine()
                    appendLine("建议你这样做：")
                    appendLine("1. 把每个已知条件单独写在纸上")
                    appendLine("2. 思考这些条件之间有什么关系")
                    appendLine("3. 看看哪些关系可以帮你推导出答案")
                    appendLine()
                    appendLine("如果还有具体哪一步不理解，可以继续告诉我。")
                }

            userMessage.contains("为什么") ->
                buildString {
                    appendLine("这是一个很好的问题！理解\"为什么\"比记住\"是什么\"重要得多。")
                    appendLine()
                    appendLine("根本原因在于这里涉及的基本原理：当满足特定条件时，对应的结论是必然成立的。")
                    appendLine()
                    appendLine("你可以从定义出发来理解：")
                    appendLine("- 先写出相关概念的准确定义")
                    appendLine("- 逐条对照题目条件")
                    appendLine("- 你会发现结论自然而然就推出来了")
                    appendLine()
                    appendLine("这就是数理推导的魅力——每一步都有严格的逻辑支撑。")
                }

            userMessage.contains("举例") || userMessage.contains("例子") || userMessage.contains("比如") ->
                buildString {
                    appendLine("好的，给你一个更简单的同类型例子：")
                    appendLine()
                    appendLine("假设把题目中的数值换成更小的、更好算的数字。")
                    appendLine("解题步骤完全一样，只是运算更简单。")
                    appendLine()
                    appendLine("你可以先在这个简化版上练手：")
                    appendLine("1. 按照标准步骤解一遍")
                    appendLine("2. 确认每一步你都理解")
                    appendLine("3. 然后回来做原题，方法一模一样")
                    appendLine()
                    appendLine("这种\"从简单到复杂\"的方法对攻克难题很有效。")
                }

            userMessage.contains("还有") || userMessage.contains("其他方法") || userMessage.contains("别的") ->
                buildString {
                    appendLine("当然有其他思路可以参考：")
                    appendLine()
                    appendLine("**方法一：逆向思维**")
                    appendLine("从结论出发，反推需要什么中间条件，再看题目是否提供了这些条件。")
                    appendLine()
                    appendLine("**方法二：类比法**")
                    appendLine("回忆你做过的类似题目，把那道题的解法\"平移\"过来试试。")
                    appendLine()
                    appendLine("**方法三：分类讨论**")
                    appendLine("把问题按某个关键变量分成几种情况，分别分析后再合并。")
                    appendLine()
                    appendLine("想深入了解哪种方法？我可以展开讲讲。")
                }

            userMessage.contains("总结") || userMessage.contains("归纳") ->
                buildString {
                    appendLine("好的，我来帮你做个总结归纳：")
                    appendLine()
                    appendLine("**本题核心知识点：**")
                    appendLine("- ${question.category.ifEmpty { "综合" }}基础概念的理解和应用")
                    appendLine()
                    appendLine("**解题关键步骤：**")
                    appendLine("1. 审题提取条件 → 2. 选择方法 → 3. 规范求解 → 4. 验证结果")
                    appendLine()
                    appendLine("**易错点提醒：**")
                    appendLine("- 注意隐含条件")
                    appendLine("- 注意单位和符号")
                    appendLine("- 注意结果的合理性检验")
                    appendLine()
                    appendLine("建议把这个总结记在笔记里，复习的时候会很有帮助。")
                }

            else ->
                buildString {
                    appendLine("针对你提到的问题，我的建议如下：")
                    appendLine()
                    appendLine("首先，确保你对题目中涉及的核心概念有清晰的理解。如果某个概念模糊，先翻课本把定义搞清楚。")
                    appendLine()
                    appendLine("其次，注意题目条件之间的关联性。很多时候答案就藏在条件的组合之中，试着把条件两两配对看看能推出什么。")
                    appendLine()
                    appendLine("最后，做完之后一定要验证，看结果是否符合题意、是否在合理范围内。")
                    appendLine()
                    appendLine("如果你有更具体的疑问，比如某个步骤看不懂、某个概念不清楚，可以继续追问，我会针对性地解答。")
                }
        }

        return FollowUpChat(
            role = "assistant",
            content = response.trimEnd(),
            createdAt = System.currentTimeMillis()
        )
    }
}
