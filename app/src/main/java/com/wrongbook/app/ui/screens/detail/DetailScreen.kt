package com.wrongbook.app.ui.screens.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.wrongbook.app.model.FollowUpChat
import com.wrongbook.app.model.ImageRef
import com.wrongbook.app.model.Question
import com.wrongbook.app.model.QuestionAnalysis
import com.wrongbook.app.ui.components.EditableImageSection
import com.wrongbook.app.ui.components.LoadingOverlay
import com.wrongbook.app.ui.components.ReadOnlyImageSection
import com.wrongbook.app.ui.components.SectionCard
import com.wrongbook.app.ui.navigation.Screen
import com.wrongbook.app.utils.ImageFileStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    navController: NavController,
    questionId: String,
    viewModel: DetailViewModel = viewModel(factory = DetailViewModel.Factory(questionId))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingNoteCameraImage by remember { mutableStateOf<ImageRef?>(null) }

    val noteImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                importAndAddNoteImage(
                    uri = it,
                    context = context,
                    onAdd = viewModel::addNoteImageRef,
                    onError = viewModel::showMessage
                )
            }
        }
    }

    val noteCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val imageRef = pendingNoteCameraImage
        pendingNoteCameraImage = null
        if (success && imageRef != null) {
            viewModel.addNoteImageRef(imageRef)
        } else {
            viewModel.showMessage("拍照已取消或保存失败")
        }
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) navController.popBackStack()
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这道错题吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.softDelete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    val activeQuestion = uiState.question

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        activeQuestion?.title ?: uiState.unavailableMessage ?: "详情",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (activeQuestion != null) {
                        IconButton(onClick = {
                            navController.navigate(Screen.EditQuestion.createRoute(questionId))
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingOverlay()
            activeQuestion == null -> UnavailableQuestionContent(
                message = uiState.unavailableMessage ?: "题目不存在",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onBack = { navController.popBackStack() }
            )

            else -> DetailContent(
                question = activeQuestion,
                uiState = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onMarkReviewed = viewModel::markReviewed,
                onNotesChange = viewModel::onNotesInputChange,
                onSaveNotes = viewModel::saveNotes,
                onAddNoteImage = { noteImageLauncher.launch(arrayOf("image/*")) },
                onTakeNotePhoto = {
                    val imageRef = ImageFileStore.createCameraImageRef(context)
                    pendingNoteCameraImage = imageRef
                    noteCameraLauncher.launch(imageRef.uri.toUri())
                },
                onRemoveNoteImage = viewModel::removeNoteImageRef,
                onAnalyze = viewModel::analyze,
                onGenerateExplanation = viewModel::generateExplanation,
                onGenerateHint = viewModel::generateHint,
                onFollowUpInputChange = viewModel::onFollowUpInputChange,
                onSendFollowUp = viewModel::sendFollowUp
            )
        }
    }
}

@Composable
private fun DetailContent(
    question: Question,
    uiState: DetailUiState,
    modifier: Modifier = Modifier,
    onMarkReviewed: () -> Unit,
    onNotesChange: (String) -> Unit,
    onSaveNotes: () -> Unit,
    onAddNoteImage: () -> Unit,
    onTakeNotePhoto: () -> Unit,
    onRemoveNoteImage: (String) -> Unit,
    onAnalyze: () -> Unit,
    onGenerateExplanation: () -> Unit,
    onGenerateHint: () -> Unit,
    onFollowUpInputChange: (String) -> Unit,
    onSendFollowUp: () -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        if (question.hasAnyStaleAiContent) {
            item {
                SectionCard("AI 状态") {
                    Text(
                        "题目内容已经更新，下面的 AI 结果可能过期。旧结果仍可参考，但建议重新生成。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (!question.questionText.isNullOrBlank()) {
            item {
                SectionCard("题目内容") {
                    Text(question.questionText, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (!question.userAnswer.isNullOrBlank() || !question.correctAnswer.isNullOrBlank()) {
            item {
                SectionCard("答案对比") {
                    if (!question.userAnswer.isNullOrBlank()) {
                        Text(
                            "我的答案",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(question.userAnswer, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (!question.correctAnswer.isNullOrBlank()) {
                        Text(
                            "正确答案",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(question.correctAnswer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (question.errorCause.isNotEmpty()) {
            item {
                SectionCard("错误原因") {
                    Text(question.errorCause, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            ReadOnlyImageSection(title = "题目图片", images = question.imageRefs)
        }

        item {
            NotesSection(
                notes = uiState.notesInput,
                isSaving = uiState.isSavingNotes,
                noteImages = question.noteImageRefs,
                isSavingImages = uiState.isSavingNoteImages,
                onNotesChange = onNotesChange,
                onSave = onSaveNotes,
                onAddImage = onAddNoteImage,
                onTakePhoto = onTakeNotePhoto,
                onRemoveImage = onRemoveNoteImage
            )
        }

        item {
            Button(
                onClick = onMarkReviewed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("标记已复习")
            }
        }

        item {
            AiAnalysisSection(
                analysis = question.analysis,
                isStale = question.isAnalysisStale,
                isLoading = uiState.isAnalyzing,
                onGenerate = onAnalyze
            )
        }

        item {
            AiTextSection(
                title = "AI 详解",
                content = question.detailedExplanation,
                isStale = question.isDetailedExplanationStale,
                isLoading = uiState.isGeneratingExplanation,
                onGenerate = onGenerateExplanation
            )
        }

        item {
            AiTextSection(
                title = "思路指引",
                content = question.hint,
                isStale = question.isHintStale,
                isLoading = uiState.isGeneratingHint,
                onGenerate = onGenerateHint
            )
        }

        item {
            FollowUpChatSection(
                chats = question.followUpChats,
                isStale = question.isFollowUpStale,
                input = uiState.followUpInput,
                isLoading = uiState.isSendingFollowUp,
                onInputChange = onFollowUpInputChange,
                onSend = onSendFollowUp
            )
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun UnavailableQuestionContent(
    message: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回")
        }
    }
}

@Composable
private fun NotesSection(
    notes: String,
    isSaving: Boolean,
    noteImages: List<ImageRef>,
    isSavingImages: Boolean,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onAddImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveImage: (String) -> Unit
) {
    SectionCard("笔记") {
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("在这里补充自己的笔记") },
            minLines = 3,
            maxLines = 8
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onSave,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text("保存笔记")
        }
        Spacer(Modifier.height(12.dp))
        EditableImageSection(
            title = "图片笔记",
            images = noteImages,
            emptyText = "还没有图片笔记",
            addButtonText = "选择图片",
            onAdd = onAddImage,
            onRemove = onRemoveImage,
            takePhotoText = "拍照",
            onTakePhoto = onTakePhoto,
            isBusy = isSavingImages,
            busyText = "正在保存图片笔记...",
            wrapInCard = false
        )
    }
}

@Composable
private fun AiAnalysisSection(
    analysis: QuestionAnalysis?,
    isStale: Boolean,
    isLoading: Boolean,
    onGenerate: () -> Unit
) {
    SectionCard("AI 分析") {
        if (isStale) {
            StaleNotice()
            Spacer(Modifier.height(8.dp))
        }
        if (isLoading) {
            LoadingText("正在分析...")
        } else if (analysis != null) {
            Text("难度：${analysis.difficulty}（${analysis.difficultyScore}/5）")
            if (
                analysis.knowledgePoints.isEmpty() &&
                analysis.commonMistakes.isEmpty() &&
                analysis.solutionMethods.isEmpty() &&
                analysis.notices.isEmpty()
            ) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "AI 分析已生成，但没有返回可展示的条目。请重新分析一次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (analysis.knowledgePoints.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("知识点：", style = MaterialTheme.typography.labelLarge)
                analysis.knowledgePoints.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (analysis.commonMistakes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("常见错误：", style = MaterialTheme.typography.labelLarge)
                analysis.commonMistakes.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (analysis.solutionMethods.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("推荐方法：", style = MaterialTheme.typography.labelLarge)
                analysis.solutionMethods.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (analysis.notices.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("注意事项：", style = MaterialTheme.typography.labelLarge)
                analysis.notices.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onGenerate) {
                Text("重新分析")
            }
        } else {
            OutlinedButton(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                Text("生成 AI 分析")
            }
        }
    }
}

@Composable
private fun AiTextSection(
    title: String,
    content: String?,
    isStale: Boolean,
    isLoading: Boolean,
    onGenerate: () -> Unit
) {
    SectionCard(title) {
        if (isStale) {
            StaleNotice()
            Spacer(Modifier.height(8.dp))
        }
        if (isLoading) {
            LoadingText("正在生成...")
        } else if (!content.isNullOrBlank()) {
            Text(content, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onGenerate) {
                Text("重新生成")
            }
        } else {
            OutlinedButton(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                Text("生成$title")
            }
        }
    }
}

@Composable
private fun FollowUpChatSection(
    chats: List<FollowUpChat>,
    isStale: Boolean,
    input: String,
    isLoading: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    SectionCard("AI 追问") {
        if (isStale) {
            StaleNotice(text = "当前追问记录基于旧题目内容，继续追问前建议先重新确认题目内容。")
            Spacer(Modifier.height(8.dp))
        }
        if (chats.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chats.forEach { chat ->
                    val isUser = chat.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = chat.content,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入你的问题...") },
                maxLines = 3
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = input.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Send, contentDescription = "发送")
                }
            }
        }
    }
}

@Composable
private fun LoadingText(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StaleNotice(text: String = "题目内容已变更，这份 AI 结果可能过期。") {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

suspend fun importAndAddNoteImage(
    uri: Uri,
    context: android.content.Context,
    onAdd: (ImageRef) -> Unit,
    onError: (String) -> Unit
) {
    try {
        onAdd(ImageFileStore.importImage(context, uri))
    } catch (e: Exception) {
        onError("图片笔记导入失败: ${e.message}")
    }
}
