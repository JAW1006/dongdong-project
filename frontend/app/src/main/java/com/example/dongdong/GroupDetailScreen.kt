package com.example.dongdong

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dongdong.ui.theme.BrandOrange
import com.example.dongdong.ui.theme.BrandTeal

// 호환용 — 외부에서 참조 가능
val TealPoint = BrandTeal
val OrangePoint = BrandOrange

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupDetailScreen(
    groupId: String?,
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToChat: ((groupId: Int, groupTitle: String) -> Unit)? = null,
    onNavigateToEdit: ((groupId: Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val group by viewModel.selectedGroupDetail.collectAsState()
    val isLeader by viewModel.isLeader.collectAsState()
    val isMember by viewModel.isMember.collectAsState()
    val hasPendingRequest by viewModel.hasPendingRequest.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    var selectedMember by remember { mutableStateOf<Member?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddScheduleDialog by remember { mutableStateOf(false) }
    var showManageMenu by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }

    val reviews by viewModel.reviews.collectAsState()
    val currentUserId = remember { com.example.dongdong.network.AuthManager.getUserId(context) }

    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var showKickConfirmDialog by remember { mutableStateOf<Member?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }

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
        groupId?.let {
            viewModel.fetchGroupDetail(context, it.toInt())
            viewModel.fetchReviews(context, it.toInt())
        }
    }

    Scaffold(
        bottomBar = {
            group?.let { currentGroup ->
                BottomActionBar(
                    isOwner = isLeader,
                    isMember = isMember,
                    hasPendingRequest = hasPendingRequest,
                    enabled = !isProcessing,
                    onManage = { if (isLeader) showManageMenu = true },
                    onChat = {
                        groupId?.let { id ->
                            onNavigateToChat?.invoke(id.toInt(), currentGroup.title)
                        }
                    },
                    onJoin = {
                        groupId?.let { id -> viewModel.applyToGroup(context, id.toInt()) }
                    },
                    onLeave = { showLeaveConfirmDialog = true }
                )
            }
        }
    ) { padding ->
        if (group == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val currentGroup = group!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // 1. 이미지
                item {
                    Box(modifier = Modifier.height(260.dp).fillMaxWidth()) {
                        AsyncImage(
                            model = currentGroup.groupImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // 뒤로가기 (좌상단)
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    CircleShape
                                )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                        // 설정 아이콘 + 드롭다운 (우상단): 방장이면 관리, 아니면 신고
                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                            IconButton(
                                onClick = { showManageMenu = true },
                                modifier = Modifier.background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    CircleShape
                                )
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "메뉴",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showManageMenu,
                                onDismissRequest = { showManageMenu = false }
                            ) {
                                if (isLeader) {
                                    DropdownMenuItem(
                                        text = { Text("정보 수정") },
                                        onClick = {
                                            showManageMenu = false
                                            groupId?.let { id -> onNavigateToEdit?.invoke(id.toInt()) }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("모임 삭제", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showManageMenu = false
                                            showDeleteDialog = true
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("신고하기", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showManageMenu = false
                                            showReportDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. 제목/위치/태그
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(24.dp)
                    ) {
                        Text(currentGroup.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = currentGroup.description ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                currentGroup.location,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))

                            Surface(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text(
                                    text = "멤버 ${currentGroup.members?.size ?: 0}명",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        FlowRow(
                            modifier = Modifier.padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            currentGroup.tags.forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        "#$tag",
                                        color = MaterialTheme.colorScheme.secondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. 멤버
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(24.dp)
                    ) {
                        Text("Group Members", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            currentGroup.members?.forEach { member ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { selectedMember = member }
                                ) {
                                    Box {
                                        AsyncImage(
                                            model = member.avatar,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                        if (member.id == currentGroup.leaderId) {
                                            Surface(
                                                modifier = Modifier.size(22.dp).align(Alignment.TopEnd),
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape
                                            ) {
                                                Text(
                                                    "👑",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.wrapContentSize()
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        member.nickname,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. 일정
                val schedules = currentGroup.schedules ?: emptyList()
                if (schedules.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📅", fontSize = 40.sp)
                            Text(
                                "아직 등록된 일정이 없습니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isLeader) TextButton(onClick = { showAddScheduleDialog = true }) {
                                Text(
                                    "새 일정 추가",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, top = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Schedules",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (isLeader) {
                                TextButton(onClick = { showAddScheduleDialog = true }) {
                                    Text(
                                        "+ 일정 추가",
                                        color = MaterialTheme.colorScheme.secondary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                    items(schedules) { schedule ->
                        ScheduleCard(
                            schedule = schedule,
                            isOwner = isLeader,
                            canAttend = isMember || isLeader,
                            onToggleAttend = {
                                groupId?.let { gid ->
                                    viewModel.toggleScheduleAttendance(context, gid.toInt(), schedule.id)
                                }
                            }
                        )
                    }
                }

                // 5. 후기
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("후기", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        if (currentGroup.reviewCount > 0) {
                            Text(
                                "⭐ ${currentGroup.averageRating} (${currentGroup.reviewCount})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        if (isMember || isLeader) {
                            TextButton(onClick = { showReviewDialog = true }) {
                                Text(
                                    "+ 후기 작성",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
                if (reviews.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                "아직 등록된 후기가 없어요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(reviews, key = { it.id }) { review ->
                        ReviewCard(
                            review = review,
                            canDelete = isLeader || review.userId == currentUserId,
                            onDelete = {
                                groupId?.let { gid ->
                                    viewModel.deleteReview(context, gid.toInt(), review.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 멤버 프로필 팝업
    selectedMember?.let { member ->
        MemberProfileDialog(
            member = member,
            isOwner = isLeader,
            isGroupOwner = (member.id == group?.leaderId),
            onDismiss = { selectedMember = null },
            onRemove = {
                showKickConfirmDialog = member
                selectedMember = null
            }
        )
    }

    // 탈퇴 확인
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
                ) {
                    Text(
                        "탈퇴하기",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmDialog = false }) { Text("취소") }
            }
        )
    }

    // 강퇴 확인
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
                ) {
                    Text(
                        "내보내기",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showKickConfirmDialog = null }) { Text("취소") }
            }
        )
    }

    if (showDeleteDialog) {
        GroupDeleteDialog(
            groupName = group?.title ?: "",
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                groupId?.let { id ->
                    viewModel.deleteGroup(context, id.toInt()) {
                        showDeleteDialog = false
                        onBack()
                    }
                }
            }
        )
    }

    if (showReviewDialog) {
        ReviewWriteDialog(
            existingMyReview = reviews.firstOrNull { it.userId == currentUserId },
            onDismiss = { showReviewDialog = false },
            onSubmit = { rating, content ->
                groupId?.let { id ->
                    viewModel.submitReview(
                        context = context,
                        groupId = id.toInt(),
                        rating = rating,
                        content = content.ifBlank { null },
                        onSuccess = { showReviewDialog = false }
                    )
                }
            }
        )
    }

    if (showAddScheduleDialog) {
        AddScheduleDialog(
            onDismiss = { showAddScheduleDialog = false },
            onAdd = { title, meetingTime, location, isDrinking, isSmoking ->
                groupId?.let { id ->
                    viewModel.createSchedule(
                        context = context,
                        groupId = id.toInt(),
                        title = title,
                        meetingTime = meetingTime,
                        location = location,
                        isDrinking = isDrinking,
                        isSmoking = isSmoking,
                        onSuccess = { showAddScheduleDialog = false }
                    )
                }
            }
        )
    }

    if (showReportDialog) {
        ReportDialog(
            title = "이 모임 신고하기",
            onDismiss = { showReportDialog = false },
            onSubmit = { reason ->
                groupId?.let { id ->
                    viewModel.submitReport(
                        context = context,
                        targetType = ReportTargetType.GROUP,
                        targetId = id.toInt(),
                        reason = reason
                    )
                }
            }
        )
    }
}

@Composable
fun BottomActionBar(
    isOwner: Boolean,
    isMember: Boolean,
    hasPendingRequest: Boolean,
    enabled: Boolean,
    onManage: () -> Unit,
    onChat: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp).navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isMember && !isOwner) {
                OutlinedButton(
                    onClick = onLeave,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Text("탈퇴하기", style = MaterialTheme.typography.labelLarge)
                }
            }

            val secondary = MaterialTheme.colorScheme.secondary
            val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
            val (buttonText, buttonColor, onClick, isBtnEnabled) = when {
                isOwner || isMember -> Quadruple("채팅방으로 이동", secondary, onChat, enabled)
                hasPendingRequest -> Quadruple("신청 대기 중", onSurfaceVariant, {}, false)
                else -> Quadruple("모임 가입하기", secondary, onJoin, enabled)
            }

            Button(
                onClick = onClick,
                enabled = isBtnEnabled,
                modifier = Modifier.weight(if (isMember && !isOwner) 2f else 1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(buttonText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun ScheduleCard(
    schedule: Schedule,
    isOwner: Boolean,
    canAttend: Boolean = false,
    onToggleAttend: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(schedule.title, style = MaterialTheme.typography.titleSmall)
                        if (schedule.isDrinking) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    "🍺 술자리",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (schedule.isSmoking) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    "🚬 흡연 가능",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    val displayTime = schedule.meetingTime.replace("T", " ").take(16)
                    Text(
                        "$displayTime | ${schedule.location ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isOwner) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "👥 참여 ${schedule.attendeeCount}명",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (canAttend) {
                    if (schedule.isAttending) {
                        OutlinedButton(
                            onClick = onToggleAttend,
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("✓ 참여 중", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Button(
                            onClick = onToggleAttend,
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("참여하기", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupDeleteDialog(groupName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("삭제 확인") },
        text = {
            Column {
                Text(
                    "'$groupName 삭제'를 입력하세요.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = text == "$groupName 삭제",
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text("삭제") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduleDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, meetingTime: String, location: String, isDrinking: Boolean, isSmoking: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var isDrinking by remember { mutableStateOf(false) }
    var isSmoking by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var selectedHour by remember { mutableStateOf<Int?>(null) }
    var selectedMinute by remember { mutableStateOf<Int?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateText = selectedDateMillis?.let {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = it }
        String.format(
            "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    } ?: ""
    val timeText = if (selectedHour != null && selectedMinute != null) {
        String.format("%02d:%02d", selectedHour, selectedMinute)
    } else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일정 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("날짜") },
                        placeholder = { Text("탭하여 날짜 선택") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true }
                    )
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("시간") },
                        placeholder = { Text("탭하여 시간 선택") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showTimePicker = true }
                    )
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("장소") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "이번 일정의 분위기",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isDrinking, onCheckedChange = { isDrinking = it })
                    Text("🍺 술자리 예정", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSmoking, onCheckedChange = { isSmoking = it })
                    Text("🚬 흡연 가능 자리", style = MaterialTheme.typography.bodyMedium)
                }

                errorMsg?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        title.isBlank() -> errorMsg = "제목을 입력하세요."
                        selectedDateMillis == null -> errorMsg = "날짜를 선택하세요."
                        selectedHour == null || selectedMinute == null -> errorMsg = "시간을 선택하세요."
                        else -> {
                            val meetingTime = "${dateText}T${timeText}:00"
                            onAdd(title.trim(), meetingTime, location.trim(), isDrinking, isSmoking)
                        }
                    }
                }
            ) { Text("등록") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val now = java.util.Calendar.getInstance()
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour ?: now.get(java.util.Calendar.HOUR_OF_DAY),
            initialMinute = selectedMinute ?: now.get(java.util.Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("시간 선택") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimeInput(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedHour = timePickerState.hour
                    selectedMinute = timePickerState.minute
                    showTimePicker = false
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("취소") }
            }
        )
    }
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
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
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
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        if (isGroupOwner) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text(
                                    "👑",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.wrapContentSize()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(member.nickname, style = MaterialTheme.typography.titleLarge)

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
                    ) {
                        Text(
                            text = member.bio?.takeIf { it.isNotEmpty() } ?: "등록된 자기소개가 없습니다.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isOwner && !isGroupOwner) {
                        Button(
                            onClick = onRemove,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("강퇴하기", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("닫기")
                    }
                }
            }
        }
    }
}

// ==================== 후기 카드 ====================
@Composable
fun ReviewCard(
    review: GroupReviewDTO,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = review.userAvatar,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        review.userNickname ?: "익명",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) { i ->
                            Icon(
                                imageVector = if (i < review.rating) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            review.createdAt.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (!review.content.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    review.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ==================== 후기 작성 다이얼로그 ====================
@Composable
fun ReviewWriteDialog(
    existingMyReview: GroupReviewDTO?,
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, content: String) -> Unit
) {
    var rating by remember { mutableIntStateOf(existingMyReview?.rating ?: 5) }
    var content by remember { mutableStateOf(existingMyReview?.content ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingMyReview != null) "후기 수정" else "후기 작성") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "별점",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    (1..5).forEach { i ->
                        Icon(
                            imageVector = if (i <= rating) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(36.dp)
                                .padding(2.dp)
                                .clickable { rating = i }
                        )
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("한줄평 (선택)") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (rating !in 1..5) {
                    error = "별점을 선택하세요."
                } else {
                    onSubmit(rating, content.trim())
                }
            }) { Text(if (existingMyReview != null) "수정" else "등록") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
