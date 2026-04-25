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

    val mainImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                importAndAddImage(
                    uri = it,
                    context = context,
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
            viewModel.addImageRef(imageRef)
        } else {
            viewModel.showError("拍照已取消或保存失败")
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
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
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
                    item {
                        OutlinedTextField(
                            value = uiState.title,
                            onValueChange = { viewModel.onTitleChange(it) },
                            label = { Text("标题 *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = uiState.errorMessage != null && uiState.title.isBlank()
                        )
                    }

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

                    item {
                        Text(
                            text = "AI 将根据题目图片和补充信息生成分析。题目文字可选填写，用于提高分析准确度。",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
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
    onAdd: (ImageRef) -> Unit,
    onError: (String) -> Unit
) {
    try {
        onAdd(ImageFileStore.importImage(context, uri, kind = "question"))
    } catch (e: Exception) {
        onError("图片导入失败: ${e.message}")
    }
}
