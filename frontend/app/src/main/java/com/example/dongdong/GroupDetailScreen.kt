package com.example.dongdong

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog

// 테마 컬러
val TealPoint = Color(0xFF00BFA5)
val OrangePoint = Color(0xFFFF7043)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupDetailScreen(
    groupId: String?,
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val group by viewModel.selectedGroupDetail.collectAsState()
    val isLeader by viewModel.isLeader.collectAsState()
    val isMember by viewModel.isMember.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    var selectedMember by remember { mutableStateOf<Member?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddScheduleDialog by remember { mutableStateOf(false) }

    // 🚀 확인 다이얼로그 상태 추가
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var showKickConfirmDialog by remember { mutableStateOf<Member?>(null) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(groupId) {
        groupId?.let { viewModel.fetchGroupDetail(context, it.toInt()) }
    }

    Scaffold(
        bottomBar = {
            group?.let { currentGroup ->
                BottomActionBar(
                    isOwner = isLeader,
                    isMember = isMember,
                    enabled = !isProcessing,
                    onManage = { if (isLeader) showDeleteDialog = true },
                    onChat = { /* 채팅 이동 */ },
                    onJoin = {
                        groupId?.let { id ->
                            viewModel.joinGroup(context, id.toInt())
                        }
                    },
                    onLeave = {
                        // 🚀 바로 탈퇴하지 않고 확인 창을 띄움
                        showLeaveConfirmDialog = true
                    }
                )
            }
        }
    ) { padding ->
        if (group == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OrangePoint)
            }
        } else {
            val currentGroup = group!!
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF9F9F9))
            ) {
                // 1. 이미지
                item {
                    Box(modifier = Modifier.height(260.dp).fillMaxWidth()) {
                        AsyncImage(model = currentGroup.groupImage, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        IconButton(onClick = onBack, modifier = Modifier.padding(16.dp).background(Color.White.copy(alpha = 0.9f), CircleShape)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                }

                // 2. 제목 및 인원수 UI
                item {
                    Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(24.dp)) {
                        Text(currentGroup.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = currentGroup.description ?: "",
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Text(currentGroup.location, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                            Spacer(modifier = Modifier.width(16.dp))

                            Surface(color = TealPoint, shape = RoundedCornerShape(16.dp)) {
                                Text(
                                    text = "멤버 ${currentGroup.members?.size ?: 0}명",
                                    fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        FlowRow(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            currentGroup.tags.forEach { tag ->
                                Surface(color = TealPoint.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                    Text("#$tag", color = TealPoint, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }

                // 3. 멤버 리스트
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).background(Color.White).padding(24.dp)) {
                        Text("Group Members", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Row(modifier = Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            currentGroup.members?.forEach { member ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { selectedMember = member }) {
                                    Box {
                                        AsyncImage(model = member.avatar, contentDescription = null, modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.LightGray))
                                        if (member.id == currentGroup.leaderId) {
                                            Surface(modifier = Modifier.size(22.dp).align(Alignment.TopEnd), color = OrangePoint, shape = CircleShape) {
                                                Text("👑", fontSize = 10.sp, modifier = Modifier.wrapContentSize())
                                            }
                                        }
                                    }
                                    Text(member.nickname, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }

                // 4. 일정 영역
                val schedules = currentGroup.schedules ?: emptyList()
                if (schedules.isEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📅", fontSize = 40.sp)
                            Text("아직 등록된 일정이 없습니다.", color = Color.Gray)
                            if (isLeader) TextButton(onClick = { showAddScheduleDialog = true }) { Text("새 일정 추가", color = TealPoint) }
                        }
                    }
                } else {
                    item { Text("Schedules", fontSize = 18.sp, modifier = Modifier.padding(start = 24.dp, top = 24.dp)) }
                    items(schedules) { schedule -> ScheduleCard(schedule = schedule, isOwner = isLeader) }
                }
            }
        }
    }

    // 🚀 멤버 프로필 팝업
    selectedMember?.let { member ->
        MemberProfileDialog(
            member = member,
            isOwner = isLeader,
            isGroupOwner = (member.id == group?.leaderId),
            onDismiss = { selectedMember = null },
            onRemove = {
                // 🚀 바로 강퇴하지 않고 확인 창을 띄움
                showKickConfirmDialog = member
                selectedMember = null
            }
        )
    }

    // 🚀 탈퇴 확인 다이얼로그
    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            title = { Text("모임 탈퇴") },
            text = { Text("정말로 이 모임에서 탈퇴하시겠습니까?\n탈퇴 후에는 다시 가입 신청을 해야 합니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        groupId?.let { id -> viewModel.leaveGroup(context, id.toInt()) }
                        showLeaveConfirmDialog = false
                    }
                ) { Text("탈퇴하기", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) { Text("취소") }
            }
        )
    }

    // 🚀 강퇴 확인 다이얼로그
    showKickConfirmDialog?.let { member ->
        AlertDialog(
            onDismissRequest = { showKickConfirmDialog = null },
            title = { Text("멤버 내보내기") },
            text = { Text("'${member.nickname}' 멤버를 정말로 이 모임에서 내보내시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        groupId?.let { id -> viewModel.kickMember(context, id.toInt(), member.id) }
                        showKickConfirmDialog = null
                    }
                ) { Text("내보내기", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showKickConfirmDialog = null }) { Text("취소") }
            }
        )
    }

    if (showDeleteDialog) {
        GroupDeleteDialog(groupName = group?.title ?: "", onDismiss = { showDeleteDialog = false }, onConfirm = { onBack() })
    }

    if (showAddScheduleDialog) {
        AddScheduleDialog(onDismiss = { showAddScheduleDialog = false }, onAdd = { _, _, _ -> })
    }
}

@Composable
fun BottomActionBar(
    isOwner: Boolean,
    isMember: Boolean,
    enabled: Boolean,
    onManage: () -> Unit,
    onChat: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp, color = Color.White) {
        Row(modifier = Modifier.padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isMember && !isOwner) {
                OutlinedButton(
                    onClick = onLeave,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                ) {
                    Text("탈퇴하기", fontWeight = FontWeight.Bold)
                }
            }

            val (buttonText, buttonColor, onClick) = when {
                isOwner -> Triple("모임 관리하기", OrangePoint, onManage)
                isMember -> Triple("채팅방으로 이동", TealPoint, onChat)
                else -> Triple("모임 가입하기", TealPoint, onJoin)
            }

            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.weight(if (isMember && !isOwner) 2f else 1f),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(buttonText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun ScheduleCard(schedule: Schedule, isOwner: Boolean) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(schedule.title, fontWeight = FontWeight.Bold)
                Text("${schedule.date} | ${schedule.location}", color = Color.Gray, fontSize = 13.sp)
            }
            if (isOwner) Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun GroupDeleteDialog(groupName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("삭제 확인") }, text = { Column { Text("'$groupName 삭제'를 입력하세요."); OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) } }, confirmButton = { Button(onClick = onConfirm, enabled = text == "$groupName 삭제", colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("삭제") } })
}

@Composable
fun AddScheduleDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("일정 추가") }, text = { Text("일정 입력 폼...") }, confirmButton = { Button(onClick = { onAdd("", "", "") }) { Text("등록") } })
}

@Composable
fun MemberProfileDialog(
    member: Member,
    isOwner: Boolean,
    isGroupOwner: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.6f),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AsyncImage(
                            model = member.avatar,
                            contentDescription = null,
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray)
                        )
                        if (isGroupOwner) {
                            Surface(color = OrangePoint, shape = CircleShape, modifier = Modifier.size(28.dp)) {
                                Text("👑", fontSize = 14.sp, modifier = Modifier.wrapContentSize())
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(member.nickname, fontSize = 22.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
                    ) {
                        Text(
                            text = member.bio?.takeIf { it.isNotEmpty() } ?: "등록된 자기소개가 없습니다.",
                            modifier = Modifier.padding(16.dp),
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isOwner && !isGroupOwner) {
                        Button(
                            onClick = onRemove,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("강퇴하기", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("닫기")
                    }
                }
            }
        }
    }
}
