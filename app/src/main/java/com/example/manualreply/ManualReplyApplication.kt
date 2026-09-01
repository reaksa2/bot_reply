package com.example.manualreply

import android.app.Application

class ManualReplyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(applicationContext)
        NotificationHelper.createChannel(applicationContext)
        // No automatic sign-in here anymore — SignInActivity handles real
        // Google sign-in explicitly, since the Worker needs to know WHO is
        // registering each bot, not just "someone anonymous."
    }
}
