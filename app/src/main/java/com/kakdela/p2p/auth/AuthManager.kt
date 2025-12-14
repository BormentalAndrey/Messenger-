package com.kakdela.p2p.auth

import com.google.firebase.auth.FirebaseAuth

object AuthManager {

    fun ensureUser(onReady: (String) -> Unit) {
        val auth = FirebaseAuth.getInstance()
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
