package com.myhealthtracker.app.ui

import com.myhealthtracker.app.data.FakeRepository
import com.myhealthtracker.app.ui.body.AddBodyMeasurementViewModel
import com.myhealthtracker.app.ui.profile.ProfileUiState
import com.myhealthtracker.app.ui.profile.ProfileViewModel
import com.myhealthtracker.app.ui.workout.AddWorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class UiValidationTests {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        FakeRepository.resetToDefaults()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- ProfileViewModel Tests ---

    @Test
    fun profileViewModel_initialization_loadsProfileAndCalculatesAge() = runTest {
        val viewModel = ProfileViewModel(profileRepository = FakeRepository, uidProvider = { "test-uid" })
        val state = viewModel.uiState.value
        assertTrue(state is ProfileUiState.Loaded)
        val loadedProfile = (state as ProfileUiState.Loaded).profile
        assertEquals(1995, loadedProfile.birthYear)
        assertEquals("זכר", loadedProfile.gender)
        
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        assertEquals(currentYear - 1995, viewModel.calculatedAge.value)
    }

    @Test
    fun profileViewModel_saveProfile_invalidBirthYear_returnsError() = runTest {
        val viewModel = ProfileViewModel(profileRepository = FakeRepository, uidProvider = { "test-uid" })
        
        // Under 1900
        viewModel.saveProfile("1899", "70.0", "175.0", "זכר")
        val state1 = viewModel.uiState.value
        assertTrue(state1 is ProfileUiState.Error)
        assertTrue((state1 as ProfileUiState.Error).message.contains("שנת הלידה חייבת להיות בין 1900 ל"))

        // Over current year
        val nextYear = Calendar.getInstance().get(Calendar.YEAR) + 1
        viewModel.saveProfile(nextYear.toString(), "70.0", "175.0", "זכר")
        val state2 = viewModel.uiState.value
        assertTrue(state2 is ProfileUiState.Error)
        assertTrue((state2 as ProfileUiState.Error).message.contains("שנת הלידה חייבת להיות בין 1900 ל"))
    }

    @Test
    fun profileViewModel_saveProfile_emptyGender_returnsError() = runTest {
        val viewModel = ProfileViewModel(profileRepository = FakeRepository, uidProvider = { "test-uid" })
        viewModel.saveProfile("1995", "70.0", "175.0", "")
        val state = viewModel.uiState.value
        assertTrue(state is ProfileUiState.Error)
        assertEquals("אנא בחר מין", (state as ProfileUiState.Error).message)
    }

    @Test
    fun profileViewModel_saveProfile_invalidWeight_returnsError() = runTest {
        val viewModel = ProfileViewModel(profileRepository = FakeRepository, uidProvider = { "test-uid" })
        
        // Too low
        viewModel.saveProfile("1995", "29.9", "175.0", "זכר")
        val state1 = viewModel.uiState.value
        assertTrue(state1 is ProfileUiState.Error)
        assertEquals("המשקל חייב להיות בין 30.0 ל-300.0 ק״ג", (state1 as ProfileUiState.Error).message)

        // Too high
        viewModel.saveProfile("1995", "300.1", "175.0", "זכר")
        val state2 = viewModel.uiState.value
        assertTrue(state2 is ProfileUiState.Error)
        assertEquals("המשקל חייב להיות בין 30.0 ל-300.0 ק״ג", (state2 as ProfileUiState.Error).message)
    }

    @Test
    fun profileViewModel_saveProfile_invalidHeight_returnsError() = runTest {
        val viewModel = ProfileViewModel(profileRepository = FakeRepository, uidProvider = { "test-uid" })
        
        // Too low
        viewModel.saveProfile("1995", "70.0", "99.9", "זכר")
        val state1 = viewModel.uiState.value
        assertTrue(state1 is ProfileUiState.Error)
        assertEquals("הגובה חייב להיות בין 100.0 ל-250.0 ס״מ", (state1 as ProfileUiState.Error).message)

        // Too high
        viewModel.saveProfile("1995", "70.0", "250.1", "זכר")
        val state2 = viewModel.uiState.value
        assertTrue(state2 is ProfileUiState.Error)
        assertEquals("הגובה חייב להיות בין 100.0 ל-250.0 ס״מ", (state2 as ProfileUiState.Error).message)
    }

    @Test
    fun profileViewModel_saveProfile_validInputs_savesProfile() = runTest {
        val viewModel = ProfileViewModel(profileRepository = FakeRepository, uidProvider = { "test-uid" })
        viewModel.saveProfile("1990", "80.0", "180.0", "נקבה")
        
        val state = viewModel.uiState.value
        assertTrue("State should be Saved or Loaded", state is ProfileUiState.Saved || state is ProfileUiState.Loaded)

        val profile = FakeRepository.profile.value
        assertNotNull(profile)
        assertEquals(1990, profile!!.birthYear)
        assertEquals(80.0, profile.weightKg, 0.001)
        assertEquals(180.0, profile.heightCm, 0.001)
        assertEquals("female", profile.gender)
    }

    // --- AddWorkoutViewModel Tests ---

    @Test
    fun addWorkoutViewModel_saveWorkout_noTypeSelected_returnsError() = runTest {
        val viewModel = AddWorkoutViewModel(healthRepository = FakeRepository, uidProvider = { "test-uid" })
        viewModel.onDurationChange("30")
        viewModel.saveWorkout()
        
        assertEquals("אנא בחר סוג אימון", viewModel.errorMessage.value)
        assertEquals(false, viewModel.isSaved.value)
    }

    @Test
    fun addWorkoutViewModel_saveWorkout_invalidDuration_returnsError() = runTest {
        val viewModel = AddWorkoutViewModel(healthRepository = FakeRepository, uidProvider = { "test-uid" })
        viewModel.selectType("ריצה")
        
        // Duration 0
        viewModel.onDurationChange("0")
        viewModel.saveWorkout()
        assertEquals("משך האימון חייב להיות גדול מ-0 דקות", viewModel.errorMessage.value)
        assertEquals(false, viewModel.isSaved.value)

        // Negative duration
        viewModel.onDurationChange("-5")
        viewModel.saveWorkout()
        assertEquals("משך האימון חייב להיות גדול מ-0 דקות", viewModel.errorMessage.value)
        assertEquals(false, viewModel.isSaved.value)

        // Non-number duration
        viewModel.onDurationChange("abc")
        viewModel.saveWorkout()
        assertEquals("משך האימון חייב להיות גדול מ-0 דקות", viewModel.errorMessage.value)
        assertEquals(false, viewModel.isSaved.value)
    }

    @Test
    fun addWorkoutViewModel_saveWorkout_validInputs_savesWorkout() = runTest {
        val viewModel = AddWorkoutViewModel(healthRepository = FakeRepository, uidProvider = { "test-uid" })
        viewModel.selectType("ריצה")
        viewModel.onDurationChange("45")
        viewModel.saveWorkout()
        
        assertNull(viewModel.errorMessage.value)
        assertEquals(true, viewModel.isSaved.value)

        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val healthData = FakeRepository.getDailyHealthData(todayStr)
        val savedWorkout = healthData.workouts.lastOrNull { it.type == "ריצה" && it.source == "manual" }
        assertNotNull(savedWorkout)
        assertEquals(45, savedWorkout!!.durationMin)
    }

    // --- AddBodyMeasurementViewModel Tests ---

    @Test
    fun addBodyMeasurementViewModel_saveMeasurement_invalidWeight_returnsError() = runTest {
        val viewModel = AddBodyMeasurementViewModel(bodyMeasurementRepository = FakeRepository)
        
        viewModel.onWeightChange("-1.0")
        viewModel.saveMeasurement()
        assertEquals("המשקל חייב להיות בין 0 ל-500 ק״ג", viewModel.errorMessage.value)
        assertEquals(false, viewModel.isSaved.value)

        viewModel.onWeightChange("501.0")
        viewModel.saveMeasurement()
        assertEquals("המשקל חייב להיות בין 0 ל-500 ק״ג", viewModel.errorMessage.value)
        assertEquals(false, viewModel.isSaved.value)
    }

    @Test
    fun addBodyMeasurementViewModel_saveMeasurement_invalidWaist_returnsError() = runTest {
        val viewModel = AddBodyMeasurementViewModel(bodyMeasurementRepository = FakeRepository)
        
        viewModel.onWaistChange("-1.0")
        viewModel.saveMeasurement()
        assertEquals("היקף המותן חייב להיות בין 0 ל-300 ס״מ", viewModel.errorMessage.value)
        assertEquals(false, viewModel.isSaved.value)

        viewModel.onWaistChange("301.0")
        viewModel.saveMeasurement()
        assertEquals("היקף המותן חייב להיות בין 0 ל-300 ס״מ", viewModel.errorMessage.value)
        assertEquals(false, viewModel.isSaved.value)
    }

    @Test
    fun addBodyMeasurementViewModel_saveMeasurement_invalidHips_returnsError() = runTest {
        val viewModel = AddBodyMeasurementViewModel(bodyMeasurementRepository = FakeRepository)
        
        viewModel.onHipsChange("-1.0")
        viewModel.saveMeasurement()
        assertEquals("היקף הירכיים חייב להיות בין 0 ל-300 ס״מ", viewModel.errorMessage.value)
        assertEquals(false, viewModel.isSaved.value)

        viewModel.onHipsChange("301.0")
        viewModel.saveMeasurement()
        assertEquals("היקף הירכיים חייב להיות בין 0 ל-300 ס״מ", viewModel.errorMessage.value)
        assertEquals(false, viewModel.isSaved.value)
    }

    @Test
    fun addBodyMeasurementViewModel_saveMeasurement_validInputs_savesMeasurement() = runTest {
        val viewModel = AddBodyMeasurementViewModel(bodyMeasurementRepository = FakeRepository)
        viewModel.onWeightChange("78.5")
        viewModel.onWaistChange("82.0")
        viewModel.onHipsChange("95.0")
        viewModel.onNoteChange("שקילה שבועית")
        viewModel.saveMeasurement()
        
        assertNull(viewModel.errorMessage.value)
        assertEquals(true, viewModel.isSaved.value)

        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val measurements = FakeRepository.bodyMeasurements.value
        val saved = measurements.lastOrNull { it.date == todayStr }
        assertNotNull(saved)
        assertEquals(78.5, saved!!.weightKg!!, 0.001)
        assertEquals(82.0, saved.waistCm!!, 0.001)
        assertEquals(95.0, saved.hipsCm!!, 0.001)
        assertEquals("שקילה שבועית", saved.note)
    }
}
