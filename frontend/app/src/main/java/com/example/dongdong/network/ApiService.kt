package com.example.dongdong.network

import com.example.dongdong.HobbyGroup
import com.example.dongdong.GroupDetailResponse
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import com.example.dongdong.*

interface ApiService {
    // 1. 모임 목록 조회 (검색 + 지역 필터 + 정렬)
    @GET("groups/")
    suspend fun getGroups(
        @Query("search") search: String? = null,
        @Query("location") location: String? = null,
        @Query("sort") sort: String? = null
    ): List<HobbyGroup>

    // 2. 모임 상세 조회 (hasPendingRequest 필드 포함됨)
    @GET("groups/{group_id}")
    suspend fun getGroupDetail(
        @Header("Authorization") token: String?,
        @Path("group_id") groupId: Int
    ): GroupDetailResponse

    // 3. 모임 가입 신청하기 (즉시 가입에서 신청으로 변경)
    @POST("groups/{group_id}/apply")
    suspend fun applyToGroup(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int
    ): Response<Unit>

    // 4. 모임 탈퇴하기
    @POST("groups/{group_id}/leave")
    suspend fun leaveGroup(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int
    ): Response<Unit>

    // 5. 멤버 강퇴하기
    @POST("groups/{group_id}/kick/{user_id}")
    suspend fun kickMember(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int,
        @Path("user_id") userId: Int
    ): Response<Unit>

    // 🚀 6. 알림: 대기 중인 가입 신청 목록 조회
    @GET("groups/requests/pending")
    suspend fun getPendingRequests(
        @Header("Authorization") token: String
    ): List<JoinRequestDTO>

    // 🚀 7. 알림: 신청 수락 또는 거절
    @POST("groups/requests/{request_id}/respond")
    suspend fun respondToRequest(
        @Header("Authorization") token: String,
        @Path("request_id") requestId: Int,
        @Query("accept") accept: Boolean
    ): Response<Unit>

    // 8. 회원가입
    @POST("users/signup")
    suspend fun registerUser(@Body user: UserRegisterRequest): RegisterResponse

    // 9. 로그인
    @POST("users/login")
    suspend fun login(@Body request: UserLoginRequest): LoginResponse

    // 10. 채팅: 이전 메시지 조회
    @GET("chat/{group_id}/messages")
    suspend fun getChatMessages(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int,
        @Query("limit") limit: Int = 50,
        @Query("before_id") beforeId: Long? = null
    ): List<ChatMessageDTO>

    // 🚀 12. 프로필 저장 (회원가입 직후)
    @PUT("users/me/profile-setup")
    suspend fun setupProfile(
        @Header("Authorization") token: String,
        @Body body: ProfileSetupRequest
    ): Response<Unit>

    // 🚀 13. AI 모임 추천
    @GET("ai/recommendations")
    suspend fun getRecommendations(
        @Header("Authorization") token: String,
        @Query("top_n") topN: Int = 3
    ): RecommendationListResponse

    // 🚀 14. 모임 일정 추가 (모임장 전용)
    @POST("groups/{group_id}/schedules")
    suspend fun createSchedule(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int,
        @Body body: ScheduleCreateRequest
    ): Schedule

    // 🚀 15. 모임 일정 삭제 (모임장 전용)
    @DELETE("groups/{group_id}/schedules/{schedule_id}")
    suspend fun deleteSchedule(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int,
        @Path("schedule_id") scheduleId: Int
    ): Response<Unit>

    // 🚀 16. 일정 참여/취소 토글 (모임원 전용)
    @POST("groups/{group_id}/schedules/{schedule_id}/attend")
    suspend fun toggleAttendance(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int,
        @Path("schedule_id") scheduleId: Int
    ): Schedule

    // 11. 모임 생성 (이미지 업로드 지원)
    @Multipart
    @POST("groups/")
    suspend fun createGroup(
        @Header("Authorization") token: String,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody,
        @Part("location") location: RequestBody,
        @Part("hobby_id") hobbyId: RequestBody,
        @Part("leader_id") leaderId: RequestBody,
        @Part("tags") tags: RequestBody,
        @Part image: MultipartBody.Part? = null
    ): HobbyGroup
}
