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

    // 🚀 현재 GPS 기반 위치 (추천 호출 시 함께 전송)
    private val _currentLocation = MutableStateFlow<String?>(null)
    val currentLocation: StateFlow<String?> = _currentLocation.asStateFlow()

    fun refreshCurrentLocation(context: Context, thenRefetchRecommendations: Boolean = true) {
        viewModelScope.launch {
            try {
                val helper = LocationHelper(context)
                val addr = helper.fetchCurrentAddress()
                if (!addr.isNullOrBlank()) {
                    _currentLocation.value = addr
                    if (thenRefetchRecommendations) {
                        fetchRecommendations(context)
                    }
                }
            } catch (e: Exception) {
                Log.e("LOCATION", "현재 위치 조회 실패: ${e.message}")
            }
        }
    }

    fun fetchRecommendations(context: Context) {
        viewModelScope.launch {
            _isRecommendLoading.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                val response = RetrofitClient.instance.getRecommendations(
                    token = "Bearer $token",
                    topN = 3,
                    currentLocation = _currentLocation.value
                )
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
                // 🚀 토큰과 함께 userId도 저장 (채팅 isMe 판별 등에 필요)
                AuthManager.saveAuthData(context, response.accessToken, response.userId)
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

    // 🚀 마이페이지 관련 상태
    private val _myProfile = MutableStateFlow<MyProfile?>(null)
    val myProfile: StateFlow<MyProfile?> = _myProfile.asStateFlow()

    private val _myGroups = MutableStateFlow<List<HobbyGroup>>(emptyList())
    val myGroups: StateFlow<List<HobbyGroup>> = _myGroups.asStateFlow()

    private val _mySchedules = MutableStateFlow<List<MySchedule>>(emptyList())
    val mySchedules: StateFlow<List<MySchedule>> = _mySchedules.asStateFlow()

    private val _isMyPageLoading = MutableStateFlow(false)
    val isMyPageLoading: StateFlow<Boolean> = _isMyPageLoading.asStateFlow()

    fun fetchMyPage(context: Context) {
        viewModelScope.launch {
            _isMyPageLoading.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                val auth = "Bearer $token"

                val profile = RetrofitClient.instance.getMyProfile(auth)
                val groups = RetrofitClient.instance.getMyGroups(auth)
                val schedules = RetrofitClient.instance.getMySchedules(auth)

                _myProfile.value = profile
                _myGroups.value = groups
                _mySchedules.value = schedules
            } catch (e: Exception) {
                Log.e("MYPAGE", "조회 실패: ${e.message}")
            } finally {
                _isMyPageLoading.value = false
            }
        }
    }

    fun updateMyProfile(
        context: Context,
        payload: ProfileUpdateRequest,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) {
                    onError("로그인이 필요합니다.")
                    return@launch
                }
                val updated = RetrofitClient.instance.updateMyProfile("Bearer $token", payload)
                _myProfile.value = updated
                _eventFlow.emit(UiEvent.ShowToast("프로필이 업데이트되었습니다."))
                onSuccess()
            } catch (e: Exception) {
                Log.e("MYPAGE", "수정 실패: ${e.message}")
                onError("프로필 수정에 실패했습니다.")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun logout(context: Context) {
        AuthManager.clearAuthData(context)
        _myProfile.value = null
        _myGroups.value = emptyList()
        _mySchedules.value = emptyList()
    }

    // 🚀 안 읽음 카운트 (group_id -> count)
    private val _unreadCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<Int, Int>> = _unreadCounts.asStateFlow()

    fun fetchUnreadCounts(context: Context) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                val raw = RetrofitClient.instance.getUnreadCounts("Bearer $token")
                _unreadCounts.value = raw.mapKeys { it.key.toInt() }
            } catch (e: Exception) {
                Log.e("UNREAD", "조회 실패: ${e.message}")
            }
        }
    }

    fun markChatRead(context: Context, groupId: Int) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                RetrofitClient.instance.markChatRead("Bearer $token", groupId)
                // 로컬 상태에서도 즉시 0으로
                _unreadCounts.value = _unreadCounts.value.toMutableMap().apply { put(groupId, 0) }
            } catch (e: Exception) {
                Log.e("UNREAD", "갱신 실패: ${e.message}")
            }
        }
    }

    /**
     * 🚀 [채팅 이미지 업로드]
     */
    fun sendChatImage(
        context: Context,
        groupId: Int,
        imageUri: Uri,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) {
                    onError("로그인이 필요합니다.")
                    return@launch
                }
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val tempFile = File(context.cacheDir, "chat_image_${System.currentTimeMillis()}.jpg")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                val requestFile = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
                RetrofitClient.instance.uploadChatImage("Bearer $token", groupId, part)
                onSuccess()
            } catch (e: Exception) {
                Log.e("CHAT_IMAGE", "업로드 실패: ${e.message}")
                onError("이미지 전송에 실패했습니다.")
            }
        }
    }

    // 🚀 후기 상태
    private val _reviews = MutableStateFlow<List<GroupReviewDTO>>(emptyList())
    val reviews: StateFlow<List<GroupReviewDTO>> = _reviews.asStateFlow()

    fun fetchReviews(context: Context, groupId: Int) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                _reviews.value = RetrofitClient.instance.getReviews("Bearer $token", groupId)
            } catch (e: Exception) {
                Log.e("REVIEW", "조회 실패: ${e.message}")
            }
        }
    }

    fun submitReview(
        context: Context,
        groupId: Int,
        rating: Int,
        content: String?,
        onSuccess: () -> Unit
    ) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                RetrofitClient.instance.createReview(
                    "Bearer $token",
                    groupId,
                    GroupReviewCreateRequest(rating = rating, content = content)
                )
                _eventFlow.emit(UiEvent.ShowToast("후기가 등록되었습니다."))
                fetchReviews(context, groupId)
                fetchGroupDetail(context, groupId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("REVIEW", "작성 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("후기 등록에 실패했습니다."))
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun deleteReview(context: Context, groupId: Int, reviewId: Int) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                val res = RetrofitClient.instance.deleteReview("Bearer $token", groupId, reviewId)
                if (res.isSuccessful) {
                    _eventFlow.emit(UiEvent.ShowToast("후기가 삭제되었습니다."))
                    fetchReviews(context, groupId)
                    fetchGroupDetail(context, groupId)
                }
            } catch (e: Exception) {
                Log.e("REVIEW", "삭제 실패: ${e.message}")
            }
        }
    }

    /**
     * 🚀 [모임 정보 수정 — 방장 전용]
     */
    fun updateGroup(
        context: Context,
        groupId: Int,
        payload: GroupUpdateRequest,
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
                val updated = RetrofitClient.instance.updateGroup("Bearer $token", groupId, payload)
                _selectedGroupDetail.value = updated
                _eventFlow.emit(UiEvent.ShowToast("모임 정보가 수정되었습니다."))
                onSuccess()
            } catch (e: Exception) {
                Log.e("UPDATE_GROUP", "수정 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("수정에 실패했습니다."))
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * 🚀 [모임 삭제 — 방장 전용]
     */
    fun deleteGroup(context: Context, groupId: Int, onSuccess: () -> Unit) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) {
                    _eventFlow.emit(UiEvent.ShowToast("로그인이 필요합니다."))
                    return@launch
                }
                val response = RetrofitClient.instance.deleteGroup("Bearer $token", groupId)
                if (response.isSuccessful) {
                    _eventFlow.emit(UiEvent.ShowToast("모임을 삭제했습니다."))
                    fetchGroups()
                    onSuccess()
                } else {
                    _eventFlow.emit(UiEvent.ShowToast("삭제 실패: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e("DELETE_GROUP", "삭제 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("삭제에 실패했습니다."))
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun uploadProfileImage(
        context: Context,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) {
                    onError("로그인이 필요합니다.")
                    return@launch
                }
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val tempFile = File(context.cacheDir, "profile_image_${System.currentTimeMillis()}.jpg")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                val requestFile = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
                val response = RetrofitClient.instance.uploadProfileImage("Bearer $token", part)

                // 프로필 상태에도 즉시 반영
                _myProfile.value = _myProfile.value?.copy(profileImage = response.imageUrl)
                onSuccess(response.imageUrl)
            } catch (e: Exception) {
                Log.e("UPLOAD_PROFILE", "이미지 업로드 실패: ${e.message}")
                onError("이미지 업로드에 실패했습니다.")
            }
        }
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

    // 🚀 관리자 콘솔 상태
    private val _adminGroups = MutableStateFlow<List<AdminGroupRow>>(emptyList())
    val adminGroups: StateFlow<List<AdminGroupRow>> = _adminGroups.asStateFlow()

    private val _adminUsers = MutableStateFlow<List<AdminUserRow>>(emptyList())
    val adminUsers: StateFlow<List<AdminUserRow>> = _adminUsers.asStateFlow()

    private val _isAdminLoading = MutableStateFlow(false)
    val isAdminLoading: StateFlow<Boolean> = _isAdminLoading.asStateFlow()

    fun adminFetchGroups(context: Context, search: String? = null) {
        viewModelScope.launch {
            _isAdminLoading.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                _adminGroups.value = RetrofitClient.instance.adminListGroups(
                    "Bearer $token",
                    search?.ifBlank { null }
                )
            } catch (e: Exception) {
                Log.e("ADMIN", "모임 조회 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("모임 목록을 불러오지 못했습니다."))
            } finally {
                _isAdminLoading.value = false
            }
        }
    }

    fun adminFetchUsers(context: Context, search: String? = null) {
        viewModelScope.launch {
            _isAdminLoading.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                _adminUsers.value = RetrofitClient.instance.adminListUsers(
                    "Bearer $token",
                    search?.ifBlank { null }
                )
            } catch (e: Exception) {
                Log.e("ADMIN", "유저 조회 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("유저 목록을 불러오지 못했습니다."))
            } finally {
                _isAdminLoading.value = false
            }
        }
    }

    fun adminDeleteGroup(context: Context, groupId: Int) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                val res = RetrofitClient.instance.adminDeleteGroup("Bearer $token", groupId)
                if (res.isSuccessful) {
                    _adminGroups.value = _adminGroups.value.filterNot { it.id == groupId }
                    _eventFlow.emit(UiEvent.ShowToast("모임을 삭제했습니다."))
                } else {
                    _eventFlow.emit(UiEvent.ShowToast("삭제 실패: ${res.code()}"))
                }
            } catch (e: Exception) {
                Log.e("ADMIN", "모임 삭제 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("삭제에 실패했습니다."))
            }
        }
    }

    fun adminBanUser(context: Context, userId: Int) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                val updated = RetrofitClient.instance.adminBanUser("Bearer $token", userId)
                _adminUsers.value = _adminUsers.value.map { if (it.id == updated.id) updated else it }
                _eventFlow.emit(UiEvent.ShowToast("계정을 정지했습니다."))
            } catch (e: Exception) {
                Log.e("ADMIN", "정지 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("정지에 실패했습니다."))
            }
        }
    }

    fun adminUnbanUser(context: Context, userId: Int) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                val updated = RetrofitClient.instance.adminUnbanUser("Bearer $token", userId)
                _adminUsers.value = _adminUsers.value.map { if (it.id == updated.id) updated else it }
                _eventFlow.emit(UiEvent.ShowToast("정지를 해제했습니다."))
            } catch (e: Exception) {
                Log.e("ADMIN", "해제 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("해제에 실패했습니다."))
            }
        }
    }

    // 🚀 관리자 신고 처리 상태
    private val _adminReports = MutableStateFlow<List<ReportDTO>>(emptyList())
    val adminReports: StateFlow<List<ReportDTO>> = _adminReports.asStateFlow()

    fun adminFetchReports(context: Context, status: String? = "PENDING") {
        viewModelScope.launch {
            _isAdminLoading.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                _adminReports.value = RetrofitClient.instance.adminListReports("Bearer $token", status)
            } catch (e: Exception) {
                Log.e("ADMIN", "신고 조회 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("신고 목록을 불러오지 못했습니다."))
            } finally {
                _isAdminLoading.value = false
            }
        }
    }

    fun adminResolveReport(context: Context, reportId: Int, dismiss: Boolean = false, note: String? = null) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                val body = ReportResolveRequest(
                    status = if (dismiss) "DISMISSED" else "RESOLVED",
                    adminNote = note
                )
                val updated = RetrofitClient.instance.adminResolveReport("Bearer $token", reportId, body)
                _adminReports.value = _adminReports.value.map { if (it.id == updated.id) updated else it }
                _eventFlow.emit(UiEvent.ShowToast(if (dismiss) "신고를 기각했습니다." else "신고를 처리했습니다."))
            } catch (e: Exception) {
                Log.e("ADMIN", "신고 처리 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("처리에 실패했습니다."))
            }
        }
    }

    // 🚀 일반 사용자: 신고 제출
    fun submitReport(
        context: Context,
        targetType: ReportTargetType,
        targetId: Int,
        reason: String,
        onSuccess: () -> Unit = {}
    ) {
        if (reason.isBlank()) {
            viewModelScope.launch { _eventFlow.emit(UiEvent.ShowToast("신고 사유를 입력해주세요.")) }
            return
        }
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) {
                    _eventFlow.emit(UiEvent.ShowToast("로그인이 필요합니다."))
                    return@launch
                }
                RetrofitClient.instance.createReport(
                    "Bearer $token",
                    ReportCreateRequest(targetType.value, targetId, reason.trim())
                )
                _eventFlow.emit(UiEvent.ShowToast("신고가 접수되었습니다."))
                onSuccess()
            } catch (e: Exception) {
                Log.e("REPORT", "신고 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("신고 접수에 실패했습니다."))
            }
        }
    }

    // 🚀 비밀번호 변경
    fun changePassword(
        context: Context,
        current: String,
        new: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (new.length < 4) { onError("새 비밀번호는 4자 이상이어야 합니다."); return }
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) { onError("로그인이 필요합니다."); return@launch }
                val res = RetrofitClient.instance.changePassword(
                    "Bearer $token",
                    PasswordChangeRequest(current, new)
                )
                if (res.isSuccessful) {
                    _eventFlow.emit(UiEvent.ShowToast("비밀번호가 변경되었습니다."))
                    onSuccess()
                } else if (res.code() == 401 || res.code() == 400) {
                    onError("현재 비밀번호가 일치하지 않습니다.")
                } else {
                    onError("변경 실패: ${res.code()}")
                }
            } catch (e: Exception) {
                Log.e("PASSWORD", "변경 실패: ${e.message}")
                onError("네트워크 오류가 발생했습니다.")
            }
        }
    }

    // 🚀 회원 탈퇴
    fun deleteMyAccount(
        context: Context,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) { onError("로그인이 필요합니다."); return@launch }
                val res = RetrofitClient.instance.deleteMyAccount(
                    "Bearer $token",
                    AccountDeleteRequest(password)
                )
                if (res.isSuccessful) {
                    AuthManager.clearAuthData(context)
                    _myProfile.value = null
                    _eventFlow.emit(UiEvent.ShowToast("계정이 삭제되었습니다."))
                    onSuccess()
                } else if (res.code() == 401 || res.code() == 400) {
                    onError("비밀번호가 일치하지 않습니다.")
                } else {
                    onError("탈퇴 실패: ${res.code()}")
                }
            } catch (e: Exception) {
                Log.e("DELETE_ACCOUNT", "실패: ${e.message}")
                onError("네트워크 오류가 발생했습니다.")
            }
        }
    }

    // 🚀 AI 자기소개 후보 (성공 시 콜백으로 전달)
    fun generateBioSuggestions(
        context: Context,
        keywords: String?,
        onResult: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) { onError("로그인이 필요합니다."); return@launch }
                val res = RetrofitClient.instance.generateBioSuggestions(
                    "Bearer $token",
                    BioSuggestionRequest(keywords?.ifBlank { null })
                )
                onResult(res.suggestions)
            } catch (e: Exception) {
                Log.e("AI_BIO", "후보 생성 실패: ${e.message}")
                onError("자기소개 후보를 가져오지 못했어요.")
            }
        }
    }

    // 🚀 인앱 알림 인박스
    private val _inboxNotifications = MutableStateFlow<List<NotificationDTO>>(emptyList())
    val inboxNotifications: StateFlow<List<NotificationDTO>> = _inboxNotifications.asStateFlow()

    fun fetchInboxNotifications(context: Context) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                _inboxNotifications.value = RetrofitClient.instance.getInboxNotifications("Bearer $token")
            } catch (e: Exception) {
                Log.e("INBOX", "조회 실패: ${e.message}")
            }
        }
    }

    fun markNotificationRead(context: Context, id: Int) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                val updated = RetrofitClient.instance.markNotificationRead("Bearer $token", id)
                _inboxNotifications.value = _inboxNotifications.value.map {
                    if (it.id == updated.id) updated else it
                }
            } catch (e: Exception) {
                Log.e("INBOX", "읽음 실패: ${e.message}")
            }
        }
    }

    fun markAllNotificationsRead(context: Context) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                RetrofitClient.instance.markAllNotificationsRead("Bearer $token")
                _inboxNotifications.value = _inboxNotifications.value.map { it.copy(isRead = true) }
            } catch (e: Exception) {
                Log.e("INBOX", "모두 읽음 실패: ${e.message}")
            }
        }
    }

    // 🚀 일정 출석 체크인
    fun checkInSchedule(context: Context, groupId: Int, scheduleId: Int) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) { _eventFlow.emit(UiEvent.ShowToast("로그인이 필요합니다.")); return@launch }
                val updated = RetrofitClient.instance.checkInSchedule("Bearer $token", groupId, scheduleId)
                _selectedGroupDetail.value = _selectedGroupDetail.value?.let { current ->
                    current.copy(schedules = current.schedules.map { if (it.id == updated.id) updated else it })
                }
                _eventFlow.emit(UiEvent.ShowToast("출석이 기록됐어요. 🎉"))
            } catch (e: Exception) {
                Log.e("CHECKIN", "체크인 실패: ${e.message}")
                val msg = if (e is retrofit2.HttpException && e.code() == 400) {
                    try {
                        val body = e.response()?.errorBody()?.string()
                        val map = com.google.gson.Gson().fromJson(body, Map::class.java)
                        map["detail"]?.toString() ?: "체크인 가능한 시간이 아니에요."
                    } catch (_: Exception) { "체크인 가능한 시간이 아니에요." }
                } else "체크인에 실패했습니다."
                _eventFlow.emit(UiEvent.ShowToast(msg))
            }
        }
    }

    // 🚀 방장용 모임 통계
    private val _groupStats = MutableStateFlow<GroupStatsDTO?>(null)
    val groupStats: StateFlow<GroupStatsDTO?> = _groupStats.asStateFlow()

    fun fetchGroupStats(context: Context, groupId: Int) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                _groupStats.value = RetrofitClient.instance.getGroupStats("Bearer $token", groupId)
            } catch (e: Exception) {
                Log.e("STATS", "통계 조회 실패: ${e.message}")
                _groupStats.value = null
            }
        }
    }

    fun clearGroupStats() {
        _groupStats.value = null
    }

    // 🚀 FCM 토큰 등록 (FirebaseMessagingService에서 호출)
    fun registerFcmToken(context: Context, fcmToken: String) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty() || fcmToken.isBlank()) return@launch
                RetrofitClient.instance.registerDeviceToken(
                    "Bearer $token",
                    DeviceTokenRequest(fcmToken, "android")
                )
            } catch (e: Exception) {
                Log.e("FCM", "토큰 등록 실패: ${e.message}")
            }
        }
    }

    fun adminDeleteUser(context: Context, userId: Int) {
        viewModelScope.launch {
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) return@launch
                val res = RetrofitClient.instance.adminDeleteUser("Bearer $token", userId)
                if (res.isSuccessful) {
                    _adminUsers.value = _adminUsers.value.filterNot { it.id == userId }
                    _eventFlow.emit(UiEvent.ShowToast("계정을 삭제했습니다."))
                } else {
                    _eventFlow.emit(UiEvent.ShowToast("삭제 실패: ${res.code()}"))
                }
            } catch (e: Exception) {
                Log.e("ADMIN", "유저 삭제 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("삭제에 실패했습니다."))
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
