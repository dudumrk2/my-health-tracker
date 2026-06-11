package com.myhealthtracker.app

import com.google.firebase.Timestamp
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.health.SleepSessionInfo
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

import io.mockk.mockk

class ProfileAndHealthUnitTest {

    private val profileRepository = ProfileRepository(mockk(relaxed = true))
    private val healthRepository = HealthRepository(mockk(relaxed = true))

    @Test
    fun testCalculateAge() {
        assertEquals(26, profileRepository.calculateAge(2000, 2026))
        assertEquals(0, profileRepository.calculateAge(2026, 2026))
        assertEquals(0, profileRepository.calculateAge(2030, 2026))
        assertEquals(0, profileRepository.calculateAge(0, 2026))
    }

    @Test
    fun testProfileValidation() {
        // Valid profile
        val validProfile = UserProfile(birthYear = 1995, weightKg = 70.0, heightCm = 175.0)
        assertTrue(profileRepository.validateProfile(validProfile).isSuccess)

        // Invalid birth year
        val invalidYearProfile = UserProfile(birthYear = 1899, weightKg = 70.0, heightCm = 175.0)
        assertTrue(profileRepository.validateProfile(invalidYearProfile).isFailure)

        // Invalid weight (too low)
        val lowWeightProfile = UserProfile(birthYear = 1995, weightKg = 25.0, heightCm = 175.0)
        assertTrue(profileRepository.validateProfile(lowWeightProfile).isFailure)

        // Invalid weight (too high)
        val highWeightProfile = UserProfile(birthYear = 1995, weightKg = 350.0, heightCm = 175.0)
        assertTrue(profileRepository.validateProfile(highWeightProfile).isFailure)

        // Invalid height (too low)
        val lowHeightProfile = UserProfile(birthYear = 1995, weightKg = 70.0, heightCm = 90.0)
        assertTrue(profileRepository.validateProfile(lowHeightProfile).isFailure)

        // Invalid height (too high)
        val highHeightProfile = UserProfile(birthYear = 1995, weightKg = 70.0, heightCm = 260.0)
        assertTrue(profileRepository.validateProfile(highHeightProfile).isFailure)
    }

    @Test
    fun testSleepDurationAggregation() {
        // Empty sessions
        assertEquals(0, healthRepository.aggregateSleepMinutes(emptyList()))

        // Single session of 8 hours (480 mins)
        val start1 = Timestamp(Instant.parse("2026-06-11T00:00:00Z").epochSecond, 0)
        val end1 = Timestamp(Instant.parse("2026-06-11T08:00:00Z").epochSecond, 0)
        val singleSession = listOf(SleepSessionInfo(start1, end1))
        assertEquals(480, healthRepository.aggregateSleepMinutes(singleSession))

        // Multiple non-overlapping sessions (4 hours + 4 hours)
        val start2 = Timestamp(Instant.parse("2026-06-11T12:00:00Z").epochSecond, 0)
        val end2 = Timestamp(Instant.parse("2026-06-11T16:00:00Z").epochSecond, 0)
        val multipleSessions = listOf(
            SleepSessionInfo(start1, end1),
            SleepSessionInfo(start2, end2)
        )
        assertEquals(720, healthRepository.aggregateSleepMinutes(multipleSessions))

        // Overlapping sessions
        val startOverlap = Timestamp(Instant.parse("2026-06-11T06:00:00Z").epochSecond, 0)
        val endOverlap = Timestamp(Instant.parse("2026-06-11T10:00:00Z").epochSecond, 0)
        val overlappingSessions = listOf(
            SleepSessionInfo(start1, end1),
            SleepSessionInfo(startOverlap, endOverlap)
        )
        assertEquals(600, healthRepository.aggregateSleepMinutes(overlappingSessions))
    }
}
