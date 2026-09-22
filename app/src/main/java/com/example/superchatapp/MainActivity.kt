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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: Button
    private lateinit var adapter: MessageAdapter
    private var currentToken: String = ""
    private var username: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ─── 1. СНАЧАЛА находим все UI-элементы ───
        recyclerView = findViewById(R.id.recyclerViewMessages)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)

        // ─── 2. Настраиваем RecyclerView ───
        adapter = MessageAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // ─── 3. ТЕПЕРЬ можно вешать слушатели на editTextMessage ───
        editTextMessage.setOnLongClickListener {
            showRenameDialog()
            true
        }

        // ─── 4. Обработка клавиатуры (insets) ───
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layoutInput)) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                maxOf(imeInsets.bottom, systemBars.bottom)
            )
            insets
        }

        // ─── 5. Запрос разрешения на уведомления (Android 13+) ───
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        // ─── 6. ТОЛЬКО ТЕПЕРЬ логика входа ───
        val savedToken = TokenStorage.getToken(this)
        if (savedToken != null) {
            username = TokenStorage.getName(this) ?: "Пользователь"
            currentToken = savedToken
            connectToServer()
        } else {
            showNameDialog()
        }
    }

    private fun showRenameDialog() {
        val input = EditText(this)
        input.setText(username)
        AlertDialog.Builder(this)
            .setTitle("Новое имя")
            .setView(input)
            .setPositiveButton("Сменить") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != username) {
                    ChatService.webSocketClient?.sendMessage("RENAME:$newName")
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
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
                        when {
                            message.startsWith("TOKEN:") -> {
                                // Сервер выдал новый токен — сохраняем
                                val token = message.removePrefix("TOKEN:")
                                currentToken = token
                                TokenStorage.saveToken(this, token)
                                TokenStorage.saveName(this, username)
                                adapter.addMessage("*** Добро пожаловать, $username! ***")
                            }
                            message.startsWith("HELLO:") -> {
                                // Возвращение по токену
                                val name = message.removePrefix("HELLO:")
                                username = name
                                TokenStorage.saveName(this, name)
                                adapter.addMessage("*** С возвращением, $name! ***")
                            }
                            message.startsWith("RENAMED:") -> {
                                val name = message.removePrefix("RENAMED:")
                                username = name
                                TokenStorage.saveName(this, name)
                                adapter.addMessage("*** Имя изменено на $name ***")
                            }
                            message.startsWith("ERROR:") -> {
                                val err = message.removePrefix("ERROR:")
                                Toast.makeText(this, "Ошибка: $err", Toast.LENGTH_LONG).show()
                                // Если токен просрочен/не найден — сбрасываем и просим имя заново
                                if (err.contains("Токен")) {
                                    TokenStorage.clear(this)
                                    showNameDialog()
                                }
                            }
                            else -> {
                                adapter.addMessage(message)
                                recyclerView.scrollToPosition(adapter.itemCount - 1)
                            }
                        }
                    }
                },
                onConnectionOpened = {
                    val token = TokenStorage.getToken(this@MainActivity)
                    if (token != null) {
                        // Повторный вход
                        ChatService.webSocketClient?.sendMessage("AUTH:$token")
                    } else {
                        // Первая регистрация
                        ChatService.webSocketClient?.sendMessage("REGISTER:$username")
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