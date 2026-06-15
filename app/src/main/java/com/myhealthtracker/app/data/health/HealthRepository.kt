package com.myhealthtracker.app.data.health

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Duration
import java.time.Instant

data class SleepSessionInfo(
    val start: Instant,
    val end: Instant
)

data class ExerciseSessionInfo(
    val type: String,
    val durationMin: Int,
    val startTime: Instant,
    val source: String = "health_connect"
)

data class DailyHealthData(
    val date: String = "",
    val steps: Long = 0L,
    val sleepMinutes: Int = 0,
    val sleepSessions: List<SleepSessionInfo> = emptyList(),
    val workouts: List<ExerciseSessionInfo> = emptyList(),
    val syncedAt: Instant? = null,
    val source: String = "health_connect"
)

interface HealthRepository {
    fun getDailyHealthData(uid: String, date: String): Flow<Result<DailyHealthData?>>
    fun getWeeklyHealthData(uid: String, startDate: String, endDate: String): Flow<Result<List<DailyHealthData>>>
    fun saveDailyHealthData(
        uid: String,
        date: String,
        steps: Long,
        sleepSessions: List<SleepSessionInfo>,
        workouts: List<ExerciseSessionInfo>
    ): Flow<Result<Unit>>
    fun saveManualWorkout(
        uid: String,
        date: String,
        type: String,
        durationMin: Int,
        startTime: Instant
    ): Flow<Result<Unit>>
}

class FirestoreHealthRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : HealthRepository {

    override fun getWeeklyHealthData(uid: String, startDate: String, endDate: String): Flow<Result<List<DailyHealthData>>> = callbackFlow {
        val query = firestore.collection("users")
            .document(uid)
            .collection("healthDaily")
            .whereGreaterThanOrEqualTo("__name__", startDate)
            .whereLessThanOrEqualTo("__name__", endDate)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val data = doc.toObject(DailyHealthDataDto::class.java) ?: return@mapNotNull null
                    DailyHealthData(
                        date = data.date ?: doc.id,
                        steps = data.steps ?: 0L,
                        sleepMinutes = data.sleepMinutes ?: 0,
                        sleepSessions = data.sleepSessions?.map { 
                            SleepSessionInfo(
                                start = it.start?.toDate()?.toInstant() ?: Instant.now(), 
                                end = it.end?.toDate()?.toInstant() ?: Instant.now()
                            ) 
                        } ?: emptyList(),
                        workouts = data.workouts?.map { 
                            ExerciseSessionInfo(
                                type = it.type ?: "Exercise", 
                                durationMin = it.durationMin ?: 0, 
                                startTime = it.startTime?.toDate()?.toInstant() ?: Instant.now(), 
                                source = it.source ?: "health_connect"
                            ) 
                        } ?: emptyList(),
                        syncedAt = data.syncedAt?.toDate()?.toInstant(),
                        source = data.source ?: "health_connect"
                    )
                }
                trySend(Result.success(list))
            } else {
                trySend(Result.success(emptyList()))
            }
        }
        awaitClose { listener.remove() }
    }

    override fun getDailyHealthData(uid: String, date: String): Flow<Result<DailyHealthData?>> = callbackFlow {
        val docRef = firestore.collection("users")
            .document(uid)
            .collection("healthDaily")
            .document(date)

        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.toObject(DailyHealthDataDto::class.java)
                if (data != null) {
                    val healthData = DailyHealthData(
                        date = data.date ?: date,
                        steps = data.steps ?: 0L,
                        sleepMinutes = data.sleepMinutes ?: 0,
                        sleepSessions = data.sleepSessions?.map { 
                            SleepSessionInfo(
                                start = it.start?.toDate()?.toInstant() ?: Instant.now(), 
                                end = it.end?.toDate()?.toInstant() ?: Instant.now()
                            ) 
                        } ?: emptyList(),
                        workouts = data.workouts?.map { 
                            ExerciseSessionInfo(
                                type = it.type ?: "Exercise", 
                                durationMin = it.durationMin ?: 0, 
                                startTime = it.startTime?.toDate()?.toInstant() ?: Instant.now(), 
                                source = it.source ?: "health_connect"
                            ) 
                        } ?: emptyList(),
                        syncedAt = data.syncedAt?.toDate()?.toInstant(),
                        source = data.source ?: "health_connect"
                    )
                    trySend(Result.success(healthData))
                } else {
                    trySend(Result.success(null))
                }
            } else {
                trySend(Result.success(null))
            }
        }
        awaitClose { listener.remove() }
    }

    override fun saveDailyHealthData(
        uid: String,
        date: String,
        steps: Long,
        sleepSessions: List<SleepSessionInfo>,
        workouts: List<ExerciseSessionInfo>
    ): Flow<Result<Unit>> = callbackFlow {
        val docRef = firestore.collection("users")
            .document(uid)
            .collection("healthDaily")
            .document(date)

        val sleepMinutes = aggregateSleepMinutes(sleepSessions)

        val hasHC = workouts.any { it.source == "health_connect" }
        val hasManual = workouts.any { it.source == "manual" }
        val docSource = when {
            hasHC && hasManual -> "mixed"
            hasManual -> "manual"
            else -> "health_connect"
        }

        val data = mapOf(
            "date" to date,
            "steps" to steps,
            "sleepMinutes" to sleepMinutes,
            "sleepSessions" to sleepSessions.map { mapOf(
                "start" to Timestamp(it.start.epochSecond, it.start.nano), 
                "end" to Timestamp(it.end.epochSecond, it.end.nano)
            ) },
            "workouts" to workouts.map { mapOf(
                "type" to it.type, 
                "durationMin" to it.durationMin, 
                "startTime" to Timestamp(it.startTime.epochSecond, it.startTime.nano), 
                "source" to it.source
            ) },
            "syncedAt" to Timestamp.now(),
            "source" to docSource
        )

        docRef.set(data, SetOptions.merge())
            .addOnSuccessListener {
                trySend(Result.success(Unit))
                close()
            }
            .addOnFailureListener { exception ->
                trySend(Result.failure(exception))
                close()
            }
        awaitClose()
    }

    fun aggregateSleepMinutes(sessions: List<SleepSessionInfo>): Int {
        if (sessions.isEmpty()) return 0
        val sorted = sessions.sortedBy { it.start.toEpochMilli() }
        val merged = mutableListOf<SleepSessionInfo>()
        var current = sorted[0]
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.start.toEpochMilli() <= current.end.toEpochMilli()) {
                if (next.end.toEpochMilli() > current.end.toEpochMilli()) {
                    current = SleepSessionInfo(current.start, next.end)
                }
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)

        var totalMin = 0L
        for (m in merged) {
            val diffMs = m.end.toEpochMilli() - m.start.toEpochMilli()
            totalMin += diffMs / (1000 * 60)
        }
        return totalMin.toInt()
    }

    fun mapHealthConnectData(
        steps: Long,
        sleepRecords: List<SleepSessionRecord>,
        exerciseRecords: List<ExerciseSessionRecord>
    ): MappedHealthData {
        val sleepSessions = sleepRecords.map {
            SleepSessionInfo(
                start = Instant.ofEpochSecond(it.startTime.epochSecond, it.startTime.nano.toLong()),
                end = Instant.ofEpochSecond(it.endTime.epochSecond, it.endTime.nano.toLong())
            )
        }

        val workouts = exerciseRecords.map {
            val durationMin = Duration.between(it.startTime, it.endTime).toMinutes().toInt()
            ExerciseSessionInfo(
                type = mapExerciseType(it.exerciseType),
                durationMin = if (durationMin > 0) durationMin else 0,
                startTime = Instant.ofEpochSecond(it.startTime.epochSecond, it.startTime.nano.toLong()),
                source = "health_connect"
            )
        }

        return MappedHealthData(sleepSessions, workouts)
    }

    override fun saveManualWorkout(
        uid: String,
        date: String,
        type: String,
        durationMin: Int,
        startTime: Instant
    ): Flow<Result<Unit>> = callbackFlow {
        val docRef = firestore.collection("users")
            .document(uid)
            .collection("healthDaily")
            .document(date)

        docRef.get()
            .addOnSuccessListener { snapshot ->
                try {
                    val dto = if (snapshot.exists()) snapshot.toObject(DailyHealthDataDto::class.java) else null
                    val existingWorkouts = dto?.workouts?.map {
                        ExerciseSessionInfo(
                            type = it.type ?: "Exercise", 
                            durationMin = it.durationMin ?: 0, 
                            startTime = it.startTime?.toDate()?.toInstant() ?: Instant.now(), 
                            source = it.source ?: "health_connect"
                        )
                    } ?: emptyList()

                    val newWorkout = ExerciseSessionInfo(type, durationMin, startTime, source = "manual")
                    val updatedWorkouts = existingWorkouts + newWorkout

                    val hasHC = updatedWorkouts.any { it.source == "health_connect" }
                    val hasManual = updatedWorkouts.any { it.source == "manual" }
                    val docSource = when {
                        hasHC && hasManual -> "mixed"
                        hasManual -> "manual"
                        else -> "health_connect"
                    }

                    val workoutsData = updatedWorkouts.map {
                        mapOf(
                            "type" to it.type, 
                            "durationMin" to it.durationMin, 
                            "startTime" to Timestamp(it.startTime.epochSecond, it.startTime.nano), 
                            "source" to it.source
                        )
                    }

                    docRef.set(
                        mapOf("workouts" to workoutsData, "source" to docSource, "syncedAt" to Timestamp.now()),
                        SetOptions.merge()
                    )
                        .addOnSuccessListener { trySend(Result.success(Unit)); close() }
                        .addOnFailureListener { e -> trySend(Result.failure(e)); close() }
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                    close()
                }
            }
            .addOnFailureListener { e -> trySend(Result.failure(e)); close() }
        awaitClose()
    }

    private fun mapExerciseType(type: Int): String {
        return when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
            else -> "Exercise"
        }
    }
}

data class MappedHealthData(
    val sleepSessions: List<SleepSessionInfo>,
    val workouts: List<ExerciseSessionInfo>
)

// DTOs for Firestore mapping
class DailyHealthDataDto {
    var date: String? = null
    var steps: Long? = null
    var sleepMinutes: Int? = null
    var sleepSessions: List<SleepSessionInfoDto>? = null
    var workouts: List<ExerciseSessionInfoDto>? = null
    var syncedAt: Timestamp? = null
    var source: String? = null
}

class SleepSessionInfoDto {
    var start: Timestamp? = null
    var end: Timestamp? = null
}

class ExerciseSessionInfoDto {
    var type: String? = null
    var durationMin: Int? = null
    var startTime: Timestamp? = null
    var source: String? = null
}
