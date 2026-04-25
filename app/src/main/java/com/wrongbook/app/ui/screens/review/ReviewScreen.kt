package com.wrongbook.app.ui.screens.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.wrongbook.app.ui.components.LoadingOverlay
import com.wrongbook.app.ui.components.ReadOnlyImageSection
import com.wrongbook.app.ui.components.ReviewStatusBadge
import com.wrongbook.app.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    navController: NavController,
    viewModel: ReviewViewModel = viewModel(factory = ReviewViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (!uiState.isCompleted && uiState.totalCount > 0) {
                        Text("复习 ${uiState.completedCount + 1}/${uiState.totalCount}")
                    } else {
                        Text("今日复习")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                LoadingOverlay()
            }

            uiState.isCompleted -> {
                // 完成状态
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (uiState.totalCount == 0) "今天没有需要复习的题目"
                        else "太棒了！今日复习全部完成",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    if (uiState.totalCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "共复习了 ${uiState.totalCount} 道题",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { navController.popBackStack() }) {
                        Text("返回首页")
                    }
                }
            }

            else -> {
                // 复习中
                val question = uiState.reviewQuestions[uiState.currentIndex]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // 进度条
                    LinearProgressIndicator(
                        progress = if (uiState.totalCount > 0) uiState.completedCount.toFloat() / uiState.totalCount else 0f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReviewSortOrder.entries.forEach { order ->
                            FilterChip(
                                selected = uiState.sortOrder == order,
                                onClick = { viewModel.onSortOrderChange(order) },
                                label = { Text(order.label) }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 题目卡片
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = question.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ReviewStatusBadge(question.reviewStatus)
                                }

                                if (question.category.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = question.category,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!question.questionText.isNullOrBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = question.questionText,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }

                                if (question.reviewCount > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "已复习 ${question.reviewCount} 次",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "掌握度 ${question.masteryLevel}/5",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        ReadOnlyImageSection(title = "题目图片", images = question.imageRefs)

                        // 显示/隐藏答案
                        if (!uiState.showAnswer) {
                            FilledTonalButton(
                                onClick = { viewModel.toggleShowAnswer() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("显示答案")
                            }
                        } else {
                            // 我的答案
                            if (!question.userAnswer.isNullOrBlank()) {
                                SectionCard("我的答案") {
                                    Text(
                                        question.userAnswer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // 正确答案
                            if (!question.correctAnswer.isNullOrBlank()) {
                                SectionCard("正确答案") {
                                    Text(
                                        question.correctAnswer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // 备注
                            if (!question.notes.isNullOrBlank()) {
                                SectionCard("备注") {
                                    Text(
                                        question.notes,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            // AI 分析摘要
                            if (question.analysis != null) {
                                SectionCard("AI 分析") {
                                    Text(
                                        buildString {
                                            append("难度：${question.analysis.difficulty}")
                                            if (question.analysis.knowledgePoints.isNotEmpty()) {
                                                append("\n知识点：")
                                                append(question.analysis.knowledgePoints.joinToString("、"))
                                            }
                                            if (question.analysis.commonMistakes.isNotEmpty()) {
                                                append("\n易错点：")
                                                append(question.analysis.commonMistakes.take(2).joinToString("、"))
                                            }
                                            if (question.analysis.solutionMethods.isNotEmpty()) {
                                                append("\n推荐方法：")
                                                append(question.analysis.solutionMethods.joinToString("、"))
                                            }
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    // 底部操作栏
                    if (uiState.showAnswer) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "这次掌握情况",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.completeReview(0) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("不会")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.completeReview(1) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("模糊")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.completeReview(2) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("会")
                                }
                                Button(
                                    onClick = { viewModel.completeReview(3) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("熟练")
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.postponeReview() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("稍后再看")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.previousQuestion() },
                                    enabled = uiState.currentIndex > 0,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("上一题")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.nextQuestion() },
                                    enabled = uiState.currentIndex < uiState.reviewQuestions.lastIndex,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("下一题")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
