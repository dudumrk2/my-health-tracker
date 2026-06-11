package com.myhealthtracker.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Auth : NavKey
@Serializable data object Profile : NavKey
@Serializable data object Dashboard : NavKey
