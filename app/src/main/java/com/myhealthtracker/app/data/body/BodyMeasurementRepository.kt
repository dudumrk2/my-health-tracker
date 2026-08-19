package com.myhealthtracker.app.data.body

import com.myhealthtracker.app.data.model.BodyMeasurement
import kotlinx.coroutines.flow.StateFlow

interface BodyMeasurementRepository {
    val bodyMeasurements: StateFlow<List<BodyMeasurement>>
    fun addBodyMeasurement(date: String, weight: Double?, waist: Double?, hips: Double?, note: String)

    /**
     * Seed/update only the weight for [date] without touching other fields
     * (waist/hips/note). Used to mirror the profile's setup weight into the
     * body-measurement history so the dashboard reflects it. Merges rather than
     * overwrites, so a same-day manual measurement keeps its other fields.
     */
    fun seedWeight(date: String, weight: Double)
}
