package com.myhealthtracker.app.ui.profile

import com.myhealthtracker.app.data.account.AccountDeletionException
import com.myhealthtracker.app.data.account.AccountRepository
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelDeleteTest {

    private val dispatcher = StandardTestDispatcher()

    // Profile repo that emits no profile, so init's loadProfile() settles to Idle without Firebase.
    private class FakeProfileRepo : ProfileRepository {
        override fun getUserProfile(uid: String): Flow<Result<UserProfile?>> = flowOf(Result.success(null))
        override fun saveUserProfile(uid: String, profile: UserProfile): Flow<Result<Unit>> = flowOf(Result.success(Unit))
        override fun validateProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)
        override fun calculateAge(birthYear: Int, currentYear: Int): Int = 0
    }

    private class FakeAccountRepo(val error: String? = null) : AccountRepository {
        var called = false
        override suspend fun deleteAccount() {
            called = true
            error?.let { throw AccountDeletionException(it) }
        }
    }

    private class NoopBodyRepo : com.myhealthtracker.app.data.body.BodyMeasurementRepository {
        override val bodyMeasurements =
            kotlinx.coroutines.flow.MutableStateFlow<List<com.myhealthtracker.app.data.model.BodyMeasurement>>(emptyList())
        override fun addBodyMeasurement(date: String, weight: Double?, waist: Double?, hips: Double?, note: String) {}
        override fun seedWeight(date: String, weight: Double) {}
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(account: AccountRepository) = ProfileViewModel(
        profileRepository = FakeProfileRepo(),
        uidProvider = { "uid-1" },
        accountRepository = account,
        bodyMeasurementRepository = NoopBodyRepo()
    )

    @Test
    fun deleteAccountSuccessReachesDeleted() = runTest(dispatcher) {
        val account = FakeAccountRepo()
        val vm = viewModel(account)
        vm.deleteAccount()
        advanceUntilIdle()
        assertTrue(account.called)
        assertEquals(AccountState.Deleted, vm.accountState.value)
    }

    @Test
    fun deleteAccountFailureReachesErrorWithMessage() = runTest(dispatcher) {
        val vm = viewModel(FakeAccountRepo(error = "boom"))
        vm.deleteAccount()
        advanceUntilIdle()
        val state = vm.accountState.value
        assertTrue(state is AccountState.Error)
        assertEquals("boom", (state as AccountState.Error).message)
    }
}
