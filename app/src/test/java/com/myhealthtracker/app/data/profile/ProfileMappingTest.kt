package com.myhealthtracker.app.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [mapProfile] — the pure Firestore-map → [UserProfile] parser. Focused on
 * the `quickActionsEnabled` field added for the Quick Actions notification.
 */
class ProfileMappingTest {

    private fun baseMap(): MutableMap<String, Any> = mutableMapOf(
        "birthYear" to 1990L,
        "weightKg" to 70.0,
        "heightCm" to 175.0,
        "gender" to "male"
    )

    @Test
    fun quickActionsEnabled_defaultsToTrueWhenAbsent() {
        val profile = mapProfile(baseMap())
        assertTrue(profile.quickActionsEnabled)
    }

    @Test
    fun quickActionsEnabled_parsedWhenTrue() {
        val map = baseMap().apply { put("quickActionsEnabled", true) }
        assertTrue(mapProfile(map).quickActionsEnabled)
    }

    @Test
    fun quickActionsEnabled_parsedWhenFalse() {
        val map = baseMap().apply { put("quickActionsEnabled", false) }
        assertFalse(mapProfile(map).quickActionsEnabled)
    }

    @Test
    fun quickActionsEnabled_defaultsToTrueWhenWrongType() {
        // A malformed (non-Boolean) value must not crash; it falls back to the default.
        val map = baseMap().apply { put("quickActionsEnabled", "yes") }
        assertTrue(mapProfile(map).quickActionsEnabled)
    }

    @Test
    fun otherFields_stillParsedAlongsideNewField() {
        val map = baseMap().apply { put("quickActionsEnabled", false) }
        val profile = mapProfile(map)
        assertEquals(1990, profile.birthYear)
        assertEquals(70.0, profile.weightKg, 0.0)
        assertEquals(175.0, profile.heightCm, 0.0)
        assertEquals("male", profile.gender)
    }
}
