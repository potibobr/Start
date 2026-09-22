package com.example.superchatapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper


class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: Button
    private lateinit var adapter: MessageAdapter

    private var username: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        recyclerView = findViewById(R.id.recyclerViewMessages)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)

        adapter = MessageAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Запрашиваем имя пользователя при запуске
        showNameDialog()
    }

    private fun showNameDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Введите ваше имя")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                username = input.text.toString().ifBlank { "Аноним" }
                connectToServer()
            }
            .show()
    }

    private fun connectToServer() {
        // ─── 1. Запускаем сервис ───
        val serviceIntent = Intent(this, ChatService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)  // Android 8+ требует именно этот вызов
        } else {
            startService(serviceIntent)             // Для старых версий
        }

        // ─── 2. Создаём WebSocket-клиент в СЕРВИСЕ ───
        // Небольшая задержка, чтобы сервис успел создаться
        Handler(Looper.getMainLooper()).postDelayed({
            val serverUrl = "ws://192.168.1.111:8765"  // ← Ваш IP

            ChatService.webSocketClient = ChatWebSocketClient(
                serverUrl,
                onMessageReceived = { message ->
                    runOnUiThread {
                        adapter.addMessage(message)
                        recyclerView.scrollToPosition(adapter.itemCount - 1)
                    }
                },
                onConnectionOpened = {
                    runOnUiThread {
                        // Первое сообщение — наше имя
                        ChatService.webSocketClient?.sendMessage(username)
                    }
                },
                onConnectionClosed = { reason ->
                    runOnUiThread {
                        Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
                    }
                }
            )
            ChatService.webSocketClient?.connect()
        }, 500)  // 500 миллисекунд — сервис успеет создать уведомление

        // ─── 3. Кнопка отправки ───
        buttonSend.setOnClickListener {
            val message = editTextMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                // Отправляем через сервис, а не через локальную переменную
                ChatService.webSocketClient?.sendMessage(message)
                adapter.addMessage("[$username]: $message")
                recyclerView.scrollToPosition(adapter.itemCount - 1)
                editTextMessage.text.clear()
            }
        }
    }
}