package com.wrongbook.app.model

import com.google.gson.annotations.SerializedName

data class QuestionAnalysis(
    val difficulty: String = "",
    @SerializedName(value = "difficultyScore", alternate = ["difficulty_score"])
    val difficultyScore: Int = 0,
    @SerializedName(value = "knowledgePoints", alternate = ["knowledge_points"])
    val knowledgePoints: List<String> = emptyList(),
    @SerializedName(value = "commonMistakes", alternate = ["common_mistakes"])
    val commonMistakes: List<String> = emptyList(),
    @SerializedName(value = "solutionMethods", alternate = ["solution_methods", "recommendedMethods", "recommended_methods"])
    val solutionMethods: List<String> = emptyList(),
    @SerializedName(value = "notices", alternate = ["cautions"])
    val notices: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
