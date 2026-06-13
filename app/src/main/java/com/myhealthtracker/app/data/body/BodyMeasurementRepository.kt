package com.myhealthtracker.app.data.body

import com.myhealthtracker.app.data.model.BodyMeasurement
import kotlinx.coroutines.flow.StateFlow

interface BodyMeasurementRepository {
    val bodyMeasurements: StateFlow<List<BodyMeasurement>>
    fun addBodyMeasurement(date: String, weight: Double?, waist: Double?, hips: Double?, note: String)
}
