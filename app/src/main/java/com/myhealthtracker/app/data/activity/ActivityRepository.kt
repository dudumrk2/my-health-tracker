package com.myhealthtracker.app.data.activity

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** Records app-foreground activity so the server-side inactivity cleanup can tell a live install from an abandoned one. */
interface ActivityRepository {
    suspend fun touchLastActive(uid: String)
}

class FirestoreActivityRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ActivityRepository {

    /** Merges a server-timestamped `lastActiveAt` onto `users/{uid}` (sibling of `profile`). */
    override suspend fun touchLastActive(uid: String) {
        firestore.collection("users").document(uid)
            .set(mapOf("lastActiveAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
    }
}
