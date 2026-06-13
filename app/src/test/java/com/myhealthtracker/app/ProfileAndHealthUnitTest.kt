package com.myhealthtracker.app

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import com.google.firebase.Timestamp
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.health.SleepSessionInfo
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ProfileAndHealthUnitTest {

    private val profileRepository = ProfileRepository(mockk(relaxed = true))
    private val healthRepository = HealthRepository(mockk(relaxed = true))

    // ── Age ──────────────────────────────────────────────────────────────────

    @Test
    fun testCalculateAge() {
        assertEquals(26, profileRepository.calculateAge(2000, 2026))
        assertEquals(0, profileRepository.calculateAge(2026, 2026))
        assertEquals(0, profileRepository.calculateAge(2030, 2026))
        assertEquals(0, profileRepository.calculateAge(0, 2026))
    }

    // ── Profile validation ───────────────────────────────────────────────────

    @Test
    fun testProfileValidation() {
        assertTrue(profileRepository.validateProfile(UserProfile(1995, 70.0, 175.0, gender = "Male")).isSuccess)
        assertTrue(profileRepository.validateProfile(UserProfile(1899, 70.0, 175.0, gender = "Male")).isFailure)
        assertTrue(profileRepository.validateProfile(UserProfile(1995, 25.0, 175.0, gender = "Male")).isFailure)
        assertTrue(profileRepository.validateProfile(UserProfile(1995, 350.0, 175.0, gender = "Male")).isFailure)
        assertTrue(profileRepository.validateProfile(UserProfile(1995, 70.0, 90.0, gender = "Male")).isFailure)
        assertTrue(profileRepository.validateProfile(UserProfile(1995, 70.0, 260.0, gender = "Male")).isFailure)
    }

    @Test
    fun testProfileValidation_gender() {
        // Empty gender fails
        assertTrue(profileRepository.validateProfile(UserProfile(1995, 70.0, 175.0, gender = "")).isFailure)
        val error = profileRepository.validateProfile(UserProfile(1995, 70.0, 175.0, gender = "")).exceptionOrNull()
        assertEquals("Gender is required", error?.message)
        // Valid gender values succeed
        assertTrue(profileRepository.validateProfile(UserProfile(1995, 70.0, 175.0, gender = "Male")).isSuccess)
        assertTrue(profileRepository.validateProfile(UserProfile(1995, 70.0, 175.0, gender = "Female")).isSuccess)
        assertTrue(profileRepository.validateProfile(UserProfile(1995, 70.0, 175.0, gender = "Other")).isSuccess)
    }

    // ── Sleep aggregation ────────────────────────────────────────────────────

    @Test
    fun testSleepDurationAggregation() {
        assertEquals(0, healthRepository.aggregateSleepMinutes(emptyList()))

        val start1 = Timestamp(Instant.parse("2026-06-11T00:00:00Z").epochSecond, 0)
        val end1   = Timestamp(Instant.parse("2026-06-11T08:00:00Z").epochSecond, 0)
        assertEquals(480, healthRepository.aggregateSleepMinutes(listOf(SleepSessionInfo(start1, end1))))

        val start2 = Timestamp(Instant.parse("2026-06-11T12:00:00Z").epochSecond, 0)
        val end2   = Timestamp(Instant.parse("2026-06-11T16:00:00Z").epochSecond, 0)
        assertEquals(720, healthRepository.aggregateSleepMinutes(listOf(SleepSessionInfo(start1, end1), SleepSessionInfo(start2, end2))))

        val startOverlap = Timestamp(Instant.parse("2026-06-11T06:00:00Z").epochSecond, 0)
        val endOverlap   = Timestamp(Instant.parse("2026-06-11T10:00:00Z").epochSecond, 0)
        assertEquals(600, healthRepository.aggregateSleepMinutes(listOf(SleepSessionInfo(start1, end1), SleepSessionInfo(startOverlap, endOverlap))))
    }

    // ── Manual workout source field ──────────────────────────────────────────

    @Test
    fun testManualWorkout_hasSourceManual() {
        val todayStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        val timestamp = com.google.firebase.Timestamp.now()
        com.myhealthtracker.app.data.FakeRepository.addWorkout(todayStr, "ריצה", 30, timestamp)
        val data = com.myhealthtracker.app.data.FakeRepository.getDailyHealthData(todayStr)
        val saved = data.workouts.lastOrNull { it.type == "ריצה" && it.source == "manual" }
        assertNotNull(saved)
        assertEquals(30, saved!!.durationMin)
    }

    @Test
    fun testMixedSource_whenHealthConnectAndManualWorkoutsExist() {
        val date = "2099-01-01"
        val hcTimestamp = com.google.firebase.Timestamp.now()
        val manualTimestamp = com.google.firebase.Timestamp.now()
        // Seed an HC workout via saveDailyHealthData
        com.myhealthtracker.app.data.FakeRepository.saveDailyHealthData(
            date, 5000L, 420, emptyList(),
            listOf(healthRepository.mapHealthConnectData(0L, emptyList(), emptyList()).let {
                com.myhealthtracker.app.data.health.ExerciseSessionInfo("Walking", 20, hcTimestamp, "health_connect")
            })
        )
        // Add a manual workout on the same day
        com.myhealthtracker.app.data.FakeRepository.addWorkout(date, "Yoga", 30, manualTimestamp)
        val data = com.myhealthtracker.app.data.FakeRepository.getDailyHealthData(date)
        assertEquals("mixed", data.source)
        assertTrue(data.workouts.any { it.source == "health_connect" })
        assertTrue(data.workouts.any { it.source == "manual" })
    }

    @Test
    fun testManualWorkout_idempotentDayDocument() {
        val date = "2099-01-02"
        val ts = com.google.firebase.Timestamp.now()
        com.myhealthtracker.app.data.FakeRepository.addWorkout(date, "Cycling", 45, ts)
        com.myhealthtracker.app.data.FakeRepository.addWorkout(date, "Cycling", 45, ts)
        val data = com.myhealthtracker.app.data.FakeRepository.getDailyHealthData(date)
        // Both saves append (expected for manual entry); document source stays "manual"
        assertEquals("manual", data.source)
    }

    // ── mapHealthConnectData ─────────────────────────────────────────────────

    @Test
    fun testMapHealthConnectData_emptyInputsReturnsEmpty() {
        val result = healthRepository.mapHealthConnectData(0L, emptyList(), emptyList())
        assertTrue(result.sleepSessions.isEmpty())
        assertTrue(result.workouts.isEmpty())
    }

    @Test
    fun testMapHealthConnectData_sleepRecordsMapped() {
        val startInstant = Instant.parse("2026-06-11T00:00:00Z")
        val endInstant   = Instant.parse("2026-06-11T08:00:00Z")
        val sleepRecord = mockk<SleepSessionRecord> {
            every { startTime } returns startInstant
            every { endTime }   returns endInstant
        }

        val result = healthRepository.mapHealthConnectData(0L, listOf(sleepRecord), emptyList())

        assertEquals(1, result.sleepSessions.size)
        assertEquals(startInstant.epochSecond, result.sleepSessions[0].start.seconds)
        assertEquals(endInstant.epochSecond,   result.sleepSessions[0].end.seconds)
    }

    @Test
    fun testMapHealthConnectData_exerciseTypeMappingAndDuration() {
        val start = Instant.parse("2026-06-11T07:00:00Z")
        val end   = Instant.parse("2026-06-11T07:30:00Z") // 30 min

        fun makeExercise(type: Int) = mockk<ExerciseSessionRecord> {
            every { exerciseType } returns type
            every { startTime }    returns start
            every { endTime }      returns end
        }

        val result = healthRepository.mapHealthConnectData(
            steps = 0L,
            sleepRecords = emptyList(),
            exerciseRecords = listOf(
                makeExercise(ExerciseSessionRecord.EXERCISE_TYPE_WALKING),
                makeExercise(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING),
                makeExercise(999)
            )
        )

        assertEquals(3, result.workouts.size)
        assertEquals("Walking",  result.workouts[0].type)
        assertEquals("Running",  result.workouts[1].type)
        assertEquals("Exercise", result.workouts[2].type)
        assertEquals(30, result.workouts[0].durationMin)
        assertEquals(start.epochSecond, result.workouts[0].startTime.seconds)
    }
}
