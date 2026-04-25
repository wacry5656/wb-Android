package com.wrongbook.app.ai

import com.wrongbook.app.model.FollowUpChat
import com.wrongbook.app.model.Question
import com.wrongbook.app.model.QuestionAnalysis

interface AiService {
    suspend fun analyze(question: Question): QuestionAnalysis
    suspend fun generateDetailedExplanation(question: Question): String
    suspend fun generateHint(question: Question): String
    suspend fun followUp(question: Question, userMessage: String): FollowUpChat
}
