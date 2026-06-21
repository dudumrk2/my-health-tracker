package com.myhealthtracker.app.data.body

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.myhealthtracker.app.data.model.BodyMeasurement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Auth-aware snapshot listener on `users/{uid}/bodyMeasurements`. Documents are keyed
 * by date (yyyy-MM-dd) so a re-entry for the same day overwrites rather than duplicates.
 * Self-tracking only — not fed to the AI prompts (per CLAUDE.md).
 */
class FirestoreBodyMeasurementRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : BodyMeasurementRepository {

    private val _bodyMeasurements = MutableStateFlow<List<BodyMeasurement>>(emptyList())
    override val bodyMeasurements: StateFlow<List<BodyMeasurement>> = _bodyMeasurements.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null

    init {
        auth.addAuthStateListener { fa ->
            val uid = fa.currentUser?.uid
            if (uid != null) {
                attachListener(uid)
            } else {
                listenerRegistration?.remove()
                listenerRegistration = null
                _bodyMeasurements.value = emptyList()
            }
        }
    }

    private fun attachListener(uid: String) {
        listenerRegistration?.remove()
        listenerRegistration = firestore.collection("users").document(uid)
            .collection("bodyMeasurements")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                _bodyMeasurements.value = snap.documents
                    .map { doc ->
                        BodyMeasurement(
                            date = doc.getString("date") ?: doc.id,
                            weightKg = (doc.get("weightKg") as? Number)?.toDouble(),
                            waistCm = (doc.get("waistCm") as? Number)?.toDouble(),
                            hipsCm = (doc.get("hipsCm") as? Number)?.toDouble(),
                            note = doc.getString("note") ?: ""
                        )
                    }
                    .sortedBy { it.date }
            }
    }

    override fun addBodyMeasurement(date: String, weight: Double?, waist: Double?, hips: Double?, note: String) {
        val uid = auth.currentUser?.uid ?: return
        val data = mutableMapOf<String, Any>(
            "date" to date,
            "note" to note,
            "loggedAt" to Timestamp.now()
        )
        if (weight != null) data["weightKg"] = weight
        if (waist != null) data["waistCm"] = waist
        if (hips != null) data["hipsCm"] = hips

        // Overwrite per-date (idempotent self-tracking) rather than merge,
        // so clearing a field on re-entry removes it.
        firestore.collection("users").document(uid)
            .collection("bodyMeasurements").document(date)
            .set(data)
    }

    override fun seedWeight(date: String, weight: Double) {
        val uid = auth.currentUser?.uid ?: return
        val data = mapOf(
            "date" to date,
            "weightKg" to weight,
            "loggedAt" to Timestamp.now()
        )
        // Merge so a same-day manual measurement keeps its waist/hips/note.
        firestore.collection("users").document(uid)
            .collection("bodyMeasurements").document(date)
            .set(data, SetOptions.merge())
    }
}
