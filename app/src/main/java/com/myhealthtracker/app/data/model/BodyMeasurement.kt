package com.myhealthtracker.app.data.model

data class BodyMeasurement(
    val date: String, // yyyy-MM-dd
    val weightKg: Double?,
    val waistCm: Double?,
    val hipsCm: Double?,
    val note: String = ""
)
