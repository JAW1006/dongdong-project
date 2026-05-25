package com.example.dongdong

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditScreen(
    groupId: Int,
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val group by viewModel.selectedGroupDetail.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    LaunchedEffect(groupId) {
        viewModel.fetchGroupDetail(context, groupId)
    }

    var title by remember(group?.id) { mutableStateOf(group?.title ?: "") }
    var description by remember(group?.id) { mutableStateOf(group?.description ?: "") }
    var location by remember(group?.id) { mutableStateOf(group?.location ?: "") }
    var tagsInput by remember(group?.id) { mutableStateOf("") }
    var tagsList by remember(group?.id) { mutableStateOf(group?.tags ?: emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("모임 정보 수정", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp, color = Color.White) {
                Button(
                    onClick = {
                        val finalTags = if (tagsList.isEmpty() && tagsInput.isNotBlank()) {
                            tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        } else tagsList
                        viewModel.updateGroup(
                            context = context,
                            groupId = groupId,
                            payload = GroupUpdateRequest(
                                title = title.trim().ifBlank { null },
                                description = description.trim().ifBlank { null },
                                location = location.trim().ifBlank { null },
                                tags = finalTags
                            ),
                            onSuccess = { onBack() }
                        )
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7043)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("저장", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        },
        containerColor = Color(0xFFF9F9F9)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Section("모임 이름") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Section("활동 지역") {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Section("모임 설명") {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
            }
            Section("태그 (콤마로 추가/삭제는 입력창 비우고 새로 입력)") {
                Column {
                    if (tagsList.isNotEmpty()) {
                        Text(
                            tagsList.joinToString(", ") { "#$it" },
                            color = Color(0xFF00BFA5),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    OutlinedTextField(
                        value = tagsInput,
                        onValueChange = { input ->
                            if (input.endsWith(",") || input.endsWith(" ")) {
                                val newTag = input.dropLast(1).trim()
                                if (newTag.isNotEmpty() && newTag !in tagsList) {
                                    tagsList = tagsList + newTag
                                }
                                tagsInput = ""
                            } else {
                                tagsInput = input
                            }
                        },
                        placeholder = { Text("새 태그 입력 후 쉼표") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (tagsList.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { tagsList = emptyList() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("태그 모두 비우기", fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        content()
    }
}
