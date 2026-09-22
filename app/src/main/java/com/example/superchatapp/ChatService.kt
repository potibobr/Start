package com.example.superchatapp

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ChatService : Service() {

    // ─── Статические поля ───
    // Они принадлежат классу, а не экземпляру.
    // MainActivity будет обращаться к ним напрямую.
    companion object {
        var isRunning = false                       // Запущен ли сервис
        var webSocketClient: ChatWebSocketClient? = null  // Наш WebSocket-клиент
    }

    // ─── onCreate: вызывается один раз при создании сервиса ───
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        // САМОЕ ГЛАВНОЕ: говорим системе «это foreground-сервис, вот уведомление»
        startForeground(1, createNotification())
    }

    // ─── onStartCommand: вызывается каждый раз при startService() ───
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY — если система убьёт сервис, она попробует перезапустить его
        return START_STICKY
    }

    // ─── onBind: нужен для Bound Service, нам не нужен ───
    override fun onBind(intent: Intent?): IBinder? = null

    // ─── onDestroy: вызывается при остановке сервиса ───
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        webSocketClient?.close()  // Аккуратно закрываем соединение
        webSocketClient = null
    }

    // ─── Создание уведомления для шторки ───
    private fun createNotification(): Notification {
        val channelId = "chat_service_channel"

        // С Android 8 нужен канал уведомлений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Чат работает",                    // Видимое имя канала
                NotificationManager.IMPORTANCE_LOW // Тихий режим (без звука)
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SuperChatApp")
            .setContentText("Подключение к чату активно")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setOngoing(true)  // Нельзя смахнуть — обязательно для foreground
            .build()
    }
}