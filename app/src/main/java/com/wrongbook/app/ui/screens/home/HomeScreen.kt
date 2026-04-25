package com.wrongbook.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.wrongbook.app.ui.components.LoadingOverlay
import com.wrongbook.app.ui.components.QuestionListItem
import com.wrongbook.app.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("错题本") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddQuestion.route) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增错题")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingOverlay()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // 今日复习卡片
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Screen.Review.route) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "今日待复习",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${uiState.dueReviewCount} 题",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            FilledTonalButton(
                                onClick = { navController.navigate(Screen.Review.route) }
                            ) {
                                Text("开始复习")
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeStatCard(
                            label = "错题",
                            value = uiState.totalCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        HomeStatCard(
                            label = "已分析",
                            value = uiState.analyzedCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        HomeStatCard(
                            label = "已复习",
                            value = uiState.reviewedCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    FilledTonalButton(
                        onClick = viewModel::syncNow,
                        enabled = !uiState.isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isSyncing) "正在同步..." else "同步到 VPS")
                    }
                    uiState.syncMessage?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 快捷入口
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { navController.navigate("question_list") }
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.List, contentDescription = null)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("全部错题", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        OutlinedCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { navController.navigate(Screen.AddQuestion.route) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("新增错题", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                // 分类
                if (uiState.categories.isNotEmpty()) {
                    item {
                        Text(
                            "分类",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.categories) { category ->
                                FilterChip(
                                    onClick = {
                                        navController.navigate("question_list?category=$category")
                                    },
                                    label = { Text(category) },
                                    selected = false
                                )
                            }
                        }
                    }
                }

                // 最近新增
                item {
                    Text(
                        "最近新增",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (uiState.recentQuestions.isEmpty()) {
                    item {
                        Text(
                            "还没有错题，点击右下角 + 添加第一道",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(uiState.recentQuestions, key = { it.id }) { question ->
                        QuestionListItem(
                            question = question,
                            onClick = {
                                navController.navigate(Screen.QuestionDetail.createRoute(question.id))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
