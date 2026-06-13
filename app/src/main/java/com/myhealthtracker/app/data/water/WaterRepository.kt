package com.myhealthtracker.app.data.water

import kotlinx.coroutines.flow.StateFlow

interface WaterRepository {
    val waterLog: StateFlow<Map<String, Int>>
    fun addWater(date: String, amountMl: Int)
}
