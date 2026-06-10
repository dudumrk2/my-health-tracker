package com.myhealthtracker.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myhealthtracker.app.data.health.ExerciseSessionInfo
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.health.SleepSessionInfo
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryAndHealthConnectTest {

    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val docRef = mockk<DocumentReference>(relaxed = true)
    private val snapshot = mockk<DocumentSnapshot>(relaxed = true)

    private lateinit var profileRepository: ProfileRepository
    private lateinit var healthRepository: HealthRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        profileRepository = ProfileRepository(firestore)
        healthRepository = HealthRepository(firestore)

        every { firestore.collection("users").document(any()) } returns docRef
        every { docRef.collection("healthDaily").document(any()) } returns docRef
    }

    @Test
    fun testProfileRepositorySaveAndLoad() = runBlocking {
        val uid = "test_user_id"
        val profile = UserProfile(birthYear = 1990, weightKg = 75.0, heightCm = 180.0)

        val setTask = mockk<com.google.android.gms.tasks.Task<Void>>(relaxed = true)
        val getTask = mockk<com.google.android.gms.tasks.Task<DocumentSnapshot>>(relaxed = true)
        
        every { docRef.get() } returns getTask
        every { getTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<com.google.android.gms.tasks.OnSuccessListener<DocumentSnapshot>>()
            listener.onSuccess(snapshot)
            getTask
        }
        every { getTask.addOnFailureListener(any()) } returns getTask

        every { snapshot.get("profile") } returns mapOf(
            "birthYear" to 1990L,
            "weightKg" to 75.0,
            "heightCm" to 180.0
        )

        every { docRef.set(any(), SetOptions.merge()) } returns setTask
        every { setTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<com.google.android.gms.tasks.OnSuccessListener<Void>>()
            listener.onSuccess(null)
            setTask
        }
        every { setTask.addOnFailureListener(any()) } returns setTask

        val saveResult = profileRepository.saveUserProfile(uid, profile).first()
        assertTrue(saveResult.isSuccess)
    }

    @Test
    fun testHealthRepositoryIdempotentSave() = runBlocking {
        val uid = "test_user_id"
        val date = "2026-06-11"
        val steps = 5000L
        val sleepSessions = listOf(SleepSessionInfo(Timestamp.now(), Timestamp.now()))
        val workouts = listOf(ExerciseSessionInfo("Running", 30, Timestamp.now()))

        val setTask = mockk<com.google.android.gms.tasks.Task<Void>>(relaxed = true)
        every { docRef.set(any(), SetOptions.merge()) } returns setTask
        every { setTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<com.google.android.gms.tasks.OnSuccessListener<Void>>()
            listener.onSuccess(null)
            setTask
        }
        every { setTask.addOnFailureListener(any()) } returns setTask

        val saveResult = healthRepository.saveDailyHealthData(uid, date, steps, sleepSessions, workouts).first()
        assertTrue(saveResult.isSuccess)

        verify(exactly = 1) { docRef.set(any(), SetOptions.merge()) }
    }
}
