package com.wrongbook.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wrongbook.app.model.ImageRef

@Composable
fun EditableImageSection(
    title: String,
    images: List<ImageRef>,
    emptyText: String,
    addButtonText: String,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    takePhotoText: String? = null,
    onTakePhoto: (() -> Unit)? = null,
    isBusy: Boolean = false,
    busyText: String? = null,
    wrapInCard: Boolean = true
) {
    if (wrapInCard) {
        SectionCard(title) {
            EditableImageSectionContent(
                images = images,
                emptyText = emptyText,
                addButtonText = addButtonText,
                onAdd = onAdd,
                onRemove = onRemove,
                takePhotoText = takePhotoText,
                onTakePhoto = onTakePhoto,
                isBusy = isBusy,
                busyText = busyText
            )
        }
    } else {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        EditableImageSectionContent(
            images = images,
            emptyText = emptyText,
            addButtonText = addButtonText,
            onAdd = onAdd,
            onRemove = onRemove,
            takePhotoText = takePhotoText,
            onTakePhoto = onTakePhoto,
            isBusy = isBusy,
            busyText = busyText
        )
    }
}

@Composable
private fun EditableImageSectionContent(
    images: List<ImageRef>,
    emptyText: String,
    addButtonText: String,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    takePhotoText: String? = null,
    onTakePhoto: (() -> Unit)? = null,
    isBusy: Boolean = false,
    busyText: String? = null
) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onTakePhoto != null && takePhotoText != null) {
                FilledTonalButton(
                    onClick = onTakePhoto,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(takePhotoText)
                }
                OutlinedButton(
                    onClick = onAdd,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(addButtonText)
                }
            } else {
                FilledTonalButton(
                    onClick = onAdd,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(addButtonText)
                }
            }
        }
        if (isBusy && busyText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = busyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (images.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ImageRow(
                images = images,
                removable = true,
                onRemove = onRemove
            )
        }
}

@Composable
fun ReadOnlyImageSection(
    title: String,
    images: List<ImageRef>
) {
    if (images.isEmpty()) return

    SectionCard(title) {
        ImageRow(
            images = images,
            removable = false,
            onRemove = {}
        )
    }
}

@Composable
private fun ImageRow(
    images: List<ImageRef>,
    removable: Boolean,
    onRemove: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(end = 4.dp)
    ) {
        items(images, key = { it.id }) { image ->
            var showPreview by remember { mutableStateOf(false) }
            if (showPreview) {
                AlertDialog(
                    onDismissRequest = { showPreview = false },
                    confirmButton = {
                        TextButton(onClick = { showPreview = false }) {
                            Text("关闭")
                        }
                    },
                    text = {
                        AsyncImage(
                            model = image.displayUri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillParentMaxWidth(0.62f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !removable) { showPreview = true }
                ) {
                    AsyncImage(
                        model = image.displayUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.3f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    if (removable) {
                        IconButton(
                            onClick = { onRemove(image.id) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "删除图片",
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
