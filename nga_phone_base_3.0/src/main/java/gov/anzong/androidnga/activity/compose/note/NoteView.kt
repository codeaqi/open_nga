package gov.anzong.androidnga.activity.compose.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gov.anzong.androidnga.activity.compose.note.data.NoteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ColorHint = Color(0xFF9E9E9E)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/**
 * 「我的思考」：一条条记录随手写下的文字。
 * 点击条目编辑，长按删除——与自选股页保持一致的交互习惯。
 */
@Composable
fun NoteView(viewModel: NoteViewModel) {
    val notes by viewModel.noteLiveData.observeAsState(emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<NoteEntity?>(null) }
    var pendingRemove by remember { mutableStateOf<NoteEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (notes.isEmpty()) {
            Text(
                text = "还没有记录\n点击右下角按钮写下第一条\n点击条目可修改，长按删除",
                textAlign = TextAlign.Center,
                color = ColorHint,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(notes, key = { it.id }) { note ->
                    NoteItem(
                        note = note,
                        onClick = { pendingEdit = note },
                        onLongClick = { pendingRemove = note }
                    )
                    HorizontalDivider()
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text(text = "+", fontSize = 24.sp)
        }
    }

    if (showAddDialog) {
        NoteEditDialog(
            title = "写下想法",
            initialText = "",
            onDismiss = { showAddDialog = false },
            onConfirm = {
                viewModel.addNote(it)
                showAddDialog = false
            }
        )
    }

    pendingEdit?.let { note ->
        NoteEditDialog(
            title = "修改",
            initialText = note.content,
            onDismiss = { pendingEdit = null },
            onConfirm = {
                viewModel.updateNote(note.id, it)
                pendingEdit = null
            }
        )
    }

    pendingRemove?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("删除这条记录") },
            text = { Text(note.content.take(40)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeNote(note.id)
                    pendingRemove = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteItem(note: NoteEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = note.content,
            fontSize = 16.sp,
            color = MaterialTheme.colors.onBackground
        )
        Text(
            text = dateFormat.format(Date(note.createTime)),
            fontSize = 12.sp,
            color = ColorHint,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun NoteEditDialog(
    title: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("内容") },
                // 想法通常是多行的，给足高度且不限制行数
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input) },
                enabled = input.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
