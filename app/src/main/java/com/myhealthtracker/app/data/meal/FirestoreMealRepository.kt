package com.myhealthtracker.app.data.meal

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
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

    init {
        auth.addAuthStateListener { fa ->
            val uid = fa.currentUser?.uid
            if (uid != null) attachListener(uid) else _meals.value = emptyList()
        }
        auth.currentUser?.uid?.let { attachListener(it) }
    }

    private fun attachListener(uid: String) {
        firestore.collection("users").document(uid).collection("meals")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                _meals.value = snap.documents.mapNotNull { doc -> doc.toMealEntry() }
                    .sortedByDescending { it.loggedAt }
            }
    }

    override fun addMeal(
        date: String, inputType: String, description: String,
        items: List<MealItem>, totals: MealTotals
    ) {
        val uid = auth.currentUser?.uid ?: return
        val data = mapOf(
            "date" to date,
            "loggedAt" to Timestamp.now(),
            "inputType" to inputType,
            "description" to description,
            "items" to items.map { it.toMap() },
            "totals" to totals.toMap(),
            "aiModel" to "gemini-2.5-flash"
        )
        firestore.collection("users").document(uid).collection("meals").add(data)
    }

    override fun deleteMeal(mealId: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).collection("meals").document(mealId).delete()
    }
}

private fun MealItem.toMap() = mapOf(
    "name" to name, "quantity" to quantity, "calories" to calories,
    "proteinG" to proteinG, "carbsG" to carbsG, "fatG" to fatG
)

private fun MealTotals.toMap() = mapOf(
    "calories" to calories, "proteinG" to proteinG, "carbsG" to carbsG, "fatG" to fatG
)

private fun com.google.firebase.firestore.DocumentSnapshot.toMealEntry(): MealEntry? {
    val date = getString("date") ?: return null
    val itemsRaw = get("items") as? List<*> ?: emptyList<Any>()
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
    val t = get("totals") as? Map<*, *> ?: emptyMap<Any, Any>()
    val totals = MealTotals(
        calories = (t["calories"] as? Number)?.toInt() ?: 0,
        proteinG = (t["proteinG"] as? Number)?.toInt() ?: 0,
        carbsG = (t["carbsG"] as? Number)?.toInt() ?: 0,
        fatG = (t["fatG"] as? Number)?.toInt() ?: 0
    )
    return MealEntry(
        mealId = id,
        date = date,
        loggedAt = (get("loggedAt") as? Timestamp)?.toDate()?.toInstant() ?: Instant.now(),
        inputType = getString("inputType") ?: "text",
        description = getString("description") ?: "",
        items = items,
        totals = totals
    )
}
