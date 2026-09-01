package com.example.manualreply

data class ThreadMessage(
    val timestamp: String,
    val chatId: String,
    val chatType: String,
    val chatTitle: String,
    val userId: String,
    val username: String,
    val direction: String, // "IN" or "OUT"
    val text: String,
    val messageId: String,
    val type: String = "text", // "text", "photo", or "audio"
    val mediaUrl: String = ""
)
