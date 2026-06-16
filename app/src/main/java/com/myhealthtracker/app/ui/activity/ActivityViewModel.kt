package com.myhealthtracker.app.ui.activity

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.health.HealthConnectManager
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.health.ExerciseSessionInfo
import com.myhealthtracker.app.data.health.DailyHealthData
import com.myhealthtracker.app.data.goals.GoalCalculator
import com.myhealthtracker.app.data.goals.HealthGoals
import com.myhealthtracker.app.data.insights.InsightsRefresher
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import com.myhealthtracker.app.di.AppContainer
import com.myhealthtracker.app.sync.HealthSyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ActivityState(
    val selectedDate: LocalDate = LocalDate.now(),
    val steps: Long = 0,
    val sleepMinutes: Int = 0,
    val workouts: List<ExerciseSessionInfo> = emptyList(),
    val isRefreshing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModel(
    private val healthRepository: HealthRepository = AppContainer.healthRepository,
    private val insightsRefresher: InsightsRefresher = AppContainer.insightsRefresher,
    private val profileRepository: ProfileRepository = AppContainer.profileRepository,
    private val uidProvider: () -> String? = { AppContainer.currentUid() }
) : ViewModel() {

    /** Daily step goal computed from the profile (safe generic fallback when incomplete). */
    val goals: StateFlow<HealthGoals> = run {
        val uid = uidProvider()
        if (uid == null) flowOf(GoalCalculator.compute(UserProfile()))
        else profileRepository.getUserProfile(uid).map { GoalCalculator.compute(it.getOrNull() ?: UserProfile()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoalCalculator.compute(UserProfile()))

    // True only when the SDK is available but permissions are not yet granted — the
    // signal the screen uses to launch the permission request (minimum-permission rule).
    private val _needsPermissions = MutableStateFlow(false)
    val needsPermissions: StateFlow<Boolean> = _needsPermissions.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _isRefreshing = MutableStateFlow(false)

    private val _healthData = _selectedDate.flatMapLatest { date ->
        val uid = uidProvider()
        if (uid == null) {
            flowOf(Result.success<DailyHealthData?>(null))
        } else {
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            healthRepository.getDailyHealthData(uid, dateStr)
        }
    }

    val state: StateFlow<ActivityState> = combine(
        _selectedDate,
        _healthData,
        _isRefreshing
    ) { date, healthResult, isRefreshing ->
        val healthData = healthResult.getOrNull() ?: DailyHealthData(date = date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        ActivityState(
            selectedDate = date,
            steps = healthData.steps,
            sleepMinutes = healthData.sleepMinutes,
            workouts = healthData.workouts,
            isRefreshing = isRefreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ActivityState()
    )

    fun changeDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun selectPreviousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun selectNextDay() {
        val today = LocalDate.now()
        if (_selectedDate.value.isBefore(today)) {
            _selectedDate.value = _selectedDate.value.plusDays(1)
        }
    }

    /**
     * Checks Health Connect availability/permissions. When granted, (re)schedules the
     * periodic sync and runs an immediate sync; otherwise flags that the UI should
     * request permissions. No-op when the SDK isn't installed on the device.
     * Takes [Context] from the screen so this stays a plain ViewModel (the default
     * factory can't construct an AndroidViewModel with extra constructor args).
     */
    fun checkPermissionsAndSync(context: Context) {
        val appContext = context.applicationContext
        val healthConnectManager = HealthConnectManager(appContext)
        viewModelScope.launch {
            // getSdkStatus: 1=UNAVAILABLE, 2=PROVIDER_UPDATE_REQUIRED, 3=AVAILABLE
            val sdkStatus = healthConnectManager.getSdkStatus()
            if (!healthConnectManager.isSdkAvailable()) {
                Log.w(TAG, "Health Connect NOT available (sdkStatus=$sdkStatus) — no permission prompt shown")
                _needsPermissions.value = false
                return@launch
            }
            val granted = healthConnectManager.hasAllPermissions()
            Log.i(TAG, "Health Connect available (sdkStatus=$sdkStatus), permissionsGranted=$granted")
            if (granted) {
                _needsPermissions.value = false
                HealthSyncScheduler.schedulePeriodic(appContext)
                HealthSyncScheduler.syncNow(appContext)
                Log.i(TAG, "Permissions granted — periodic + immediate sync enqueued")
            } else {
                _needsPermissions.value = true
                Log.i(TAG, "Permissions missing — requesting Health Connect permissions")
            }
        }
    }

    private companion object {
        const val TAG = "HCSync"
    }

    /** Called by the screen after the permission request returns. */
    fun onPermissionsResult(context: Context) = checkPermissionsAndSync(context)

    /** Triggers the backend insights refresh; the snapshot listeners update the cards. */
    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                insightsRefresher.refresh()
            } catch (_: Exception) {
                // Friendly failure: keep the last known data; the user can retry.
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
