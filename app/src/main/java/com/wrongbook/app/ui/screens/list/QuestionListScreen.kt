package com.wrongbook.app.ui.screens.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.wrongbook.app.ui.components.EmptyState
import com.wrongbook.app.ui.components.LoadingOverlay
import com.wrongbook.app.ui.components.QuestionListItem
import com.wrongbook.app.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionListScreen(
    navController: NavController,
    initialCategory: String? = null,
    viewModel: QuestionListViewModel = viewModel(
        factory = QuestionListViewModel.Factory(initialCategory)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全部错题") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 搜索框
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("搜索错题...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 分类筛选
            if (uiState.categories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedCategory == null,
                            onClick = { viewModel.onCategorySelected(null) },
                            label = { Text("全部") }
                        )
                    }
                    items(uiState.categories) { category ->
                        FilterChip(
                            selected = uiState.selectedCategory == category,
                            onClick = { viewModel.onCategorySelected(category) },
                            label = { Text(category) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 排序
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = uiState.sortOrder == SortOrder.UPDATED_DESC,
                    onClick = { viewModel.onSortOrderChange(SortOrder.UPDATED_DESC) },
                    label = { Text("最近更新") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = uiState.sortOrder == SortOrder.CREATED_DESC,
                    onClick = { viewModel.onSortOrderChange(SortOrder.CREATED_DESC) },
                    label = { Text("最近创建") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = uiState.sortOrder == SortOrder.DIFFICULTY_DESC,
                    onClick = { viewModel.onSortOrderChange(SortOrder.DIFFICULTY_DESC) },
                    label = { Text("难度优先") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = uiState.sortOrder == SortOrder.MASTERY_ASC,
                    onClick = { viewModel.onSortOrderChange(SortOrder.MASTERY_ASC) },
                    label = { Text("薄弱优先") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NotebookStatCard(
                    value = uiState.questions.count { it.analysis != null }.toString(),
                    label = "已分析",
                    modifier = Modifier.weight(1f)
                )
                NotebookStatCard(
                    value = uiState.questions.count { it.reviewCount > 0 }.toString(),
                    label = "已复习",
                    modifier = Modifier.weight(1f)
                )
                NotebookStatCard(
                    value = uiState.questions.count { !it.notes.isNullOrBlank() || it.noteImageRefs.isNotEmpty() }.toString(),
                    label = "有笔记",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 列表
            if (uiState.isLoading) {
                LoadingOverlay()
            } else if (uiState.questions.isEmpty()) {
                EmptyState(message = "没有找到错题")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.questions, key = { it.id }) { question ->
                        QuestionListItem(
                            question = question,
                            onClick = {
                                navController.navigate(
                                    Screen.QuestionDetail.createRoute(question.id)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
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
