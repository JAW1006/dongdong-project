package com.example.dongdong

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dongdong.network.RetrofitClient
import com.example.dongdong.network.AuthManager
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File

// UI 이벤트를 정의합니다.
sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
}

class MainViewModel : ViewModel() {

    // 1. 상태 관리 (StateFlow)

    // 메인 목록 데이터
    private val _groups = MutableStateFlow<List<HobbyGroup>>(emptyList())
    val groups: StateFlow<List<HobbyGroup>> = _groups.asStateFlow()

    // 로딩 상태 (UI에서 프로그레스 바 표시용)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 카테고리 필터 상태
    private val _selectedCategory = MutableStateFlow(HobbyCategory.ALL)
    val selectedCategory: StateFlow<HobbyCategory> = _selectedCategory.asStateFlow()

    // 검색어 상태
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 지역 필터 상태
    private val _locationFilter = MutableStateFlow("")
    val locationFilter: StateFlow<String> = _locationFilter.asStateFlow()

    // 정렬 상태 (latest, members)
    private val _sortOption = MutableStateFlow("latest")
    val sortOption: StateFlow<String> = _sortOption.asStateFlow()

    // 상세 페이지 데이터
    private val _selectedGroupDetail = MutableStateFlow<HobbyGroup?>(null)
    val selectedGroupDetail: StateFlow<HobbyGroup?> = _selectedGroupDetail.asStateFlow()

    // 현재 사용자의 방장 여부
    private val _isLeader = MutableStateFlow(false)
    val isLeader: StateFlow<Boolean> = _isLeader.asStateFlow()

    // 현재 사용자의 멤버 여부
    private val _isMember = MutableStateFlow(false)
    val isMember: StateFlow<Boolean> = _isMember.asStateFlow()

    // 🚀 현재 사용자의 가입 신청 대기 여부
    private val _hasPendingRequest = MutableStateFlow(false)
    val hasPendingRequest: StateFlow<Boolean> = _hasPendingRequest.asStateFlow()

    // 🚀 알림 목록 (방장용 가입 신청 목록)
    private val _notifications = MutableStateFlow<List<JoinRequestDTO>>(emptyList())
    val notifications: StateFlow<List<JoinRequestDTO>> = _notifications.asStateFlow()

    // 처리 로딩 상태 (가입/탈퇴/강퇴 공통)
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // UI 이벤트 전달을 위한 SharedFlow
    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    // 2. 초기화
    init {
        fetchGroups()
    }

    // 3. 비즈니스 로직

    fun fetchGroups() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val search = _searchQuery.value.ifBlank { null }
                val location = _locationFilter.value.ifBlank { null }
                val sort = _sortOption.value

                val response = RetrofitClient.instance.getGroups(
                    search = search,
                    location = location,
                    sort = sort
                )
                _groups.value = response
            } catch (e: Exception) {
                Log.e("FETCH_GROUPS_ERROR", "목록 조회 실패: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateLocationFilter(location: String) {
        _locationFilter.value = location
        fetchGroups()
    }

    fun updateSortOption(sort: String) {
        _sortOption.value = sort
        fetchGroups()
    }

    fun performSearch() {
        fetchGroups()
    }

    fun register(context: Context, user: UserRegisterRequest, onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.registerUser(user)
                if (response.id > 0) {
                    // 🚀 가입 직후 토큰 저장 → profile-setup API 호출 가능
                    if (response.accessToken.isNotEmpty()) {
                        AuthManager.saveAuthData(context, response.accessToken, response.id)
                    }
                    onSuccess(response.id)
                } else {
                    onError("회원가입 실패")
                }
            } catch (e: Exception) {
                onError("이미 존재하는 아이디이거나 서버 에러입니다.")
            }
        }
    }

    /**
     * 🚀 프로필 설정 저장 (취미/성향/생활습관)
     */
    fun saveProfileSetup(
        context: Context,
        payload: ProfileSetupRequest,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) {
                    onError("로그인 정보가 없습니다.")
                    return@launch
                }
                val response = RetrofitClient.instance.setupProfile("Bearer $token", payload)
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    onError("프로필 저장 실패: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("PROFILE_SETUP", "저장 실패: ${e.message}")
                onError("네트워크 연결을 확인해주세요.")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // 🚀 AI 추천 상태
    private val _recommendations = MutableStateFlow<List<RecommendedGroupDTO>>(emptyList())
    val recommendations: StateFlow<List<RecommendedGroupDTO>> = _recommendations.asStateFlow()

    private val _isRecommendLoading = MutableStateFlow(false)
    val isRecommendLoading: StateFlow<Boolean> = _isRecommendLoading.asStateFlow()

    fun fetchRecommendations(context: Context) {
        viewModelScope.launch {
            _isRecommendLoading.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                val response = RetrofitClient.instance.getRecommendations("Bearer $token", 3)
                _recommendations.value = response.recommendations
            } catch (e: Exception) {
                Log.e("RECOMMEND", "추천 실패: ${e.message}")
                _recommendations.value = emptyList()
            } finally {
                _isRecommendLoading.value = false
            }
        }
    }

    fun login(context: Context, loginRequest: UserLoginRequest, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.login(loginRequest)
                AuthManager.saveToken(context, response.accessToken)
                onSuccess()
            } catch (e: Exception) {
                onError("로그인 정보를 확인해주세요.")
            }
        }
    }

    /**
     * 🚀 [상세 정보 가져오기]
     */
    fun fetchGroupDetail(context: Context, groupId: Int) {
        viewModelScope.launch {
            // 새로운 데이터를 가져오기 전 기존 상태 초기화
            _selectedGroupDetail.value = null
            _isLeader.value = false
            _isMember.value = false
            _hasPendingRequest.value = false

            try {
                val token = AuthManager.getToken(context)
                val authHeader = if (token.isNotEmpty()) "Bearer $token" else ""

                val response = RetrofitClient.instance.getGroupDetail(authHeader, groupId)

                Log.d("AUTH_CHECK", "서버 응답 - 방장여부: ${response.isLeader}, 가입여부: ${response.isMember}, 신청중: ${response.hasPendingRequest}")

                _selectedGroupDetail.value = response.groupData
                _isLeader.value = response.isLeader
                _isMember.value = response.isMember
                _hasPendingRequest.value = response.hasPendingRequest

            } catch (e: Exception) {
                Log.e("FETCH_ERROR", "상세 페이지 로딩 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("정보를 불러오지 못했습니다."))
            }
        }
    }

    /**
     * 🚀 [모임 가입 신청하기]
     */
    fun applyToGroup(context: Context, groupId: Int) {
        if (_isProcessing.value) return

        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) {
                    _eventFlow.emit(UiEvent.ShowToast("로그인이 필요합니다."))
                    return@launch
                }

                val response = RetrofitClient.instance.applyToGroup("Bearer $token", groupId)

                if (response.isSuccessful) {
                    _eventFlow.emit(UiEvent.ShowToast("가입 신청이 완료되었습니다! 📩"))
                    fetchGroupDetail(context, groupId)
                } else {
                    val errorMsg = if (response.code() == 400) "이미 신청했거나 가입된 상태입니다." else "신청에 실패했습니다."
                    _eventFlow.emit(UiEvent.ShowToast(errorMsg))
                }
            } catch (e: Exception) {
                Log.e("APPLY_ERROR", "신청 에러: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("네트워크 연결을 확인해주세요."))
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * 🚀 [알림: 가입 신청 목록 가져오기]
     */
    fun fetchNotifications(context: Context) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                
                val response = RetrofitClient.instance.getPendingRequests("Bearer $token")
                _notifications.value = response
            } catch (e: Exception) {
                Log.e("NOTI_ERROR", "알림 로딩 실패: ${e.message}")
            }
        }
    }

    /**
     * 🚀 [알림: 신청 수락 또는 거절]
     */
    fun respondToRequest(context: Context, requestId: Int, accept: Boolean) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                val response = RetrofitClient.instance.respondToRequest("Bearer $token", requestId, accept)
                
                if (response.isSuccessful) {
                    val msg = if (accept) "가입을 승인했습니다." else "가입을 거절했습니다."
                    _eventFlow.emit(UiEvent.ShowToast(msg))
                    fetchNotifications(context) // 목록 갱신
                }
            } catch (e: Exception) {
                Log.e("RESPOND_ERROR", "응답 처리 실패: ${e.message}")
            }
        }
    }

    /**
     * 🚀 [모임 탈퇴하기]
     */
    fun leaveGroup(context: Context, groupId: Int) {
        if (_isProcessing.value) return

        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch

                val response = RetrofitClient.instance.leaveGroup("Bearer $token", groupId)
                if (response.isSuccessful) {
                    _isMember.value = false
                    _eventFlow.emit(UiEvent.ShowToast("모임에서 탈퇴했습니다."))
                    fetchGroupDetail(context, groupId)
                } else {
                    _eventFlow.emit(UiEvent.ShowToast("탈퇴 실패: ${response.message()}"))
                }
            } catch (e: Exception) {
                Log.e("LEAVE_ERROR", "탈퇴 에러: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * 🚀 [멤버 강퇴하기]
     */
    fun kickMember(context: Context, groupId: Int, userId: Int) {
        if (_isProcessing.value) return

        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch

                val response = RetrofitClient.instance.kickMember("Bearer $token", groupId, userId)
                if (response.isSuccessful) {
                    _eventFlow.emit(UiEvent.ShowToast("멤버를 강퇴했습니다."))
                    fetchGroupDetail(context, groupId)
                } else {
                    _eventFlow.emit(UiEvent.ShowToast("강퇴 실패: ${response.message()}"))
                }
            } catch (e: Exception) {
                Log.e("KICK_ERROR", "강퇴 에러: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * 🚀 [모임 생성하기]
     */
    fun createGroup(
        context: Context,
        title: String,
        description: String,
        location: String,
        category: HobbyCategory,
        tags: List<String>,
        imageUri: Uri?,
        onSuccess: (Int) -> Unit
    ) {
        if (_isProcessing.value) return

        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val token = AuthManager.getToken(context)
                val userId = AuthManager.getUserId(context)

                if (token.isEmpty()) {
                    _eventFlow.emit(UiEvent.ShowToast("로그인이 필요합니다."))
                    return@launch
                }

                val titleBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
                val descBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
                val locationBody = location.toRequestBody("text/plain".toMediaTypeOrNull())
                val hobbyIdBody = category.id.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val leaderIdBody = (if (userId != -1) userId else 1).toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val tagsJson = Gson().toJson(tags)
                val tagsBody = tagsJson.toRequestBody("text/plain".toMediaTypeOrNull())

                // 이미지 파일 처리
                var imagePart: MultipartBody.Part? = null
                if (imageUri != null) {
                    val inputStream = context.contentResolver.openInputStream(imageUri)
                    val tempFile = File(context.cacheDir, "group_image_${System.currentTimeMillis()}.jpg")
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val requestFile = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                    imagePart = MultipartBody.Part.createFormData("image", tempFile.name, requestFile)
                }

                val response = RetrofitClient.instance.createGroup(
                    token = "Bearer $token",
                    title = titleBody,
                    description = descBody,
                    location = locationBody,
                    hobbyId = hobbyIdBody,
                    leaderId = leaderIdBody,
                    tags = tagsBody,
                    image = imagePart
                )
                _eventFlow.emit(UiEvent.ShowToast("모임이 생성되었습니다!"))
                fetchGroups()
                onSuccess(response.id)
            } catch (e: Exception) {
                if (e is HttpException) {
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e("CREATE_GROUP_ERROR", "HTTP ${e.code()}: $errorBody")

                    val detailMessage = try {
                        val gson = Gson()
                        val map = gson.fromJson(errorBody, Map::class.java)
                        val detail = map["detail"]
                        if (detail is List<*>) {
                            (detail.firstOrNull() as? Map<*, *>)?.get("msg")?.toString() ?: errorBody
                        } else {
                            detail?.toString() ?: errorBody
                        }
                    } catch (ex: Exception) {
                        errorBody
                    }
                    _eventFlow.emit(UiEvent.ShowToast("생성 실패: $detailMessage"))
                } else {
                    Log.e("CREATE_GROUP_ERROR", "모임 생성 실패: ${e.message}")
                    _eventFlow.emit(UiEvent.ShowToast("모임 생성에 실패했습니다."))
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun selectCategory(category: HobbyCategory) {
        _selectedCategory.value = category
    }

    /**
     * 🚀 [일정 참여/취소 토글] — 모임원 전용
     */
    fun toggleScheduleAttendance(context: Context, groupId: Int, scheduleId: Int) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) {
                    _eventFlow.emit(UiEvent.ShowToast("로그인이 필요합니다."))
                    return@launch
                }
                val updated = RetrofitClient.instance.toggleAttendance("Bearer $token", groupId, scheduleId)
                // 상세 화면 일정 리스트에서 해당 항목만 교체
                _selectedGroupDetail.value = _selectedGroupDetail.value?.let { current ->
                    current.copy(
                        schedules = current.schedules.map { if (it.id == updated.id) updated else it }
                    )
                }
                val msg = if (updated.isAttending) "참여로 표시했어요." else "참여를 취소했어요."
                _eventFlow.emit(UiEvent.ShowToast(msg))
            } catch (e: Exception) {
                Log.e("ATTEND_ERROR", "참여 토글 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("참여 처리에 실패했습니다."))
            }
        }
    }

    /**
     * 🚀 [모임 일정 추가하기]
     */
    fun createSchedule(
        context: Context,
        groupId: Int,
        title: String,
        meetingTime: String,
        location: String,
        isDrinking: Boolean,
        isSmoking: Boolean,
        onSuccess: () -> Unit
    ) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) {
                    _eventFlow.emit(UiEvent.ShowToast("로그인이 필요합니다."))
                    return@launch
                }
                val body = ScheduleCreateRequest(
                    title = title,
                    meetingTime = meetingTime,
                    location = location.ifBlank { null },
                    isDrinking = isDrinking,
                    isSmoking = isSmoking
                )
                RetrofitClient.instance.createSchedule("Bearer $token", groupId, body)
                _eventFlow.emit(UiEvent.ShowToast("일정이 등록되었습니다."))
                fetchGroupDetail(context, groupId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("SCHEDULE_ERROR", "일정 생성 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("일정 등록에 실패했습니다."))
            } finally {
                _isProcessing.value = false
            }
        }
    }
}
