package com.example.dongdong

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dongdong.network.RetrofitClient
import com.example.dongdong.network.AuthManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    // 상세 페이지 데이터
    private val _selectedGroupDetail = MutableStateFlow<HobbyGroup?>(null)
    val selectedGroupDetail: StateFlow<HobbyGroup?> = _selectedGroupDetail.asStateFlow()

    // 현재 사용자의 방장 여부
    private val _isLeader = MutableStateFlow(false)
    val isLeader: StateFlow<Boolean> = _isLeader.asStateFlow()

    // 현재 사용자의 멤버 여부
    private val _isMember = MutableStateFlow(false)
    val isMember: StateFlow<Boolean> = _isMember.asStateFlow()

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
                val response = RetrofitClient.instance.getGroups()
                _groups.value = response
            } catch (e: Exception) {
                Log.e("FETCH_GROUPS_ERROR", "목록 조회 실패: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(user: UserRegisterRequest, onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.registerUser(user)
                if (response.id > 0) onSuccess(response.id) else onError("회원가입 실패")
            } catch (e: Exception) {
                onError("이미 존재하는 아이디이거나 서버 에러입니다.")
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
            // 1. 새로운 데이터를 가져오기 전 기존 상태 초기화 (중요!)
            _selectedGroupDetail.value = null
            _isLeader.value = false
            _isMember.value = false

            try {
                val token = AuthManager.getToken(context)
                val authHeader = if (token.isNotEmpty()) "Bearer $token" else ""

                val response = RetrofitClient.instance.getGroupDetail(authHeader, groupId)

                // 2. 서버 응답 로깅 강화 (로그인 유저의 실제 권한 확인용)
                Log.d("AUTH_CHECK", "서버 응답 - 방장여부: ${response.isLeader}, 가입여부: ${response.isMember}")

                _selectedGroupDetail.value = response.groupData
                _isLeader.value = response.isLeader
                _isMember.value = response.isMember

            } catch (e: Exception) {
                Log.e("FETCH_ERROR", "상세 페이지 로딩 실패: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("정보를 불러오지 못했습니다."))
            }
        }
    }

    /**
     * 🚀 [모임 가입하기]
     */
    fun joinGroup(context: Context, groupId: Int) {
        if (_isProcessing.value) return

        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val token = AuthManager.getToken(context)
                if (token.isEmpty()) {
                    _eventFlow.emit(UiEvent.ShowToast("로그인이 필요합니다."))
                    return@launch
                }

                val response = RetrofitClient.instance.joinGroup("Bearer $token", groupId)

                if (response.isSuccessful) {
                    _eventFlow.emit(UiEvent.ShowToast("모임 가입 성공! 🎉"))
                    // 가입 성공 후 즉시 상세 정보를 다시 불러와 UI 갱신
                    fetchGroupDetail(context, groupId)
                } else {
                    // 3. FastAPI의 에러 메시지(detail)를 추출하는 것이 좋지만,
                    // 우선은 간단하게 상태 코드로 메시지 분기
                    val errorMsg = if (response.code() == 400) "이미 가입된 멤버입니다." else "가입에 실패했습니다."
                    _eventFlow.emit(UiEvent.ShowToast(errorMsg))
                }
            } catch (e: Exception) {
                Log.e("JOIN_ERROR", "가입 에러: ${e.message}")
                _eventFlow.emit(UiEvent.ShowToast("네트워크 연결을 확인해주세요."))
            } finally {
                _isProcessing.value = false // 🚀 에러가 나도 무조건 버튼 잠금 해제
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

    fun selectCategory(category: HobbyCategory) {
        _selectedCategory.value = category
    }
}
