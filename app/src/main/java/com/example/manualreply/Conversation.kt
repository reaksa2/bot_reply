package com.example.manualreply

data class Conversation(
    val chatId: String,
    val chatType: String, // "private", "group", or "supergroup"
    val chatTitle: String, // group name, blank for private chats
    val userId: String,
    val username: String,
    val lastMessage: String,
    val lastDirection: String,
    val lastTimestamp: String
) {
    val isGroup: Boolean
        get() = chatType == "group" || chatType == "supergroup"

    // What to show as the conversation name in the list/header
    val displayName: String
        get() = if (isGroup) {
            if (chatTitle.isNotBlank()) chatTitle else "Group"
        } else {
            "@$username"
        }
}
