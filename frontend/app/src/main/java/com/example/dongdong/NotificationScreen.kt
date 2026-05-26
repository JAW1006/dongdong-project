package com.example.dongdong

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dongdong.ui.BrandChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToGroup: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("알림", "가입 신청")

    val inbox by viewModel.inboxNotifications.collectAsState()
    val joinRequests by viewModel.notifications.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchInboxNotifications(context)
        viewModel.fetchNotifications(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("알림", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    if (tabIndex == 0 && inbox.any { !it.isRead }) {
                        TextButton(onClick = { viewModel.markAllNotificationsRead(context) }) {
                            Text("모두 읽음", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = tabIndex == i,
                        onClick = { tabIndex = i },
                        text = { Text(label, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            when (tabIndex) {
                0 -> InboxList(
                    items = inbox,
                    onClickItem = { n ->
                        if (!n.isRead) viewModel.markNotificationRead(context, n.id)
                        n.linkGroupId?.let { onNavigateToGroup(it) }
                    }
                )
                1 -> JoinRequestList(
                    requests = joinRequests,
                    onAccept = { req -> viewModel.respondToRequest(context, req.id, true) },
                    onReject = { req -> viewModel.respondToRequest(context, req.id, false) }
                )
            }
        }
    }
}

@Composable
private fun InboxList(
    items: List<NotificationDTO>,
    onClickItem: (NotificationDTO) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "도착한 알림이 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { n ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onClickItem(n) },
                shape = MaterialTheme.shapes.medium,
                color = if (n.isRead) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                shadowElevation = if (n.isRead) 0.dp else 1.dp
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (chipText, chipColor) = when (n.type) {
                            "chat" -> "채팅" to MaterialTheme.colorScheme.secondary
                            "join_approved" -> "가입 승인" to MaterialTheme.colorScheme.primary
                            "schedule" -> "일정" to MaterialTheme.colorScheme.primary
                            else -> "안내" to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        BrandChip(chipText, chipColor)
                        Spacer(Modifier.weight(1f))
                        Text(
                            n.createdAt.replace("T", " ").take(16),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(n.title, style = MaterialTheme.typography.titleSmall)
                    if (!n.body.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            n.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinRequestList(
    requests: List<JoinRequestDTO>,
    onAccept: (JoinRequestDTO) -> Unit,
    onReject: (JoinRequestDTO) -> Unit
) {
    if (requests.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "대기 중인 가입 신청이 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(requests, key = { it.id }) { request ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${request.userNickname ?: "알 수 없는 사용자"}님",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "'${request.groupTitle ?: "모임"}' 가입을 신청했습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { onReject(request) }) {
                            Text(
                                "거절",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onAccept(request) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("수락", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
