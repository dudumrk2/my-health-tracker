package com.myhealthtracker.app.data.insights

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.myhealthtracker.app.data.insights.model.DailyInsights
import com.myhealthtracker.app.data.insights.model.InsightToday
import com.myhealthtracker.app.data.insights.model.InsightTomorrow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Auth-aware snapshot listener on `users/{uid}/insights/{today}` — the client reads
 * only today's document, which carries both blocks (today + last night's tomorrow).
 */
class FirestoreInsightsRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val today: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
) : InsightsRepository {

    private val _insights = MutableStateFlow<DailyInsights?>(null)
    override val insights: StateFlow<DailyInsights?> = _insights.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null

    init {
        auth.addAuthStateListener { fa ->
            val uid = fa.currentUser?.uid
            if (uid != null) {
                attachListener(uid)
            } else {
                listenerRegistration?.remove()
                listenerRegistration = null
                _insights.value = null
            }
        }
    }

    private fun attachListener(uid: String) {
        listenerRegistration?.remove()
        listenerRegistration = firestore.collection("users").document(uid)
            .collection("insights").document(today)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                _insights.value = if (snap.exists()) snap.toDailyInsights(today) else null
            }
    }
}

private fun DocumentSnapshot.toDailyInsights(fallbackDate: String): DailyInsights {
    val todayMap = get("today") as? Map<*, *>
    val tomorrowMap = get("tomorrow") as? Map<*, *>
    return DailyInsights(
        date = getString("date") ?: fallbackDate,
        today = todayMap?.let {
            InsightToday(
                general = it["general"] as? String ?: "",
                nutrition = it["nutrition"] as? String ?: "",
                activity = it["activity"] as? String ?: "",
                sleep = it["sleep"] as? String ?: ""
            )
        },
        tomorrow = tomorrowMap?.let {
            InsightTomorrow(
                nutrition = it["nutrition"] as? String ?: "",
                activity = it["activity"] as? String ?: "",
                sleep = it["sleep"] as? String ?: ""
            )
        },
        disclaimer = getString("disclaimer") ?: "",
        trigger = getString("trigger") ?: "",
        generatedAt = (get("generatedAt") as? Timestamp)?.toDate()?.toInstant()
    )
}
