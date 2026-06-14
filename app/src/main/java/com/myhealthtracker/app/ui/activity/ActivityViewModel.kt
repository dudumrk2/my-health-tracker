package com.myhealthtracker.app.ui.activity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.health.HealthConnectManager
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.health.ExerciseSessionInfo
import com.myhealthtracker.app.data.health.DailyHealthData
import com.myhealthtracker.app.data.insights.InsightsRefresher
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
    application: Application,
    private val healthRepository: HealthRepository = AppContainer.healthRepository,
    private val insightsRefresher: InsightsRefresher = AppContainer.insightsRefresher,
    private val uidProvider: () -> String? = { AppContainer.currentUid() }
) : AndroidViewModel(application) {

    private val healthConnectManager = HealthConnectManager(application)

    /** Health Connect permission strings the UI launcher must request. */
    val healthPermissions: Set<String> = healthConnectManager.permissions

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
     */
    fun checkPermissionsAndSync() {
        viewModelScope.launch {
            if (!healthConnectManager.isSdkAvailable()) {
                _needsPermissions.value = false
                return@launch
            }
            if (healthConnectManager.hasAllPermissions()) {
                _needsPermissions.value = false
                val context = getApplication<Application>()
                HealthSyncScheduler.schedulePeriodic(context)
                HealthSyncScheduler.syncNow(context)
            } else {
                _needsPermissions.value = true
            }
        }
    }

    /** Called by the screen after the permission request returns. */
    fun onPermissionsResult() = checkPermissionsAndSync()

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
