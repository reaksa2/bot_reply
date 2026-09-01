package com.example.manualreply

import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ThreadAdapter(
    private var items: List<ThreadMessage>,
    private val isGroup: Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_IN = 0
        const val TYPE_OUT = 1
    }

    class InViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarContainer: FrameLayout = view.findViewById(R.id.avatarContainer)
        val senderInitials: TextView = view.findViewById(R.id.senderInitials)
        val senderAvatar: de.hdodenhof.circleimageview.CircleImageView = view.findViewById(R.id.senderAvatar)
        val sender: TextView = view.findViewById(R.id.senderText)
        val message: TextView = view.findViewById(R.id.messageText)
        val time: TextView = view.findViewById(R.id.timeText)
        val photo: ImageView = view.findViewById(R.id.photoImage)
        val audioContainer: LinearLayout = view.findViewById(R.id.audioContainer)
        val audioPlayButton: ImageButton = view.findViewById(R.id.audioPlayButton)
        val videoContainer: LinearLayout = view.findViewById(R.id.videoContainer)
    }

    class OutViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val message: TextView = view.findViewById(R.id.messageText)
        val time: TextView = view.findViewById(R.id.timeText)
        val photo: ImageView = view.findViewById(R.id.photoImage)
        val audioContainer: LinearLayout = view.findViewById(R.id.audioContainer)
        val audioPlayButton: ImageButton = view.findViewById(R.id.audioPlayButton)
        val videoContainer: LinearLayout = view.findViewById(R.id.videoContainer)
    }

    // tracks a currently-playing player so tapping another button stops the
    // previous one, and resets the icon when playback finishes
    private var activePlayer: MediaPlayer? = null
    private var activeButton: ImageButton? = null

    private fun togglePlayback(button: ImageButton, url: String) {
        if (activeButton == button && activePlayer != null) {
            activePlayer?.stop()
            activePlayer?.release()
            activePlayer = null
            button.setImageResource(android.R.drawable.ic_media_play)
            activeButton = null
            return
        }

        activePlayer?.stop()
        activePlayer?.release()
        activeButton?.setImageResource(android.R.drawable.ic_media_play)

        try {
            val player = MediaPlayer()
            player.setDataSource(url)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener {
                button.setImageResource(android.R.drawable.ic_media_play)
                it.release()
                if (activePlayer == it) activePlayer = null
            }
            player.prepareAsync()
            activePlayer = player
            activeButton = button
            button.setImageResource(android.R.drawable.ic_media_pause)
        } catch (e: Exception) {
            // playback failed silently — button just stays as "play"
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].direction == "OUT") TYPE_OUT else TYPE_IN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_OUT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bubble_out, parent, false)
            OutViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bubble_in, parent, false)
            InViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        when (holder) {
            is InViewHolder -> {
                bindMedia(item, holder.message, holder.photo, holder.audioContainer, holder.audioPlayButton, holder.videoContainer)
                holder.time.text = item.timestamp.take(16)

                if (isGroup && item.username.isNotBlank()) {
                    holder.sender.text = item.username
                    holder.sender.visibility = View.VISIBLE
                    holder.avatarContainer.visibility = View.VISIBLE
                    holder.senderAvatar.visibility = View.INVISIBLE
                    holder.senderInitials.visibility = View.VISIBLE
                    holder.senderInitials.text = AvatarUtil.initialFor(item.username)
                    val bg = holder.senderInitials.background.mutate() as android.graphics.drawable.GradientDrawable
                    bg.setColor(AvatarUtil.colorFor(item.username))

                    if (item.userId.isNotBlank()) {
                        val cached = AvatarCache.get(item.userId)
                        if (cached != null) {
                            Glide.with(holder.senderAvatar.context).load(cached).circleCrop().into(holder.senderAvatar)
                            holder.senderAvatar.visibility = View.VISIBLE
                            holder.senderInitials.visibility = View.INVISIBLE
                        } else if (!AvatarCache.has(item.userId) && AvatarCache.markInFlight(item.userId)) {
                            Thread {
                                val photoUrl = ApiClient.fetchProfilePhotoUrl(item.userId)
                                AvatarCache.put(item.userId, photoUrl)
                                AvatarCache.clearInFlight(item.userId)
                                if (photoUrl != null) {
                                    holder.senderAvatar.post {
                                        Glide.with(holder.senderAvatar.context)
                                            .load(photoUrl)
                                            .circleCrop()
                                            .into(holder.senderAvatar)
                                        holder.senderAvatar.visibility = View.VISIBLE
                                        holder.senderInitials.visibility = View.INVISIBLE
                                    }
                                }
                            }.start()
                        }
                    }
                } else {
                    holder.sender.visibility = View.GONE
                    holder.avatarContainer.visibility = View.GONE
                }
            }
            is OutViewHolder -> {
                bindMedia(item, holder.message, holder.photo, holder.audioContainer, holder.audioPlayButton, holder.videoContainer)
                holder.time.text = item.timestamp.take(16)
            }
        }
    }

    private fun bindMedia(
        item: ThreadMessage,
        messageText: TextView,
        photoView: ImageView,
        audioContainer: LinearLayout,
        audioPlayButton: ImageButton,
        videoContainer: LinearLayout
    ) {
        photoView.visibility = View.GONE
        audioContainer.visibility = View.GONE
        videoContainer.visibility = View.GONE
        messageText.visibility = View.VISIBLE
        messageText.text = item.text

        when (item.type) {
            "photo" -> {
                if (item.mediaUrl.isNotBlank()) {
                    photoView.visibility = View.VISIBLE
                    Glide.with(photoView.context).load(item.mediaUrl).into(photoView)
                    photoView.setOnClickListener {
                        val intent = android.content.Intent(photoView.context, ImageViewerActivity::class.java)
                        intent.putExtra("imageUrl", item.mediaUrl)
                        photoView.context.startActivity(intent)
                    }
                    // show the real caption below the image if one was sent, otherwise hide it
                    if (item.text.isNotBlank()) {
                        messageText.visibility = View.VISIBLE
                        messageText.text = item.text
                    } else {
                        messageText.visibility = View.GONE
                    }
                }
            }
            "video" -> {
                if (item.mediaUrl.isNotBlank()) {
                    videoContainer.visibility = View.VISIBLE
                    videoContainer.setOnClickListener {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            intent.setDataAndType(android.net.Uri.parse(item.mediaUrl), "video/mp4")
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            videoContainer.context.startActivity(intent)
                        } catch (e: Exception) {
                            // no video player app available — silently ignored
                        }
                    }
                    if (item.text.isNotBlank()) {
                        messageText.visibility = View.VISIBLE
                        messageText.text = item.text
                    } else {
                        messageText.visibility = View.GONE
                    }
                }
            }
            "audio" -> {
                if (item.mediaUrl.isNotBlank()) {
                    audioContainer.visibility = View.VISIBLE
                    messageText.visibility = View.GONE
                    audioPlayButton.setImageResource(
                        if (activeButton == audioPlayButton) android.R.drawable.ic_media_pause
                        else android.R.drawable.ic_media_play
                    )
                    audioPlayButton.setOnClickListener { togglePlayback(audioPlayButton, item.mediaUrl) }
                }
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ThreadMessage>) {
        items = newItems
        notifyDataSetChanged()
    }
}
