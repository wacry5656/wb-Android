package com.wrongbook.app.ui.screens.addedit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.wrongbook.app.model.ImageRef
import com.wrongbook.app.ui.components.EditableImageSection
import com.wrongbook.app.ui.components.LoadingOverlay
import com.wrongbook.app.utils.ImageFileStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    navController: NavController,
    questionId: String? = null,
    viewModel: AddEditViewModel = viewModel(factory = AddEditViewModel.Factory(questionId))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingMainCameraImage by remember { mutableStateOf<ImageRef?>(null) }
    var pendingNoteCameraImage by remember { mutableStateOf<ImageRef?>(null) }

    val mainImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                importAndAddImage(
                    uri = it,
                    context = context,
                    kind = "question",
                    onAdd = viewModel::addImageRef,
                    onError = viewModel::showError
                )
            }
        }
    }

    val mainCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val imageRef = pendingMainCameraImage
        pendingMainCameraImage = null
        if (success && imageRef != null) {
            scope.launch {
                try {
                    viewModel.addImageRef(ImageFileStore.ensureImageIsSupported(context, imageRef))
                } catch (e: Exception) {
                    viewModel.showError("图片导入失败: ${e.message}")
                }
            }
        } else {
            imageRef?.uri?.let { uri ->
                val file = com.wrongbook.app.utils.ImageFileStore.getFileFromUri(context, uri)
                file?.delete()
            }
            if (!success) {
                viewModel.showError("拍照已取消或保存失败")
            }
        }
    }

    val noteImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                importAndAddImage(
                    uri = it,
                    context = context,
                    kind = "note",
                    onAdd = viewModel::addNoteImageRef,
                    onError = viewModel::showError
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
            scope.launch {
                try {
                    viewModel.addNoteImageRef(ImageFileStore.ensureImageIsSupported(context, imageRef))
                } catch (e: Exception) {
                    viewModel.showError("图片笔记导入失败: ${e.message}")
                }
            }
        } else {
            imageRef?.uri?.let { uri ->
                val file = com.wrongbook.app.utils.ImageFileStore.getFileFromUri(context, uri)
                file?.delete()
            }
            if (!success) {
                viewModel.showError("拍照已取消或保存失败")
            }
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            navController.popBackStack()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEdit) "编辑错题" else "新增错题") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = !uiState.isSaving && !uiState.isLoading && !uiState.isUnavailable
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingOverlay()
            uiState.isUnavailable -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = uiState.unavailableMessage ?: "题目不存在或已删除",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("返回")
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 标题
                    item {
                        OutlinedTextField(
                            value = uiState.title,
                            onValueChange = { viewModel.onTitleChange(it) },
                            label = { Text("标题 *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = uiState.errorMessage != null && uiState.title.isBlank(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    // 学科
                    item {
                        var categoryExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded && uiState.categories.isNotEmpty(),
                            onExpandedChange = { categoryExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = uiState.category,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("学科") },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                                }
                            )
                            if (uiState.categories.isNotEmpty()) {
                                DropdownMenu(
                                    expanded = categoryExpanded,
                                    onDismissRequest = { categoryExpanded = false }
                                ) {
                                    uiState.categories.forEach { category ->
                                        DropdownMenuItem(
                                            text = { Text(category) },
                                            onClick = {
                                                viewModel.onCategoryChange(category)
                                                categoryExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 年级
                    item {
                        OutlinedTextField(
                            value = uiState.grade,
                            onValueChange = { viewModel.onGradeChange(it) },
                            label = { Text("年级") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("例如：高一、高二") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    // 题型
                    item {
                        OutlinedTextField(
                            value = uiState.questionType,
                            onValueChange = { viewModel.onQuestionTypeChange(it) },
                            label = { Text("题型") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("例如：选择题、填空题、解答题") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    // 来源
                    item {
                        OutlinedTextField(
                            value = uiState.source,
                            onValueChange = { viewModel.onSourceChange(it) },
                            label = { Text("来源") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("例如：期中考试、模拟卷、练习册") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    // 题目内容
                    item {
                        OutlinedTextField(
                            value = uiState.questionText,
                            onValueChange = { viewModel.onQuestionTextChange(it) },
                            label = { Text("题目内容（可选）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            placeholder = { Text("可以手动输入题干文字，方便搜索和 AI 分析") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    // 我的答案
                    item {
                        OutlinedTextField(
                            value = uiState.userAnswer,
                            onValueChange = { viewModel.onUserAnswerChange(it) },
                            label = { Text("我的答案（可选）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            placeholder = { Text("记录你当时写下的答案") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    // 正确答案
                    item {
                        OutlinedTextField(
                            value = uiState.correctAnswer,
                            onValueChange = { viewModel.onCorrectAnswerChange(it) },
                            label = { Text("正确答案（可选）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            placeholder = { Text("记录标准答案或参考答案") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    // 错误原因
                    item {
                        OutlinedTextField(
                            value = uiState.errorCause,
                            onValueChange = { viewModel.onErrorCauseChange(it) },
                            label = { Text("错误原因（可选）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            placeholder = { Text("分析做错的原因，方便复习时重点回顾") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    // 标签
                    item {
                        OutlinedTextField(
                            value = uiState.tagsText,
                            onValueChange = { viewModel.onTagsTextChange(it) },
                            label = { Text("标签（可选）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("用逗号、顿号或换行分隔多个标签") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    // 笔记
                    item {
                        OutlinedTextField(
                            value = uiState.notes,
                            onValueChange = { viewModel.onNotesChange(it) },
                            label = { Text("笔记（可选）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            placeholder = { Text("记录解题思路、注意事项或老师讲解要点") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                    }

                    // 题目图片
                    item {
                        EditableImageSection(
                            title = "题目图片",
                            images = uiState.imageRefs,
                            emptyText = "还没有题目图片",
                            addButtonText = "选择图片",
                            onAdd = { mainImageLauncher.launch(arrayOf("image/*")) },
                            onRemove = viewModel::removeImageRef,
                            takePhotoText = "拍照",
                            onTakePhoto = {
                                val imageRef = ImageFileStore.createCameraImageRef(context, kind = "question")
                                pendingMainCameraImage = imageRef
                                mainCameraLauncher.launch(requireNotNull(imageRef.uri).toUri())
                            },
                            isBusy = false,
                            busyText = "正在导入题目图片..."
                        )
                    }

                    // 图片笔记
                    item {
                        EditableImageSection(
                            title = "图片笔记",
                            images = uiState.noteImageRefs,
                            emptyText = "还没有图片笔记",
                            addButtonText = "选择图片",
                            onAdd = { noteImageLauncher.launch(arrayOf("image/*")) },
                            onRemove = viewModel::removeNoteImageRef,
                            takePhotoText = "拍照",
                            onTakePhoto = {
                                val imageRef = ImageFileStore.createCameraImageRef(context, kind = "note")
                                pendingNoteCameraImage = imageRef
                                noteCameraLauncher.launch(requireNotNull(imageRef.uri).toUri())
                            },
                            isBusy = false,
                            busyText = "正在导入图片笔记..."
                        )
                    }

                    item {
                        Text(
                            text = if (uiState.imageRefs.isEmpty()) "建议添加题目图片，AI 分析将根据图片和文字内容生成更准确的分析。" else "AI 将根据题目图片和补充信息生成分析。题目文字可选填写，用于提高分析准确度。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

suspend fun importAndAddImage(
    uri: Uri,
    context: android.content.Context,
    kind: String,
    onAdd: (ImageRef) -> Unit,
    onError: (String) -> Unit
) {
    try {
        onAdd(ImageFileStore.importImage(context, uri, kind = kind))
    } catch (e: Exception) {
        onError("图片导入失败: ${e.message}")
    }
}
