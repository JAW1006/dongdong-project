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

    // 🚀 17. 마이페이지: 본인 프로필 조회
    @GET("users/me")
    suspend fun getMyProfile(
        @Header("Authorization") token: String
    ): MyProfile

    // 🚀 18. 프로필 부분 수정
    @PATCH("users/me")
    suspend fun updateMyProfile(
        @Header("Authorization") token: String,
        @Body body: ProfileUpdateRequest
    ): MyProfile

    // 🚀 19. 내가 가입한 모임 목록
    @GET("users/me/groups")
    suspend fun getMyGroups(
        @Header("Authorization") token: String
    ): List<HobbyGroup>

    // 🚀 20. 내가 참여하는 다가오는 일정
    @GET("users/me/schedules")
    suspend fun getMySchedules(
        @Header("Authorization") token: String
    ): List<MySchedule>

    // 🚀 21. 프로필 이미지 업로드
    @Multipart
    @POST("uploads/image")
    suspend fun uploadProfileImage(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): ImageUploadResponse

    // 🚀 22. 모임 정보 수정 (방장)
    @PATCH("groups/{group_id}")
    suspend fun updateGroup(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int,
        @Body body: GroupUpdateRequest
    ): HobbyGroup

    // 🚀 23. 모임 삭제 (방장)
    @DELETE("groups/{group_id}")
    suspend fun deleteGroup(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int
    ): Response<Unit>

    // 🚀 24. 모임 후기 작성/수정 (모임원, 1인 1리뷰)
    @POST("groups/{group_id}/reviews")
    suspend fun createReview(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int,
        @Body body: GroupReviewCreateRequest
    ): GroupReviewDTO

    // 🚀 25. 모임 후기 목록
    @GET("groups/{group_id}/reviews")
    suspend fun getReviews(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int
    ): List<GroupReviewDTO>

    // 🚀 26. 모임 후기 삭제
    @DELETE("groups/{group_id}/reviews/{review_id}")
    suspend fun deleteReview(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int,
        @Path("review_id") reviewId: Int
    ): Response<Unit>

    // 🚀 27. 채팅 이미지 업로드
    @Multipart
    @POST("chat/{group_id}/image")
    suspend fun uploadChatImage(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int,
        @Part file: MultipartBody.Part
    ): ChatMessageDTO

    // 🚀 28. 채팅 읽음 처리 (현재 그룹의 마지막 메시지까지 읽음으로)
    @POST("chat/{group_id}/read")
    suspend fun markChatRead(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int
    ): Response<Unit>

    // 🚀 29. 그룹별 안 읽음 메시지 카운트
    @GET("chat/unread")
    suspend fun getUnreadCounts(
        @Header("Authorization") token: String
    ): Map<String, Int>

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

    // 🚀 30. 관리자: 모든 모임 목록
    @GET("admin/groups")
    suspend fun adminListGroups(
        @Header("Authorization") token: String,
        @Query("search") search: String? = null
    ): List<AdminGroupRow>

    // 🚀 31. 관리자: 모임 강제 삭제
    @DELETE("admin/groups/{group_id}")
    suspend fun adminDeleteGroup(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int
    ): Response<Unit>

    // 🚀 32. 관리자: 모든 유저 목록
    @GET("admin/users")
    suspend fun adminListUsers(
        @Header("Authorization") token: String,
        @Query("search") search: String? = null
    ): List<AdminUserRow>

    // 🚀 33. 관리자: 유저 정지
    @POST("admin/users/{user_id}/ban")
    suspend fun adminBanUser(
        @Header("Authorization") token: String,
        @Path("user_id") userId: Int
    ): AdminUserRow

    // 🚀 34. 관리자: 유저 정지 해제
    @POST("admin/users/{user_id}/unban")
    suspend fun adminUnbanUser(
        @Header("Authorization") token: String,
        @Path("user_id") userId: Int
    ): AdminUserRow

    // 🚀 35. 관리자: 유저 계정 완전 삭제
    @DELETE("admin/users/{user_id}")
    suspend fun adminDeleteUser(
        @Header("Authorization") token: String,
        @Path("user_id") userId: Int
    ): Response<Unit>

    // 🚀 36. 신고 등록 (모임/유저/채팅)
    @POST("reports/")
    suspend fun createReport(
        @Header("Authorization") token: String,
        @Body body: ReportCreateRequest
    ): ReportDTO

    // 🚀 37. 관리자: 신고 목록
    @GET("admin/reports")
    suspend fun adminListReports(
        @Header("Authorization") token: String,
        @Query("status") status: String? = null
    ): List<ReportDTO>

    // 🚀 38. 관리자: 신고 처리
    @POST("admin/reports/{report_id}/resolve")
    suspend fun adminResolveReport(
        @Header("Authorization") token: String,
        @Path("report_id") reportId: Int,
        @Body body: ReportResolveRequest
    ): ReportDTO

    // 🚀 39. 비밀번호 변경
    @POST("users/me/password")
    suspend fun changePassword(
        @Header("Authorization") token: String,
        @Body body: PasswordChangeRequest
    ): Response<Unit>

    // 🚀 40. 회원 탈퇴 (본인)
    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun deleteMyAccount(
        @Header("Authorization") token: String,
        @Body body: AccountDeleteRequest
    ): Response<Unit>

    // 🚀 41. FCM 디바이스 토큰 등록
    @POST("users/me/device-token")
    suspend fun registerDeviceToken(
        @Header("Authorization") token: String,
        @Body body: DeviceTokenRequest
    ): Response<Unit>

    // 🚀 42. FCM 디바이스 토큰 해제
    @HTTP(method = "DELETE", path = "users/me/device-token", hasBody = true)
    suspend fun unregisterDeviceToken(
        @Header("Authorization") token: String,
        @Body body: DeviceTokenRequest
    ): Response<Unit>

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
