package com.example.superchatapp

import okhttp3.*
import java.util.concurrent.TimeUnit

class ChatWebSocketClient(
    private val serverUrl: String,
    private val onMessageReceived: (String) -> Unit,
    private val onConnectionOpened: () -> Unit,
    private val onConnectionClosed: (String) -> Unit
) : WebSocketListener() {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Важно для WebSocket
        .build()
    private var webSocket: WebSocket? = null

    fun connect() {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, this)
    }

    fun sendMessage(message: String) {
        webSocket?.send(message)
    }

    fun close() {
        webSocket?.close(1000, "User closed app")
        client.dispatcher.executorService.shutdown()
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        onConnectionOpened()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        onMessageReceived(text)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        android.util.Log.e("WEBSOCKET", "Ошибка подключения : ${t.message}" ,t)
        onConnectionClosed("Connection failed: ${t.message}")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        onConnectionClosed("Connection closed: $reason")
    }
}