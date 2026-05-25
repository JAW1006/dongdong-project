package com.example.dongdong

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

private val OrangeMain = Color(0xFFFF7043)
private val TealMain = Color(0xFF00BFA5)
private val SoftBg = Color(0xFFF9F9F9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPageScreen(
    viewModel: MainViewModel = viewModel(),
    onEditProfile: () -> Unit,
    onNavigateToGroup: (Int) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val profile by viewModel.myProfile.collectAsState()
    val groups by viewModel.myGroups.collectAsState()
    val schedules by viewModel.mySchedules.collectAsState()
    val isLoading by viewModel.isMyPageLoading.collectAsState()
    val unreadCounts by viewModel.unreadCounts.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchMyPage(context)
        viewModel.fetchUnreadCounts(context)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("마이페이지", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = SoftBg
    ) { padding ->
        if (isLoading && profile == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OrangeMain)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 프로필 헤더
            item {
                ProfileHeaderCard(profile = profile, onEdit = onEditProfile)
            }

            // 성향 / 생활습관
            item {
                ProfileTraitsCard(profile = profile)
            }

            // 가입한 모임 섹션
            item {
                SectionTitle("가입한 모임", count = groups.size)
            }
            if (groups.isEmpty()) {
                item { EmptyHint("아직 가입한 모임이 없어요.") }
            } else {
                items(groups, key = { it.id }) { group ->
                    JoinedGroupRow(
                        group = group,
                        unreadCount = unreadCounts[group.id] ?: 0,
                        onClick = { onNavigateToGroup(group.id) }
                    )
                }
            }

            // 다가오는 일정 섹션
            item {
                SectionTitle("내가 참여하는 일정", count = schedules.size)
            }
            if (schedules.isEmpty()) {
                item { EmptyHint("참여 표시한 다가오는 일정이 없어요.") }
            } else {
                items(schedules, key = { it.id }) { schedule ->
                    MyScheduleRow(schedule = schedule, onClick = { onNavigateToGroup(schedule.groupId) })
                }
            }

            // 로그아웃
            item {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.logout(context)
                        onLogout()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("로그아웃", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ProfileHeaderCard(profile: MyProfile?, onEdit: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = profile?.profileImage,
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile?.nickname ?: "닉네임",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            profile?.location ?: "-",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("편집", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                color = SoftBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    profile?.hobbyProfile?.takeIf { it.isNotBlank() } ?: "한줄 소개를 작성해보세요.",
                    fontSize = 14.sp,
                    color = if (profile?.hobbyProfile.isNullOrBlank()) Color.Gray else Color.DarkGray,
                    modifier = Modifier.padding(14.dp)
                )
            }

            // 관심 취미 칩
            if (!profile?.selectedHobbies.isNullOrEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    profile?.selectedHobbies?.take(5)?.forEach { hobby ->
                        Surface(
                            color = TealMain.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "#${hobby.name}",
                                color = TealMain,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTraitsCard(profile: MyProfile?) {
    val activityLabel = listOf("매우 정적", "정적인 편", "보통", "활동적인 편", "매우 활동적")
        .getOrNull((profile?.activityIndex ?: 3) - 1) ?: "보통"
    val socialLabel = listOf("매우 조용함", "조용한 편", "보통", "사교적인 편", "매우 사교적")
        .getOrNull((profile?.socialIndex ?: 3) - 1) ?: "보통"

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("성향 · 생활습관", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            TraitRow(label = "활동 성향", value = activityLabel)
            TraitRow(label = "사교 성향", value = socialLabel)
            TraitRow(label = "음주", value = if (profile?.isDrinking == true) "🍺 O" else "X")
            TraitRow(label = "흡연", value = if (profile?.isSmoking == true) "🚬 O" else "X")
        }
    }
}

@Composable
private fun TraitRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text("$count", fontSize = 14.sp, color = OrangeMain, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White
    ) {
        Text(
            text,
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Composable
private fun JoinedGroupRow(group: HobbyGroup, unreadCount: Int = 0, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color.White
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = group.groupImage,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.LightGray)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(group.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${group.location} · 멤버 ${group.memberCount}명",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            if (unreadCount > 0) {
                Surface(
                    color = OrangeMain,
                    shape = CircleShape,
                    modifier = Modifier.padding(end = 6.dp).heightIn(min = 22.dp)
                ) {
                    Text(
                        if (unreadCount > 99) "99+" else "$unreadCount",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

@Composable
private fun MyScheduleRow(schedule: MySchedule, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color.White
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(schedule.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    if (schedule.isDrinking) {
                        Spacer(Modifier.width(6.dp))
                        Text("🍺", fontSize = 13.sp)
                    }
                    if (schedule.isSmoking) {
                        Text("🚬", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    schedule.meetingTime.replace("T", " ").take(16),
                    fontSize = 12.sp,
                    color = OrangeMain,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${schedule.groupTitle} · ${schedule.location ?: "-"}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}
