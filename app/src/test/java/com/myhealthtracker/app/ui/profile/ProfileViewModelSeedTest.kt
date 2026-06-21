package com.myhealthtracker.app.ui.profile

import com.myhealthtracker.app.data.account.AccountRepository
import com.myhealthtracker.app.data.body.BodyMeasurementRepository
import com.myhealthtracker.app.data.model.BodyMeasurement
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelSeedTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeProfileRepo : ProfileRepository {
        override fun getUserProfile(uid: String): Flow<Result<UserProfile?>> = flowOf(Result.success(null))
        override fun saveUserProfile(uid: String, profile: UserProfile): Flow<Result<Unit>> = flowOf(Result.success(Unit))
        override fun validateProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)
        override fun calculateAge(birthYear: Int, currentYear: Int): Int = 0
    }

    private class RecordingBodyRepo : BodyMeasurementRepository {
        override val bodyMeasurements: StateFlow<List<BodyMeasurement>> = MutableStateFlow(emptyList())
        var seededDate: String? = null
        var seededWeight: Double? = null
        override fun addBodyMeasurement(date: String, weight: Double?, waist: Double?, hips: Double?, note: String) {}
        override fun seedWeight(date: String, weight: Double) {
            seededDate = date
            seededWeight = weight
        }
    }

    private class NoopAccountRepo : AccountRepository {
        override suspend fun deleteAccount() {}
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun savingProfileSeedsTodayWeightIntoBodyMeasurements() = runTest(dispatcher) {
        val bodyRepo = RecordingBodyRepo()
        val vm = ProfileViewModel(
            profileRepository = FakeProfileRepo(),
            uidProvider = { "uid-1" },
            accountRepository = NoopAccountRepo(),
            bodyMeasurementRepository = bodyRepo
        )

        vm.saveProfile(
            birthYearStr = "1990",
            weightStr = "80.5",
            heightStr = "180",
            gender = "זכר"
        )
        advanceUntilIdle()

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        assertNotNull("Expected a seeded body measurement after saving the profile", bodyRepo.seededWeight)
        assertEquals(today, bodyRepo.seededDate)
        assertEquals(80.5, bodyRepo.seededWeight!!, 0.001)
    }
}
