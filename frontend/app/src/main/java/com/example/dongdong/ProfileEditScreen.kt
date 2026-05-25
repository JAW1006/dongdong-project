package com.example.dongdong

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
private val SoftBg = Color(0xFFF9F9F9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val profile by viewModel.myProfile.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    // 프로필이 아직 로드 안 됐을 수 있음 → 진입 시 보장
    LaunchedEffect(Unit) {
        if (profile == null) viewModel.fetchMyPage(context)
    }

    // 폼 상태 (프로필 로드되면 초기화)
    var nickname by remember(profile?.id) { mutableStateOf(profile?.nickname ?: "") }
    var location by remember(profile?.id) { mutableStateOf(profile?.location ?: "") }
    var hobbyProfile by remember(profile?.id) { mutableStateOf(profile?.hobbyProfile ?: "") }
    var activityIndex by remember(profile?.id) { mutableIntStateOf(profile?.activityIndex ?: 3) }
    var socialIndex by remember(profile?.id) { mutableIntStateOf(profile?.socialIndex ?: 3) }
    var isDrinking by remember(profile?.id) { mutableStateOf(profile?.isDrinking ?: false) }
    var isSmoking by remember(profile?.id) { mutableStateOf(profile?.isSmoking ?: false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.uploadProfileImage(
                context = context,
                imageUri = it,
                onSuccess = {},
                onError = {}
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("프로필 편집", fontWeight = FontWeight.Bold) },
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
                        val payload = ProfileUpdateRequest(
                            nickname = nickname.trim().ifBlank { null },
                            location = location.trim().ifBlank { null },
                            hobbyProfile = hobbyProfile.trim(),
                            activityIndex = activityIndex,
                            socialIndex = socialIndex,
                            isSmoking = isSmoking,
                            isDrinking = isDrinking
                        )
                        viewModel.updateMyProfile(
                            context = context,
                            payload = payload,
                            onSuccess = { onBack() },
                            onError = {}
                        )
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeMain),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("저장", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        },
        containerColor = SoftBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 프로필 사진
            Box(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.BottomEnd
            ) {
                AsyncImage(
                    model = profile?.profileImage,
                    contentDescription = null,
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                        .clickable { imagePicker.launch("image/*") }
                )
                Surface(
                    shape = CircleShape,
                    color = OrangeMain,
                    modifier = Modifier.size(34.dp).clickable { imagePicker.launch("image/*") }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "사진 변경",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 닉네임
            FormField(label = "닉네임") {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 지역
            FormField(label = "지역") {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 한줄 소개
            FormField(label = "한줄 소개") {
                OutlinedTextField(
                    value = hobbyProfile,
                    onValueChange = { hobbyProfile = it },
                    placeholder = { Text("나를 한 문장으로 소개해보세요") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }

            // 활동 성향
            TraitSlider(
                label = "활동 성향",
                value = activityIndex,
                onChange = { activityIndex = it },
                leftLabel = "정적",
                rightLabel = "활동적"
            )

            // 사교 성향
            TraitSlider(
                label = "사교 성향",
                value = socialIndex,
                onChange = { socialIndex = it },
                leftLabel = "조용함",
                rightLabel = "사교적"
            )

            // 음주 / 흡연
            FormField(label = "생활 습관") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = isDrinking, onCheckedChange = { isDrinking = it })
                        Spacer(Modifier.width(8.dp))
                        Text("🍺 음주")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = isSmoking, onCheckedChange = { isSmoking = it })
                        Spacer(Modifier.width(8.dp))
                        Text("🚬 흡연")
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FormField(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun TraitSlider(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    leftLabel: String,
    rightLabel: String
) {
    FormField(label = label) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(1, 5)) },
            valueRange = 1f..5f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = OrangeMain,
                activeTrackColor = OrangeMain
            )
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(leftLabel, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))
            Text("$value / 5", fontSize = 11.sp, color = OrangeMain, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(rightLabel, fontSize = 11.sp, color = Color.Gray)
        }
    }
}
