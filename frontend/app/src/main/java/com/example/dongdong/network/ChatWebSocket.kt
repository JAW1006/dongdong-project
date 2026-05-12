package com.example.dongdong.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.dongdong.ChatMessageDTO
import com.google.gson.Gson
import okhttp3.*
import java.util.concurrent.TimeUnit

class ChatWebSocket(
    private val groupId: Int,
    private val token: String,
    private val onMessageReceived: (ChatMessageDTO) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS) // 연결 유지용 ping
        .build()
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun connect() {
        val url = "ws://10.0.2.2:8000/chat/ws/$groupId?token=$token"

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("ChatWS", "WebSocket 연결됨 (group: $groupId)")
                mainHandler.post { onConnectionChanged(true) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = gson.fromJson(text, ChatMessageDTO::class.java)
                    mainHandler.post { onMessageReceived(message) }
                } catch (e: Exception) {
                    Log.e("ChatWS", "메시지 파싱 실패: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("ChatWS", "WebSocket 닫히는 중: $reason")
                webSocket.close(1000, null)
                mainHandler.post { onConnectionChanged(false) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("ChatWS", "WebSocket 에러: ${t.message}")
                mainHandler.post { onConnectionChanged(false) }
            }
        })
    }

    fun sendMessage(message: String) {
        val json = gson.toJson(mapOf("message" to message))
        webSocket?.send(json)
    }

    fun disconnect() {
        webSocket?.close(1000, "사용자 퇴장")
        webSocket = null
    }
}
