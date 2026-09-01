package com.example.manualreply

import com.google.firebase.auth.FirebaseAuth

object FirebaseAuthHelper {
    fun ensureSignedIn(onReady: () -> Unit) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            onReady()
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { onReady() }
                .addOnFailureListener {
                    // caller's screen will simply show no data / an error toast
                    // if this fails — network issue or Firebase misconfiguration
                }
        }
    }
}
