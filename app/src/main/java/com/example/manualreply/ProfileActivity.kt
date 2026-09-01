package com.example.manualreply

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val userId = intent.getStringExtra("userId") ?: ""
        val username = intent.getStringExtra("username") ?: ""
        val isGroup = intent.getBooleanExtra("isGroup", false)
        val groupTitle = intent.getStringExtra("groupTitle") ?: ""

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }

        val nameText = findViewById<TextView>(R.id.nameText)
        val usernameText = findViewById<TextView>(R.id.usernameText)
        val userIdText = findViewById<TextView>(R.id.userIdText)
        val bioLabel = findViewById<TextView>(R.id.bioLabel)
        val bioText = findViewById<TextView>(R.id.bioText)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val initials = findViewById<TextView>(R.id.initialsText)
        val avatar = findViewById<CircleImageView>(R.id.avatarImage)

        if (isGroup) {
            usernameText.text = "Group chat"
            nameText.text = groupTitle.ifBlank { "Group" }
            initials.text = groupTitle.take(1).uppercase().ifBlank { "G" }
            val bg = initials.background.mutate() as GradientDrawable
            bg.setColor(AvatarUtil.colorFor(groupTitle))
            userIdText.text = "—"
            progressBar.visibility = View.GONE
            return
        }

        nameText.text = "@$username"
        usernameText.text = "@$username"
        userIdText.text = userId.ifBlank { "—" }
        initials.text = AvatarUtil.initialFor(username)
        val bg = initials.background.mutate() as GradientDrawable
        bg.setColor(AvatarUtil.colorFor(username))

        val cachedPhoto = AvatarCache.get(userId)
        if (cachedPhoto != null) {
            Glide.with(avatar.context).load(cachedPhoto).circleCrop().into(avatar)
            avatar.visibility = View.VISIBLE
            initials.visibility = View.INVISIBLE
        } else if (userId.isNotBlank()) {
            Thread {
                val photoUrl = ApiClient.fetchProfilePhotoUrl(userId)
                AvatarCache.put(userId, photoUrl)
                if (photoUrl != null) {
                    runOnUiThread {
                        Glide.with(avatar.context).load(photoUrl).circleCrop().into(avatar)
                        avatar.visibility = View.VISIBLE
                        initials.visibility = View.INVISIBLE
                    }
                }
            }.start()
        }

        if (userId.isNotBlank()) {
            Thread {
                val profile = ApiClient.fetchUserProfile(userId)
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (profile != null) {
                        val fullName = "${profile.firstName} ${profile.lastName}".trim()
                        if (fullName.isNotBlank()) nameText.text = fullName

                        if (profile.bio.isNotBlank()) {
                            bioLabel.visibility = View.VISIBLE
                            bioText.visibility = View.VISIBLE
                            bioText.text = profile.bio
                        }
                    }
                }
            }.start()
        } else {
            progressBar.visibility = View.GONE
        }
    }
}
