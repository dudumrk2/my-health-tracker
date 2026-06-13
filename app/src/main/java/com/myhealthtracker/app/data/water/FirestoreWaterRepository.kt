package com.myhealthtracker.app.data.water

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FirestoreWaterRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : WaterRepository {

    private val _waterLog = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val waterLog: StateFlow<Map<String, Int>> = _waterLog.asStateFlow()

    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        auth.addAuthStateListener { fa ->
            val uid = fa.currentUser?.uid
            if (uid != null) {
                attachListener(uid)
            } else {
                listenerRegistration?.remove()
                listenerRegistration = null
                _waterLog.value = emptyMap()
            }
        }
    }

    private fun attachListener(uid: String) {
        listenerRegistration?.remove()
        listenerRegistration = firestore.collection("users").document(uid).collection("water")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                _waterLog.value = snap.documents.associate { doc ->
                    doc.id to ((doc.get("amountMl") as? Number)?.toInt() ?: 0)
                }
            }
    }

    override fun addWater(date: String, amountMl: Int) {
        val uid = auth.currentUser?.uid ?: return
        val doc = firestore.collection("users").document(uid).collection("water").document(date)
        doc.set(
            mapOf(
                "date" to date,
                "amountMl" to FieldValue.increment(amountMl.toLong()),
                "updatedAt" to Timestamp.now()
            ),
            SetOptions.merge()
        )
    }
}
