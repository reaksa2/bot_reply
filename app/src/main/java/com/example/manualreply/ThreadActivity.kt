package com.example.manualreply

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import de.hdodenhof.circleimageview.CircleImageView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class ThreadActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var replyInput: EditText
    private lateinit var sendButton: Button
    private lateinit var adapter: ThreadAdapter

    private lateinit var chatId: String
    private lateinit var chatType: String
    private lateinit var chatTitle: String
    private lateinit var username: String
    private lateinit var userId: String
    private lateinit var botId: String
    private var isGroup = false

    private var listenerRegistration: ListenerRegistration? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var isRecording = false

    private val mediaPicker = registerForActivityResult(
        object : ActivityResultContracts.GetContent() {
            override fun createIntent(context: android.content.Context, input: String): android.content.Intent {
                val intent = super.createIntent(context, "*/*")
                intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                return intent
            }
        }
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = contentResolver.getType(uri) ?: ""
            if (mimeType.startsWith("video/")) {
                sendVideo(uri)
            } else {
                sendImage(uri)
            }
        }
    }

    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else Toast.makeText(this, "Microphone permission needed", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)

        chatId = intent.getStringExtra("chatId") ?: ""
        chatType = intent.getStringExtra("chatType") ?: ""
        chatTitle = intent.getStringExtra("chatTitle") ?: ""
        username = intent.getStringExtra("username") ?: ""
        userId = intent.getStringExtra("userId") ?: ""
        botId = intent.getStringExtra("botId") ?: ""
        isGroup = chatType == "group" || chatType == "supergroup"

        val displayName = if (isGroup) {
            if (chatTitle.isNotBlank()) chatTitle else "Group"
        } else {
            "@$username"
        }
        findViewById<TextView>(R.id.nameText).text = displayName
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }

        val initials = findViewById<TextView>(R.id.initialsText)
        val avatar = findViewById<CircleImageView>(R.id.avatarImage)

        if (isGroup) {
            initials.text = displayName.take(1).uppercase()
            val bg = initials.background.mutate() as GradientDrawable
            bg.setColor(AvatarUtil.colorFor(chatId))
            initials.visibility = View.VISIBLE
            avatar.visibility = View.INVISIBLE
        } else {
            initials.text = AvatarUtil.initialFor(username)
            val bg = initials.background.mutate() as GradientDrawable
            bg.setColor(AvatarUtil.colorFor(username))

            if (userId.isNotBlank()) {
                val cached = AvatarCache.get(userId)
                if (cached != null) {
                    Glide.with(avatar.context).load(cached).circleCrop().into(avatar)
                    avatar.visibility = View.VISIBLE
                    initials.visibility = View.INVISIBLE
                } else if (AvatarCache.markInFlight(userId)) {
                    Thread {
                        val photoUrl = ApiClient.fetchProfilePhotoUrl(userId)
                        AvatarCache.put(userId, photoUrl)
                        AvatarCache.clearInFlight(userId)
                        if (photoUrl != null) {
                            runOnUiThread {
                                Glide.with(avatar.context).load(photoUrl).circleCrop().into(avatar)
                                avatar.visibility = View.VISIBLE
                                initials.visibility = View.INVISIBLE
                            }
                        }
                    }.start()
                }
            }
        }

        recyclerView = findViewById(R.id.recyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        replyInput = findViewById(R.id.replyInput)
        sendButton = findViewById(R.id.sendButton)

        adapter = ThreadAdapter(emptyList(), isGroup)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { swipeRefresh.isRefreshing = false }
        sendButton.setOnClickListener { sendReply() }

        findViewById<ImageButton>(R.id.attachButton).setOnClickListener {
            mediaPicker.launch("*/*")
        }

        findViewById<ImageButton>(R.id.micButton).setOnClickListener {
            if (isRecording) {
                stopRecordingAndSend()
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    startRecording()
                } else {
                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FirebaseAuthHelper.ensureSignedIn { attachListener() }
    }

    override fun onPause() {
        super.onPause()
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    private var hasScrolledOnce = false

    private fun attachListener() {
        listenerRegistration?.remove()

        listenerRegistration = FirebaseFirestore.getInstance()
            .collection("bots").document(botId).collection("conversations").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val messages = snapshot.documents.mapNotNull { doc ->
                    val ts = doc.getDate("timestamp")
                    ThreadMessage(
                        timestamp = if (ts != null) dateFormat.format(ts) else "",
                        chatId = chatId,
                        chatType = chatType,
                        chatTitle = chatTitle,
                        userId = doc.getString("userId") ?: "",
                        username = doc.getString("username") ?: "",
                        direction = doc.getString("direction") ?: "",
                        text = doc.getString("text") ?: "",
                        messageId = doc.getString("messageId") ?: "",
                        type = doc.getString("type") ?: "text",
                        mediaUrl = doc.getString("mediaUrl") ?: ""
                    )
                }

                swipeRefresh.isRefreshing = false
                adapter.updateItems(messages)
                if (messages.isNotEmpty() && !hasScrolledOnce) {
                    hasScrolledOnce = true
                    recyclerView.scrollToPosition(messages.size - 1)
                } else if (messages.isNotEmpty()) {
                    // auto-scroll to bottom on new incoming messages too
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
    }

    // ---- Writes the outgoing message directly to Firestore.
    // Uses merge() with ONLY the preview fields, so it never overwrites
    // the client's real identity (username/chatTitle) with "admin" ----
    private fun logOutgoingToFirestore(previewText: String, type: String = "text", mediaUrl: String = "") {
        val db = FirebaseFirestore.getInstance()
        val convRef = db.collection("bots").document(botId).collection("conversations").document(chatId)

        convRef.set(
            mapOf(
                "lastMessage" to previewText,
                "lastDirection" to "OUT",
                "lastTimestamp" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )

        convRef.collection("messages").add(
            mapOf(
                "direction" to "OUT",
                "text" to previewText,
                "username" to "admin",
                "userId" to "",
                "messageId" to "",
                "type" to type,
                "mediaUrl" to mediaUrl,
                "timestamp" to FieldValue.serverTimestamp()
            )
        )
    }

    private fun sendReply() {
        val text = replyInput.text.toString().trim()
        if (text.isEmpty()) return

        sendButton.isEnabled = false
        Thread {
            try {
                val sent = ApiClient.sendTelegramReply(chatId, text)
                if (sent) logOutgoingToFirestore(text)
                runOnUiThread {
                    sendButton.isEnabled = true
                    if (sent) {
                        replyInput.setText("")
                    } else {
                        Toast.makeText(this, "Telegram rejected the message", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    sendButton.isEnabled = true
                    Toast.makeText(this, "Error sending: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun sendImage(uri: Uri) {
        Toast.makeText(this, "Sending photo...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val mediaUrl = ApiClient.sendTelegramPhoto(chatId, this, uri)
                if (mediaUrl != null) {
                    logOutgoingToFirestore("📷 Photo", type = "photo", mediaUrl = mediaUrl)
                }
                runOnUiThread {
                    if (mediaUrl == null) Toast.makeText(this, "Failed to send photo", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun sendVideo(uri: Uri) {
        Toast.makeText(this, "Sending video...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val mediaUrl = ApiClient.sendTelegramVideo(chatId, this, uri)
                if (mediaUrl != null) {
                    logOutgoingToFirestore("🎬 Video", type = "video", mediaUrl = mediaUrl)
                }
                runOnUiThread {
                    if (mediaUrl == null) Toast.makeText(this, "Failed to send video", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun startRecording() {
        val micButton = findViewById<ImageButton>(R.id.micButton)
        try {
            val file = File(cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            recordingFile = file

            @Suppress("DEPRECATION")
            val recorder = if (android.os.Build.VERSION.SDK_INT >= 31) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            isRecording = true
            micButton.setColorFilter(android.graphics.Color.RED)
            Toast.makeText(this, "Recording... tap mic again to send", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't start recording: ${e.message}", Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    private fun stopRecordingAndSend() {
        val micButton = findViewById<ImageButton>(R.id.micButton)
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            // recording may have been too short — handled below
        }
        mediaRecorder = null
        isRecording = false
        micButton.clearColorFilter()

        val file = recordingFile
        if (file == null || !file.exists() || file.length() == 0L) {
            Toast.makeText(this, "Recording too short", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Sending voice message...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val mediaUrl = ApiClient.sendTelegramAudio(chatId, file)
                if (mediaUrl != null) {
                    logOutgoingToFirestore("🎤 Voice message", type = "audio", mediaUrl = mediaUrl)
                }
                runOnUiThread {
                    if (mediaUrl == null) Toast.makeText(this, "Failed to send voice message", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            } finally {
                file.delete()
            }
        }.start()
    }
}
