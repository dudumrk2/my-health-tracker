package com.myhealthtracker.app.data.meal

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.data.model.MealStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

class FirestoreMealRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : MealRepository {

    private val _meals = MutableStateFlow<List<MealEntry>>(emptyList())
    override val meals: StateFlow<List<MealEntry>> = _meals.asStateFlow()

    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        auth.addAuthStateListener { fa ->
            val uid = fa.currentUser?.uid
            if (uid != null) {
                attachListener(uid)
            } else {
                listenerRegistration?.remove()
                listenerRegistration = null
                _meals.value = emptyList()
            }
        }
    }

    private fun mealsCollection(uid: String) =
        firestore.collection("users").document(uid).collection("meals")

    private fun attachListener(uid: String) {
        listenerRegistration?.remove()
        listenerRegistration = mealsCollection(uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                _meals.value = snap.documents
                    .mapNotNull { doc -> mealEntryFromMap(doc.id, doc.data ?: emptyMap()) }
                    .sortedByDescending { it.loggedAt }
            }
    }

    override fun newMealId(): String {
        val uid = auth.currentUser?.uid ?: return java.util.UUID.randomUUID().toString()
        return mealsCollection(uid).document().id
    }

    override fun createPendingMeal(
        mealId: String, date: String, inputType: String,
        description: String, note: String?, localImagePath: String?
    ) {
        val uid = auth.currentUser?.uid ?: return
        val data = mutableMapOf<String, Any>(
            "date" to date, "loggedAt" to Timestamp.now(), "inputType" to inputType,
            "description" to description, "items" to emptyList<Map<String, Any>>(),
            "totals" to MealTotals(0, 0, 0, 0).toMap(),
            "status" to MealStatus.ANALYZING, "seen" to false, "aiModel" to "gemini-2.5-flash"
        )
        if (note != null) data["note"] = note
        if (localImagePath != null) data["localImagePath"] = localImagePath
        mealsCollection(uid).document(mealId).set(data)
    }

    override fun completeMeal(
        mealId: String, items: List<MealItem>, totals: MealTotals,
        recommendation: String?, quality: MealQuality?
    ) {
        val uid = auth.currentUser?.uid ?: return
        val data = mutableMapOf<String, Any>(
            "items" to items.map { it.toMap() },
            "totals" to totals.toMap(),
            "status" to MealStatus.COMPLETE,
            "failureReason" to com.google.firebase.firestore.FieldValue.delete()
        )
        if (recommendation != null) data["recommendation"] = recommendation
        if (quality != null) data["quality"] = quality.toMap()
        mealsCollection(uid).document(mealId).update(data)
    }

    override fun failMeal(mealId: String, reason: String) {
        val uid = auth.currentUser?.uid ?: return
        mealsCollection(uid).document(mealId)
            .update(mapOf("status" to MealStatus.FAILED, "failureReason" to reason))
    }

    override fun retryMeal(mealId: String) {
        val uid = auth.currentUser?.uid ?: return
        mealsCollection(uid).document(mealId).update(
            mapOf("status" to MealStatus.ANALYZING,
                  "failureReason" to com.google.firebase.firestore.FieldValue.delete())
        )
    }

    override fun markMealSeen(mealId: String) {
        val uid = auth.currentUser?.uid ?: return
        mealsCollection(uid).document(mealId).update("seen", true)
    }

    override fun updateMeal(mealId: String, description: String, items: List<MealItem>, totals: MealTotals) {
        val uid = auth.currentUser?.uid ?: return
        mealsCollection(uid).document(mealId).update(
            mapOf("description" to description, "items" to items.map { it.toMap() }, "totals" to totals.toMap())
        )
    }

    override fun addMeal(
        date: String, inputType: String, description: String,
        items: List<MealItem>, totals: MealTotals, recommendation: String?, quality: MealQuality?
    ) {
        val uid = auth.currentUser?.uid ?: return
        val data = mutableMapOf<String, Any>(
            "date" to date, "loggedAt" to Timestamp.now(), "inputType" to inputType,
            "description" to description, "items" to items.map { it.toMap() },
            "totals" to totals.toMap(), "status" to MealStatus.COMPLETE, "seen" to true,
            "aiModel" to "gemini-2.5-flash"
        )
        if (recommendation != null) data["recommendation"] = recommendation
        if (quality != null) data["quality"] = quality.toMap()
        mealsCollection(uid).add(data)
    }

    override fun deleteMeal(mealId: String) {
        val uid = auth.currentUser?.uid ?: return
        mealsCollection(uid).document(mealId).delete()
    }
}

private fun MealItem.toMap() = mapOf(
    "name" to name, "quantity" to quantity, "calories" to calories,
    "proteinG" to proteinG, "carbsG" to carbsG, "fatG" to fatG
)

private fun MealTotals.toMap() = mapOf(
    "calories" to calories, "proteinG" to proteinG, "carbsG" to carbsG, "fatG" to fatG
)

private fun MealQuality.toMap() = mapOf(
    "processedScore" to processedScore,
    "hasComplexCarbs" to hasComplexCarbs,
    "hasSimpleCarbs" to hasSimpleCarbs,
    "hasHealthyFats" to hasHealthyFats,
    "insulinImpact" to insulinImpact
)

fun mealEntryFromMap(id: String, data: Map<String, Any?>): MealEntry? {
    val date = data["date"] as? String ?: return null
    val itemsRaw = data["items"] as? List<*> ?: emptyList<Any>()
    val items = itemsRaw.mapNotNull { e ->
        val m = e as? Map<*, *> ?: return@mapNotNull null
        MealItem(
            name = m["name"] as? String ?: "",
            quantity = m["quantity"] as? String ?: "",
            calories = (m["calories"] as? Number)?.toInt() ?: 0,
            proteinG = (m["proteinG"] as? Number)?.toInt() ?: 0,
            carbsG = (m["carbsG"] as? Number)?.toInt() ?: 0,
            fatG = (m["fatG"] as? Number)?.toInt() ?: 0
        )
    }
    val t = data["totals"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val totals = MealTotals(
        calories = (t["calories"] as? Number)?.toInt() ?: 0,
        proteinG = (t["proteinG"] as? Number)?.toInt() ?: 0,
        carbsG = (t["carbsG"] as? Number)?.toInt() ?: 0,
        fatG = (t["fatG"] as? Number)?.toInt() ?: 0
    )
    val q = data["quality"] as? Map<*, *>
    val quality = q?.let {
        MealQuality(
            processedScore = (it["processedScore"] as? Number)?.toInt() ?: 1,
            hasComplexCarbs = it["hasComplexCarbs"] as? Boolean ?: false,
            hasSimpleCarbs = it["hasSimpleCarbs"] as? Boolean ?: false,
            hasHealthyFats = it["hasHealthyFats"] as? Boolean ?: false,
            insulinImpact = it["insulinImpact"] as? String ?: "low"
        )
    }
    return MealEntry(
        mealId = id, date = date,
        loggedAt = (data["loggedAt"] as? Timestamp)?.toDate()?.toInstant() ?: Instant.now(),
        inputType = data["inputType"] as? String ?: "text",
        description = data["description"] as? String ?: "",
        items = items, totals = totals,
        recommendation = data["recommendation"] as? String,
        quality = quality,
        status = data["status"] as? String ?: MealStatus.COMPLETE,
        localImagePath = data["localImagePath"] as? String,
        note = data["note"] as? String,
        failureReason = data["failureReason"] as? String,
        seen = data["seen"] as? Boolean ?: true
    )
}
