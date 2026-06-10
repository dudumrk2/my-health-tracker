package com.myhealthtracker.app.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow

class AuthManager(private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()) {

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    private val _authState = MutableStateFlow<FirebaseUser?>(firebaseAuth.currentUser)
    val authState: StateFlow<FirebaseUser?> = _authState

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _authState.value = auth.currentUser
        }
    }

    fun signInWithGoogle(idToken: String): Flow<Result<FirebaseUser>> = callbackFlow {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user != null) {
                    trySend(Result.success(user))
                } else {
                    trySend(Result.failure(Exception("User is null after successful sign in")))
                }
                close()
            }
            .addOnFailureListener { exception ->
                trySend(Result.failure(exception))
                close()
            }
        awaitClose()
    }

    fun signOut() {
        firebaseAuth.signOut()
    }
}
