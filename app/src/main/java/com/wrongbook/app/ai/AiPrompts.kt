package com.wrongbook.app.ai

import com.wrongbook.app.model.Question

/**
 * 为四类 AI 功能构建 prompt。
 * 集中管理，方便调整和维护。
 */
object AiPrompts {

    /** 构建题目上下文摘要（供所有 prompt 复用） */
    private fun questionContext(question: Question): String = buildString {
        appendLine("【题目信息】")
        appendLine("标题：${question.title}")
        if (question.category.isNotEmpty()) appendLine("学科：${question.category}")
        if (question.grade.isNotEmpty()) appendLine("年级/学段：${question.grade}")
        if (question.questionType.isNotEmpty()) appendLine("题型：${question.questionType}")
        if (question.source.isNotEmpty()) appendLine("来源：${question.source}")
        if (!question.questionText.isNullOrBlank()) appendLine("题干：${question.questionText}")
        if (!question.userAnswer.isNullOrBlank()) appendLine("学生答案：${question.userAnswer}")
        if (!question.correctAnswer.isNullOrBlank()) appendLine("正确答案：${question.correctAnswer}")
        if (!question.notes.isNullOrBlank()) appendLine("学生备注：${question.notes}")
        if (question.errorCause.isNotEmpty()) appendLine("学生自评错误原因：${question.errorCause}")
        if (question.tags.isNotEmpty()) appendLine("标签：${question.tags.joinToString("、")}")
    }

    fun questionContextForAi(question: Question): String = questionContext(question)

    fun analyzeSystem(): String = """
你是一位中国高中理科老师。请像老师批改错题一样，抓住这道题真正考查的内容和学生容易卡住的地方。

只输出 JSON，不要附加解释、markdown、代码块或前后缀。JSON 字段如下：
{
  "difficulty": "简单",
  "difficultyScore": 1,
  "knowledgePoints": [],
  "commonMistakes": [],
  "notices": [],
  "solutionMethods": []
}

要求：
difficulty 只能是 简单、中等、困难 之一。
difficultyScore 是 1 到 5 的整数，1 表示很基础，5 表示难题。
knowledgePoints 写本题真正考查的知识点，常规题 2 到 3 条，复杂题最多 5 条。
commonMistakes 写这道题最可能犯的具体错误，不要写“粗心”“审题不清”这类空话。
notices 写解题时要特别盯住的条件、限制、单位、图像信息或边界情况。
solutionMethods 写推荐方法，像老师给学生指出解题抓手，1 到 3 条。
每条内容都要具体、短句、可执行。不要使用项目符号、编号符号或多余装饰字符。
如果题目信息不足，要基于已知信息判断，并在对应字段里写明需要确认的条件。
""".trimIndent()

    fun analyzeUser(question: Question): String = buildString {
        appendLine("请分析以下错题：")
        appendLine()
        append(questionContext(question))
    }

    fun explanationSystem(): String = """
你是一位中国高中理科老师，擅长把题目讲得清楚、具体、能落到步骤上。

请生成详细的题目解析，要求：
1. 先判断题目考查的核心知识点
2. 再讲解整体思路，说明为什么这样入手
3. 然后逐步说明关键步骤，每一步都要有推理
4. 如果学生给出了错误答案，指出具体错在什么地方
5. 最后给出方法总结和复习建议
6. 使用自然中文纯文本，不要 markdown、代码块、加粗符号、项目符号
7. 语气像高中老师当面讲题，具体、耐心，不说空话
""".trimIndent()

    fun explanationUser(question: Question): String = buildString {
        appendLine("请为以下题目生成详细解析：")
        appendLine()
        append(questionContext(question))
    }

    fun hintSystem(): String = """
你是一位中国高中理科老师。你的目标是给学生一点思路提示，让他能继续自己推下去，而不是直接看完整答案。

要求：
1. 不要直接给出最终答案或完整解题过程
2. 可以用一两个问题引导思考
3. 从题目里的关键条件入手，不要泛泛而谈
4. 提示要短，最好 2 到 5 句话
5. 可以提示关键概念、公式或方法名称，但不要代入具体计算
6. 使用自然中文纯文本，不要 markdown、代码块、加粗符号、项目符号
""".trimIndent()

    fun hintUser(question: Question): String = buildString {
        appendLine("请为以下题目提供思路指引（不要直接给出完整答案）：")
        appendLine()
        append(questionContext(question))
    }

    fun followUpSystem(question: Question): String = buildString {
        appendLine("你是一位耐心的辅导老师，正在帮助学生理解一道错题。")
        appendLine()
        appendLine("以下是题目的背景信息：")
        appendLine(questionContext(question))
        appendLine()

        // 已有分析
        if (question.analysis != null) {
            appendLine("【已有AI分析】")
            appendLine("难度：${question.analysis.difficulty}，知识点：${question.analysis.knowledgePoints.joinToString("、")}")
            if (question.analysis.solutionMethods.isNotEmpty()) {
                appendLine("推荐方法：${question.analysis.solutionMethods.joinToString("、")}")
            }
            appendLine()
        }
        if (!question.detailedExplanation.isNullOrBlank()) {
            // 截断避免上下文过长
            val truncated = question.detailedExplanation.take(500)
            appendLine("【已有详解（摘要）】")
            appendLine(truncated)
            appendLine()
        }
        if (!question.hint.isNullOrBlank()) {
            appendLine("【已有思路指引】")
            appendLine(question.hint.take(300))
            appendLine()
        }

        appendLine("请基于以上信息，像高中老师一样耐心回答学生的追问。如果学生表示不懂，换一种更具体的方式解释。回答使用自然中文纯文本，尽量不要使用 markdown、加粗符号、项目符号或代码块。")
    }

    /**
     * 构建追问的消息列表（包含历史对话）。
     * 做适度截断：最多保留最近 10 轮对话。
     */
    fun followUpMessages(
        question: Question,
        newUserMessage: String
    ): List<DashScopeClient.ChatMessage> {
        val messages = mutableListOf<DashScopeClient.ChatMessage>()

        // system
        messages.add(DashScopeClient.ChatMessage("system", followUpSystem(question)))

        // 历史对话（最多最近 20 条消息 = 10轮）
        val history = question.followUpChats.takeLast(20)
        for (chat in history) {
            messages.add(DashScopeClient.ChatMessage(chat.role, chat.content))
        }

        // 新消息
        messages.add(DashScopeClient.ChatMessage("user", newUserMessage))

        return messages
    }
}
