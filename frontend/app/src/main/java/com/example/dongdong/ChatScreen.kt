package com.example.dongdong

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.dongdong.network.AuthManager
import com.example.dongdong.network.ChatWebSocket
import com.example.dongdong.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val OrangeMain = Color(0xFFFF7043)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    groupId: Int,
    groupTitle: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val token = remember { AuthManager.getToken(context) }
    val currentUserId = remember { AuthManager.getUserId(context) }

    var messages by remember { mutableStateOf(listOf<ChatMessageDTO>()) }
    var inputText by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }
    var isLoadingHistory by remember { mutableStateOf(true) }

    // WebSocket 인스턴스
    val chatWebSocket = remember {
        ChatWebSocket(
            groupId = groupId,
            token = token,
            onMessageReceived = { msg ->
                messages = messages + msg
            },
            onConnectionChanged = { connected ->
                isConnected = connected
            }
        )
    }

    // 화면 진입 시 이전 메시지 로드 + WebSocket 연결
    LaunchedEffect(groupId) {
        // 1. 이전 메시지 로드
        try {
            val history = withContext(Dispatchers.IO) {
                RetrofitClient.instance.getChatMessages("Bearer $token", groupId)
            }
            messages = history
        } catch (e: Exception) {
            Log.e("ChatScreen", "이전 메시지 로드 실패: ${e.message}")
        } finally {
            isLoadingHistory = false
        }

        // 2. WebSocket 연결
        chatWebSocket.connect()
    }

    // 화면 이탈 시 연결 해제
    DisposableEffect(Unit) {
        onDispose {
            chatWebSocket.disconnect()
        }
    }

    // 새 메시지가 추가되면 맨 아래로 스크롤
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(groupTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (isConnected) "연결됨" else "연결 중...",
                            fontSize = 12.sp,
                            color = if (isConnected) Color(0xFF00BFA5) else Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            // 메시지 입력 영역
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("메시지를 입력하세요", color = Color(0xFFBDBDBD)) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF5F5F5),
                            unfocusedContainerColor = Color(0xFFF5F5F5),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = false,
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 전송 버튼
                    IconButton(
                        onClick = {
                            val text = inputText.trim()
                            if (text.isNotEmpty() && isConnected) {
                                chatWebSocket.sendMessage(text)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (inputText.isNotBlank() && isConnected) OrangeMain else Color(0xFFE0E0E0),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "전송",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF9F9F9)
    ) { padding ->
        if (isLoadingHistory) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = OrangeMain)
            }
        } else if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("아직 대화가 없습니다", fontSize = 16.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("첫 메시지를 보내보세요!", fontSize = 14.sp, color = Color(0xFFBDBDBD))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    when (msg.type) {
                        "system" -> SystemMessage(msg.message)
                        else -> ChatBubble(
                            message = msg,
                            isMe = msg.senderId == currentUserId
                        )
                    }
                }
            }
        }
    }
}

// ==================== 시스템 메시지 (입장/퇴장) ====================
@Composable
fun SystemMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFFE0E0E0),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                color = Color(0xFF757575),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// ==================== 채팅 말풍선 ====================
@Composable
fun ChatBubble(message: ChatMessageDTO, isMe: Boolean) {
    val timeText = message.createdAt?.let { formatChatTime(it) } ?: ""

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        // 상대방 닉네임 (내 메시지가 아닐 때만)
        if (!isMe) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                AsyncImage(
                    model = message.senderProfileImage,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = message.senderNickname ?: "알 수 없음",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.DarkGray
                )
            }
        }

        // 말풍선 + 시간
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isMe) {
                // 내 메시지: 시간 → 말풍선
                Text(
                    text = timeText,
                    fontSize = 11.sp,
                    color = Color(0xFFBDBDBD),
                    modifier = Modifier.padding(end = 6.dp, bottom = 2.dp)
                )
            }

            if (!isMe) {
                Spacer(modifier = Modifier.width(30.dp)) // 프로필 사진 너비만큼 들여쓰기
            }

            Surface(
                color = if (isMe) OrangeMain else Color.White,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                ),
                shadowElevation = if (isMe) 0.dp else 1.dp
            ) {
                Text(
                    text = message.message,
                    fontSize = 15.sp,
                    color = if (isMe) Color.White else Color.DarkGray,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    lineHeight = 20.sp
                )
            }

            if (!isMe) {
                // 상대 메시지: 말풍선 → 시간
                Text(
                    text = timeText,
                    fontSize = 11.sp,
                    color = Color(0xFFBDBDBD),
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                )
            }
        }
    }
}

// ==================== 시간 포맷 ====================
fun formatChatTime(isoTime: String): String {
    return try {
        // "2026-05-12T14:30:00" → "오후 2:30"
        val time = isoTime.substringAfter("T").take(5) // "14:30"
        val parts = time.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1]
        val period = if (hour < 12) "오전" else "오후"
        val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        "$period $displayHour:$minute"
    } catch (e: Exception) {
        ""
    }
}
