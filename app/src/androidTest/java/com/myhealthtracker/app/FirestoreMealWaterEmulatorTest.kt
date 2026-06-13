package com.myhealthtracker.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.meal.FirestoreMealRepository
import com.myhealthtracker.app.data.water.FirestoreWaterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirestoreMealWaterEmulatorTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    @Before
    fun setUp() = runBlocking {
        firestore = FirebaseFirestore.getInstance().apply { useEmulator("10.0.2.2", 8080) }
        auth = FirebaseAuth.getInstance().apply { useEmulator("10.0.2.2", 9099) }
        auth.signInAnonymously().await()
    }

    @Test
    fun waterIncrementIsIdempotentPerDate() = runBlocking {
        val repo = FirestoreWaterRepository(firestore, auth)
        val date = "2026-06-13"
        repo.addWater(date, 250)
        repo.addWater(date, 250)
        val log = withTimeout(5000) {
            repo.waterLog.first { (it[date] ?: 0) >= 500 }
        }
        assertEquals(500, log[date])
    }

    @Test
    fun mealWriteThenListenReturnsEntry() = runBlocking {
        val repo = FirestoreMealRepository(firestore, auth)
        repo.addMeal(
            date = "2026-06-13", inputType = "text", description = "test",
            items = listOf(MealItem("Egg", "2", 140, 12, 1, 10)),
            totals = MealTotals(140, 12, 1, 10)
        )
        val meals = withTimeout(5000) { repo.meals.first { it.isNotEmpty() } }
        assertEquals("test", meals.first().description)
        assertEquals(140, meals.first().totals.calories)
    }
}
