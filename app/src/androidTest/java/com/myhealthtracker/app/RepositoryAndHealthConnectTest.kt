package com.myhealthtracker.app

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.myhealthtracker.app.data.health.ExerciseSessionInfo
import com.myhealthtracker.app.data.health.FirestoreHealthRepository
import com.myhealthtracker.app.data.health.HealthConnectManager
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.health.SleepSessionInfo
import com.myhealthtracker.app.data.profile.FirestoreProfileRepository
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/**
 * Instrumented repository tests. Firestore operations run against the Firebase
 * Local Emulator at 10.0.2.2:8080 (host machine from the Android emulator).
 *
 * Prerequisites: `firebase emulators:start --only firestore` must be running.
 */
@RunWith(AndroidJUnit4::class)
class RepositoryAndHealthConnectTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var profileRepository: ProfileRepository
    private lateinit var healthRepository: HealthRepository

    // Unique per-test UID so tests are fully isolated without explicit cleanup races.
    private val testUid = "test_${UUID.randomUUID()}"

    companion object {
        private var emulatorConfigured = false
    }

    @Before
    fun setUp() {
        firestore = FirebaseFirestore.getInstance()
        if (!emulatorConfigured) {
            try {
                firestore.useEmulator("10.0.2.2", 8080)
            } catch (e: IllegalStateException) {
                // Already configured by a previous test run in the same process.
            }
            emulatorConfigured = true
        }
        profileRepository = FirestoreProfileRepository(firestore)
        healthRepository  = FirestoreHealthRepository(firestore)
    }

    @After
    fun cleanup() {
        try {
            Tasks.await(firestore.collection("users").document(testUid).delete())
        } catch (e: Exception) {
            // Best-effort cleanup; emulator data is ephemeral anyway.
        }
    }

    // ── ProfileRepository ────────────────────────────────────────────────────

    @Test
    fun testProfileRepository_saveAndLoad() = runBlocking {
        val profile = UserProfile(birthYear = 1990, weightKg = 75.0, heightCm = 180.0, gender = "male")

        val saveResult = profileRepository.saveUserProfile(testUid, profile).first()
        assertTrue("Save should succeed", saveResult.isSuccess)

        val loadResult = profileRepository.getUserProfile(testUid).first()
        assertTrue("Load should succeed", loadResult.isSuccess)

        val loaded = loadResult.getOrNull()
        assertNotNull("Loaded profile should not be null", loaded)
        assertEquals(1990,  loaded!!.birthYear)
        assertEquals(75.0,  loaded.weightKg, 0.001)
        assertEquals(180.0, loaded.heightCm, 0.001)
        assertEquals("male", loaded.gender)
    }

    // ── HealthRepository ─────────────────────────────────────────────────────

    @Test
    fun testHealthRepository_idempotentWrite() = runBlocking {
        val date     = "2026-06-11"
        val sleep    = listOf(SleepSessionInfo(Instant.now(), Instant.now()))
        val workouts = listOf(ExerciseSessionInfo("Running", 30, Instant.now()))

        val first = healthRepository.saveDailyHealthData(testUid, date, 5000L, sleep, workouts).first()
        assertTrue("First write should succeed", first.isSuccess)

        // Second write to the same date — must update, not duplicate.
        val second = healthRepository.saveDailyHealthData(testUid, date, 6000L, sleep, workouts).first()
        assertTrue("Second write should succeed", second.isSuccess)

        val snapshot = Tasks.await(
            firestore.collection("users").document(testUid)
                .collection("healthDaily").document(date).get()
        )
        assertTrue("Document should exist", snapshot.exists())
        assertEquals(
            "Steps from second write should overwrite first",
            6000L, snapshot.getLong("steps")
        )
    }

    // ── HealthConnectManager (mock) ──────────────────────────────────────────

    @Test
    fun testHealthConnectManager_sdkUnavailable_allReadsReturnEmpty() = runBlocking {
        mockkStatic(HealthConnectClient::class)
        every { HealthConnectClient.getSdkStatus(any()) } returns
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = HealthConnectManager(context)

        assertFalse("isSdkAvailable should be false", manager.isSdkAvailable())
        assertEquals(0L, manager.readDailySteps(Instant.now(), Instant.now()))
        assertTrue(manager.readSleepSessions(Instant.now(), Instant.now()).isEmpty())
        assertTrue(manager.readExerciseSessions(Instant.now(), Instant.now()).isEmpty())
        assertFalse("hasAllPermissions should be false when SDK unavailable", manager.hasAllPermissions())

        unmockkStatic(HealthConnectClient::class)
    }

    @Test
    fun testHealthConnectManager_platformTooOld_allReadsReturnEmpty() = runBlocking {
        mockkStatic(HealthConnectClient::class)
        every { HealthConnectClient.getSdkStatus(any()) } returns
                HealthConnectClient.SDK_UNAVAILABLE

        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = HealthConnectManager(context)

        assertFalse(manager.isSdkAvailable())
        assertEquals(0L, manager.readDailySteps(Instant.now(), Instant.now()))
        assertTrue(manager.readSleepSessions(Instant.now(), Instant.now()).isEmpty())
        assertTrue(manager.readExerciseSessions(Instant.now(), Instant.now()).isEmpty())
        assertFalse(manager.hasAllPermissions())

        unmockkStatic(HealthConnectClient::class)
    }
}
