package com.example.dongdong.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // 서버 호스트:포트 (Retrofit + WebSocket 공통).
    // - Android 에뮬레이터: "10.0.2.2:8000"
    // - 실기기(같은 Wi-Fi): 맥 LAN IP (예: "10.200.43.193:8000")
    const val SERVER_HOST = "10.200.43.193:8000"
    private const val BASE_URL = "http://$SERVER_HOST/"

    // AI 추천(Gemini 호출 포함) 응답이 10초를 넘길 수 있어 타임아웃 확장
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}
