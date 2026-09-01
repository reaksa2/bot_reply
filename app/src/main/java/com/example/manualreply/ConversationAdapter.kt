package com.example.manualreply

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView

class ConversationAdapter(
    private var items: List<Conversation>,
    private val onClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val initials: TextView = view.findViewById(R.id.initialsText)
        val groupIcon: ImageView = view.findViewById(R.id.groupIcon)
        val avatar: CircleImageView = view.findViewById(R.id.avatarImage)
        val name: TextView = view.findViewById(R.id.nameText)
        val preview: TextView = view.findViewById(R.id.previewText)
        val time: TextView = view.findViewById(R.id.timeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.displayName

        val prefix = if (item.lastDirection == "OUT") {
            "You: "
        } else if (item.isGroup && item.username.isNotBlank()) {
            "${item.username}: "
        } else {
            ""
        }
        holder.preview.text = prefix + item.lastMessage
        holder.time.text = item.lastTimestamp.take(16)

        holder.avatar.visibility = View.INVISIBLE
        holder.initials.visibility = View.GONE
        holder.groupIcon.visibility = View.GONE

        if (item.isGroup) {
            holder.groupIcon.visibility = View.VISIBLE
            val bg = holder.groupIcon.background.mutate() as GradientDrawable
            bg.setColor(AvatarUtil.colorFor(item.chatId))
        } else {
            holder.initials.visibility = View.VISIBLE
            holder.initials.text = AvatarUtil.initialFor(item.username)
            val bg = holder.initials.background.mutate() as GradientDrawable
            bg.setColor(AvatarUtil.colorFor(item.username))

            if (item.userId.isNotBlank()) {
                // check the cache first — only hit the network for people we've never resolved
                val cached = AvatarCache.get(item.userId)
                if (cached != null) {
                    loadAvatar(holder, cached)
                } else if (!AvatarCache.has(item.userId) && AvatarCache.markInFlight(item.userId)) {
                    Thread {
                        val photoUrl = ApiClient.fetchProfilePhotoUrl(item.userId)
                        AvatarCache.put(item.userId, photoUrl)
                        AvatarCache.clearInFlight(item.userId)
                        if (photoUrl != null) {
                            holder.avatar.post { loadAvatar(holder, photoUrl) }
                        }
                    }.start()
                }
            }
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun loadAvatar(holder: ViewHolder, url: String) {
        Glide.with(holder.avatar.context).load(url).circleCrop().into(holder.avatar)
        holder.avatar.visibility = View.VISIBLE
        holder.initials.visibility = View.INVISIBLE
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<Conversation>) {
        items = newItems
        notifyDataSetChanged()
    }
}
