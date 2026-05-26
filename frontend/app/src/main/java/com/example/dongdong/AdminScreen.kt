package com.example.dongdong

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dongdong.ui.BrandChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("모임 관리", "유저 관리", "신고 처리")

    val groups by viewModel.adminGroups.collectAsState()
    val users by viewModel.adminUsers.collectAsState()
    val reports by viewModel.adminReports.collectAsState()
    val isLoading by viewModel.isAdminLoading.collectAsState()

    var reportStatusFilter by remember { mutableStateOf("PENDING") }

    LaunchedEffect(tabIndex, reportStatusFilter) {
        when (tabIndex) {
            0 -> viewModel.adminFetchGroups(context)
            1 -> viewModel.adminFetchUsers(context)
            2 -> viewModel.adminFetchReports(context, reportStatusFilter)
        }
    }

    var pendingGroupDelete by remember { mutableStateOf<AdminGroupRow?>(null) }
    var pendingUserDelete by remember { mutableStateOf<AdminUserRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("관리자 콘솔", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        text = { Text(label, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Scaffold
            }

            when (tabIndex) {
                0 -> AdminGroupList(
                    groups = groups,
                    onDelete = { pendingGroupDelete = it }
                )
                1 -> AdminUserList(
                    users = users,
                    onBan = { viewModel.adminBanUser(context, it.id) },
                    onUnban = { viewModel.adminUnbanUser(context, it.id) },
                    onDelete = { pendingUserDelete = it }
                )
                2 -> AdminReportList(
                    reports = reports,
                    statusFilter = reportStatusFilter,
                    onFilterChange = { reportStatusFilter = it },
                    onResolve = { viewModel.adminResolveReport(context, it.id, dismiss = false) },
                    onDismiss = { viewModel.adminResolveReport(context, it.id, dismiss = true) }
                )
            }
        }
    }

    pendingGroupDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingGroupDelete = null },
            title = { Text("모임 삭제") },
            text = { Text("'${target.title}' 모임을 삭제하시겠습니까?\n채팅·후기·일정도 함께 사라집니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.adminDeleteGroup(context, target.id)
                    pendingGroupDelete = null
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingGroupDelete = null }) { Text("취소") }
            }
        )
    }

    pendingUserDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingUserDelete = null },
            title = { Text("계정 삭제") },
            text = { Text("'${target.nickname}'(@${target.loginId}) 계정을 완전히 삭제할까요?\n방장으로 있는 모임은 함께 삭제됩니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.adminDeleteUser(context, target.id)
                    pendingUserDelete = null
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUserDelete = null }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun AdminGroupList(
    groups: List<AdminGroupRow>,
    onDelete: (AdminGroupRow) -> Unit
) {
    if (groups.isEmpty()) {
        AdminEmpty("등록된 모임이 없습니다.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(groups, key = { it.id }) { group ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = group.groupImage,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "방장 ${group.leaderNickname ?: "-"} · ${group.location ?: "-"} · 멤버 ${group.memberCount}명",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { onDelete(group) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUserList(
    users: List<AdminUserRow>,
    onBan: (AdminUserRow) -> Unit,
    onUnban: (AdminUserRow) -> Unit,
    onDelete: (AdminUserRow) -> Unit
) {
    if (users.isEmpty()) {
        AdminEmpty("등록된 유저가 없습니다.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(users, key = { it.id }) { user ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = user.profileImage,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(user.nickname, style = MaterialTheme.typography.titleSmall)
                                if (user.isAdmin) {
                                    Spacer(Modifier.width(6.dp))
                                    BrandChip("ADMIN", MaterialTheme.colorScheme.secondary)
                                }
                                if (!user.isActive) {
                                    Spacer(Modifier.width(6.dp))
                                    BrandChip("정지", MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "@${user.loginId} · ${user.location ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    if (!user.isAdmin) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (user.isActive) {
                                OutlinedButton(
                                    onClick = { onBan(user) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = MaterialTheme.shapes.small
                                ) { Text("정지", style = MaterialTheme.typography.labelMedium) }
                            } else {
                                OutlinedButton(
                                    onClick = { onUnban(user) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    shape = MaterialTheme.shapes.small
                                ) { Text("정지 해제", style = MaterialTheme.typography.labelMedium) }
                            }
                            Button(
                                onClick = { onDelete(user) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = MaterialTheme.shapes.small
                            ) { Text("삭제", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminReportList(
    reports: List<ReportDTO>,
    statusFilter: String,
    onFilterChange: (String) -> Unit,
    onResolve: (ReportDTO) -> Unit,
    onDismiss: (ReportDTO) -> Unit
) {
    val filters = listOf("PENDING" to "대기", "RESOLVED" to "처리됨", "DISMISSED" to "기각됨")

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { (value, label) ->
                FilterChip(
                    selected = statusFilter == value,
                    onClick = { onFilterChange(value) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        if (reports.isEmpty()) {
            AdminEmpty("해당 상태의 신고가 없습니다.")
            return
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
            items(reports, key = { it.id }) { report ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val typeLabel = when (report.targetType) {
                                "group" -> "모임"
                                "user" -> "유저"
                                "chat" -> "채팅"
                                else -> report.targetType
                            }
                            BrandChip(typeLabel, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            when (report.status) {
                                "RESOLVED" -> BrandChip("처리됨", MaterialTheme.colorScheme.secondary)
                                "DISMISSED" -> BrandChip("기각됨", MaterialTheme.colorScheme.onSurfaceVariant)
                                else -> BrandChip("대기", MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                report.createdAt.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "대상: ${report.targetLabel ?: "#${report.targetId}"}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "신고자: ${report.reporterNickname ?: "#${report.reporterId}"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            report.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (report.status == "PENDING") {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onDismiss(report) },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.small
                                ) { Text("기각", style = MaterialTheme.typography.labelMedium) }
                                Button(
                                    onClick = { onResolve(report) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    shape = MaterialTheme.shapes.small
                                ) { Text("처리완료", style = MaterialTheme.typography.labelMedium) }
                            }
                        } else if (!report.adminNote.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "처리 메모: ${report.adminNote}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminEmpty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// 🚀 신고 작성 다이얼로그 (재사용)
@Composable
fun ReportDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("신고 사유") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )
        },
        confirmButton = {
            TextButton(
                enabled = reason.isNotBlank(),
                onClick = { onSubmit(reason.trim()); onDismiss() }
            ) { Text("신고", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
