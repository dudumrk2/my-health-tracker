package com.myhealthtracker.app.ui.dashboard

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.myhealthtracker.app.data.auth.AuthManager
import com.myhealthtracker.app.data.health.DailyHealthData
import com.myhealthtracker.app.data.health.HealthConnectManager
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.sync.HealthSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(val healthData: DailyHealthData?, val hasProfile: Boolean) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

class DashboardViewModel(
    application: Application,
    private val authManager: AuthManager = AuthManager(),
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val healthRepository: HealthRepository = HealthRepository(),
    private val healthConnectManager: HealthConnectManager = HealthConnectManager(application)
) : AndroidViewModel(application) {

    val permissions = healthConnectManager.permissions

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _healthConnectAvailable = MutableStateFlow(true)
    val healthConnectAvailable: StateFlow<Boolean> = _healthConnectAvailable.asStateFlow()

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    init {
        checkHealthConnectStatus()
        observeHealthData()
        schedulePeriodicSync()
    }

    fun checkHealthConnectStatus() {
        viewModelScope.launch {
            _healthConnectAvailable.value = healthConnectManager.isSdkAvailable()
            if (_healthConnectAvailable.value) {
                _hasPermissions.value = healthConnectManager.hasAllPermissions()
            }
        }
    }

    private fun observeHealthData() {
        val uid = authManager.currentUser?.uid
        if (uid == null) {
            _uiState.value = DashboardUiState.Error("User not logged in")
            return
        }

        val dateStr = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE)

        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading

            combine(
                profileRepository.getUserProfile(uid),
                healthRepository.getDailyHealthData(uid, dateStr)
            ) { profileResult, healthResult ->
                val hasProfile = profileResult.isSuccess && profileResult.getOrNull() != null
                if (healthResult.isSuccess) {
                    DashboardUiState.Success(healthResult.getOrNull(), hasProfile)
                } else {
                    DashboardUiState.Error(
                        healthResult.exceptionOrNull()?.message ?: "Failed to fetch health data"
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun triggerManualSync() {
        val uid = authManager.currentUser?.uid ?: return
        if (!healthConnectManager.isSdkAvailable() || !hasPermissions.value) {
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val zoneId = ZoneId.systemDefault()
                val today = LocalDate.now(zoneId)
                val startOfDay = today.atStartOfDay(zoneId).toInstant()
                val endOfDay = today.plusDays(1).atStartOfDay(zoneId).toInstant()

                val steps = healthConnectManager.readDailySteps(startOfDay, endOfDay)
                val sleep = healthConnectManager.readSleepSessions(startOfDay, endOfDay)
                val workouts = healthConnectManager.readExerciseSessions(startOfDay, endOfDay)

                val mapped = healthRepository.mapHealthConnectData(steps, sleep, workouts)
                val dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

                val result = healthRepository.saveDailyHealthData(
                    uid = uid,
                    date = dateStr,
                    steps = steps,
                    sleepSessions = mapped.sleepSessions,
                    workouts = mapped.workouts
                ).first()

                if (result.isFailure) {
                    Log.e("DashboardViewModel", "Sync failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error in manual sync", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(getApplication())
            .enqueueUniquePeriodicWork(
                "HealthConnectSyncWork",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
    }
}
