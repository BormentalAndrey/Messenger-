package com.kakdela.p2p.auth

import com.google.firebase.auth.FirebaseAuth

object AuthManager {

    private val auth = FirebaseAuth.getInstance()

    fun ensureUser(onReady: (String) -> Unit) {
        val user = auth.currentUser
        if (user != null) {
            onReady(user.uid)
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    onReady(it.user!!.uid)
                }
        }
    }
}
