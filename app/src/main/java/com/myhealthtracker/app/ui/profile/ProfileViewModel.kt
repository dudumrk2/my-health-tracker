package com.myhealthtracker.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.goals.GoalCalculator
import com.myhealthtracker.app.data.goals.HealthGoals
import com.myhealthtracker.app.data.profile.GoalOverrides
import com.myhealthtracker.app.data.account.AccountDeletionException
import com.myhealthtracker.app.data.account.AccountRepository
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import com.myhealthtracker.app.data.profile.genderToHebrew
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    object Saved : ProfileUiState()
    data class Loaded(val profile: UserProfile) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

sealed class AccountState {
    object Idle : AccountState()
    object Deleting : AccountState()
    object Deleted : AccountState()
    data class Error(val message: String) : AccountState()
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository = AppContainer.profileRepository,
    private val uidProvider: () -> String? = { AppContainer.currentUid() },
    private val accountRepository: AccountRepository = AppContainer.accountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _calculatedAge = MutableStateFlow(0)
    val calculatedAge: StateFlow<Int> = _calculatedAge.asStateFlow()

    private val _accountState = MutableStateFlow<AccountState>(AccountState.Idle)
    val accountState: StateFlow<AccountState> = _accountState.asStateFlow()

    init {
        loadProfile()
    }

    fun resetState() {
        _uiState.value = ProfileUiState.Idle
        loadProfile()
    }

    fun loadProfile() {
        val uid = uidProvider() ?: run {
            _uiState.value = ProfileUiState.Idle
            return
        }
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            profileRepository.getUserProfile(uid).collect { result ->
                val profile = result.getOrNull()
                if (profile != null) {
                    val hebrewGender = genderToHebrew(profile.gender)
                    _uiState.value = ProfileUiState.Loaded(profile.copy(gender = hebrewGender))
                    updateAge(profile.birthYear)
                } else {
                    _uiState.value = ProfileUiState.Idle
                }
            }
        }
    }

    fun updateAge(birthYear: Int) {
        if (birthYear <= 0) {
            _calculatedAge.value = 0
            return
        }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val age = currentYear - birthYear
        _calculatedAge.value = if (age >= 0) age else 0
    }

    /**
     * Live preview of the goals computed from the current form inputs. Falls back to
     * safe generic defaults when inputs are incomplete (never crashes). Manual overrides win.
     */
    fun previewGoals(
        birthYearStr: String,
        weightStr: String,
        heightStr: String,
        gender: String,
        primaryGoal: String,
        activityLevel: String,
        goalOverrides: GoalOverrides? = null
    ): HealthGoals {
        val profile = UserProfile(
            birthYear = birthYearStr.toIntOrNull() ?: 0,
            weightKg = weightStr.toDoubleOrNull() ?: 0.0,
            heightCm = heightStr.toDoubleOrNull() ?: 0.0,
            gender = toEnglishGender(gender),
            primaryGoal = primaryGoal,
            activityLevel = activityLevel,
            goalOverrides = goalOverrides
        )
        return GoalCalculator.compute(profile)
    }

    private fun toEnglishGender(gender: String): String = when (gender) {
        "זכר" -> "male"
        "נקבה" -> "female"
        else -> gender
    }

    fun saveProfile(
        birthYearStr: String,
        weightStr: String,
        heightStr: String,
        gender: String,
        themePreference: String = "system",
        primaryGoal: String = "maintain",
        activityLevel: String = "moderate",
        focusAreas: List<String> = emptyList(),
        goalOverrides: GoalOverrides? = null,
        quickActionsEnabled: Boolean = true,
        celebrationSoundEnabled: Boolean = true
    ) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading

            val birthYear = birthYearStr.toIntOrNull()
            if (birthYear == null) {
                _uiState.value = ProfileUiState.Error("שנת הלידה חייבת להיות מספר שלם")
                return@launch
            }

            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (birthYear < 1900 || birthYear > currentYear) {
                _uiState.value = ProfileUiState.Error("שנת הלידה חייבת להיות בין 1900 ל-$currentYear")
                return@launch
            }
            if (gender.isEmpty()) {
                _uiState.value = ProfileUiState.Error("אנא בחר מין")
                return@launch
            }

            val weight = weightStr.toDoubleOrNull()
            if (weight == null) {
                _uiState.value = ProfileUiState.Error("המשקל חייב להיות מספר תקין")
                return@launch
            }
            if (weight < 30.0 || weight > 300.0) {
                _uiState.value = ProfileUiState.Error("המשקל חייב להיות בין 30.0 ל-300.0 ק״ג")
                return@launch
            }

            val height = heightStr.toDoubleOrNull()
            if (height == null) {
                _uiState.value = ProfileUiState.Error("הגובה חייב להיות מספר תקין")
                return@launch
            }
            if (height < 100.0 || height > 250.0) {
                _uiState.value = ProfileUiState.Error("הגובה חייב להיות בין 100.0 ל-250.0 ס״מ")
                return@launch
            }

            val englishGender = toEnglishGender(gender)

            val profile = UserProfile(
                birthYear = birthYear,
                weightKg = weight,
                heightCm = height,
                gender = englishGender,
                themePreference = themePreference,
                primaryGoal = primaryGoal,
                activityLevel = activityLevel,
                focusAreas = focusAreas,
                goalOverrides = goalOverrides,
                quickActionsEnabled = quickActionsEnabled,
                celebrationSoundEnabled = celebrationSoundEnabled
            )

            val uid = uidProvider() ?: run {
                _uiState.value = ProfileUiState.Error("נדרשת התחברות מחדש.")
                return@launch
            }

            profileRepository.saveUserProfile(uid, profile).collect { result ->
                if (result.isSuccess) {
                    _uiState.value = ProfileUiState.Saved
                } else {
                    _uiState.value = ProfileUiState.Error(result.exceptionOrNull()?.message ?: "שגיאה בשמירה")
                }
            }
        }
    }

    /** Permanently deletes the account + all data via the Cloud Function. Local sign-out is handled by the caller. */
    fun deleteAccount() {
        viewModelScope.launch {
            _accountState.value = AccountState.Deleting
            try {
                accountRepository.deleteAccount()
                _accountState.value = AccountState.Deleted
            } catch (e: AccountDeletionException) {
                _accountState.value = AccountState.Error(e.message ?: "שגיאה במחיקת החשבון")
            }
        }
    }
}
