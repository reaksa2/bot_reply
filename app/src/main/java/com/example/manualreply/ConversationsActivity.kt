package com.example.manualreply

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class ConversationsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var emptyText: View
    private lateinit var adapter: ConversationAdapter

    private var listenerRegistration: ListenerRegistration? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    // remembers the latest timestamp we've already seen per chat, so we only
    // notify for messages that arrive AFTER the screen first loads — not the
    // whole existing history on first open
    private val lastSeenTimestamps = HashMap<String, Long>()
    private var isFirstSnapshot = true

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* if denied, notifications simply won't show — handled gracefully in NotificationHelper */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversations)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        recyclerView = findViewById(R.id.recyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        emptyText = findViewById(R.id.emptyText)

        findViewById<android.widget.ImageButton>(R.id.accountsButton).setOnClickListener {
            startActivity(Intent(this, AccountsActivity::class.java))
        }

        adapter = ConversationAdapter(emptyList()) { conversation ->
            val account = AccountManager.getActiveAccount(this)
            val intent = Intent(this, ThreadActivity::class.java)
            intent.putExtra("chatId", conversation.chatId)
            intent.putExtra("chatType", conversation.chatType)
            intent.putExtra("chatTitle", conversation.chatTitle)
            intent.putExtra("userId", conversation.userId)
            intent.putExtra("username", conversation.username)
            intent.putExtra("botId", account?.botId ?: "")
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // manual refresh just re-triggers auth check; the listener stays live either way
        swipeRefresh.setOnRefreshListener { swipeRefresh.isRefreshing = false }
    }

    override fun onResume() {
        super.onResume()

        if (AccountManager.getActiveAccount(this) == null) {
            startActivity(Intent(this, AccountsActivity::class.java))
            return
        }

        FirebaseAuthHelper.ensureSignedIn {
            attachListener()
        }
    }

    override fun onPause() {
        super.onPause()
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    private fun attachListener() {
        listenerRegistration?.remove()
        isFirstSnapshot = true

        val account = AccountManager.getActiveAccount(this) ?: return

        listenerRegistration = FirebaseFirestore.getInstance()
            .collection("bots").document(account.botId).collection("conversations")
            .orderBy("lastTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val conversations = snapshot.documents.mapNotNull { doc ->
                    val ts = doc.getDate("lastTimestamp")
                    Conversation(
                        chatId = doc.id,
                        chatType = doc.getString("chatType") ?: "",
                        chatTitle = doc.getString("chatTitle") ?: "",
                        userId = doc.getString("userId") ?: "",
                        username = doc.getString("username") ?: "",
                        lastMessage = doc.getString("lastMessage") ?: "",
                        lastDirection = doc.getString("lastDirection") ?: "",
                        lastTimestamp = if (ts != null) dateFormat.format(ts) else ""
                    )
                }

                if (!isFirstSnapshot) {
                    for (doc in snapshot.documents) {
                        val ts = doc.getDate("lastTimestamp")?.time ?: continue
                        val direction = doc.getString("lastDirection") ?: ""
                        val previouslySeen = lastSeenTimestamps[doc.id]

                        if (direction == "IN" && (previouslySeen == null || ts > previouslySeen)) {
                            val chatTitle = doc.getString("chatTitle") ?: ""
                            val username = doc.getString("username") ?: ""
                            val isGroup = doc.getString("chatType") in listOf("group", "supergroup")
                            val displayName = if (isGroup && chatTitle.isNotBlank()) chatTitle else "@$username"

                            NotificationHelper.showNewMessage(
                                this,
                                displayName,
                                doc.getString("lastMessage") ?: "",
                                doc.id,
                                account.botId,
                                doc.getString("chatType") ?: "",
                                chatTitle,
                                doc.getString("userId") ?: "",
                                username
                            )
                        }
                    }
                }

                snapshot.documents.forEach { doc ->
                    val ts = doc.getDate("lastTimestamp")?.time
                    if (ts != null) lastSeenTimestamps[doc.id] = ts
                }
                isFirstSnapshot = false

                adapter.updateItems(conversations)
                emptyText.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE
            }
    }
}
