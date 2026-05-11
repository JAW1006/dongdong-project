package com.example.dongdong.network

import com.example.dongdong.HobbyGroup
import com.example.dongdong.GroupDetailResponse
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*
import com.example.dongdong.*

interface ApiService {
    // 1. 모임 목록 조회
    @GET("groups/")
    suspend fun getGroups(): List<HobbyGroup>

    // 2. 모임 상세 조회 (정확한 경로 매핑)
    @GET("groups/{group_id}")
    suspend fun getGroupDetail(
        @Header("Authorization") token: String?,
        @Path("group_id") groupId: Int
    ): GroupDetailResponse

    // 3. 모임 가입하기
    @POST("groups/{group_id}/join")
    suspend fun joinGroup(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int
    ): Response<Unit>

    // 4. 모임 탈퇴하기 (일반 멤버용)
    @POST("groups/{group_id}/leave")
    suspend fun leaveGroup(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int
    ): Response<Unit>

    // 5. 멤버 강퇴하기 (방장 전용)
    @POST("groups/{group_id}/kick/{user_id}")
    suspend fun kickMember(
        @Header("Authorization") token: String,
        @Path("group_id") groupId: Int,
        @Path("user_id") userId: Int
    ): Response<Unit>

    // 6. 회원가입
    @POST("users/signup")
    suspend fun registerUser(@Body user: UserRegisterRequest): RegisterResponse

    // 7. 로그인
    @POST("users/login")
    suspend fun login(@Body request: UserLoginRequest): LoginResponse
}

