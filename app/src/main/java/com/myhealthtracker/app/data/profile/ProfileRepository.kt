package com.myhealthtracker.app.data.profile

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import java.util.Calendar

/**
 * Manual override of computed goals. Every field is optional; a non-null value
 * takes precedence over the value derived from the profile in [com.myhealthtracker.app.data.goals.GoalCalculator].
 */
data class GoalOverrides(
    val caloriesKcal: Int? = null,
    val steps: Int? = null,
    val proteinG: Int? = null,
    val sleepHours: Int? = null,
    val waterMl: Int? = null
)

data class UserProfile(
    val birthYear: Int = 0,
    val weightKg: Double = 0.0,
    val heightCm: Double = 0.0,
    val gender: String = "",
    val themePreference: String = "system",
    // Self-declared usage goal. Chosen by the user at registration, never inferred.
    val primaryGoal: String = "maintain",      // "lose" | "maintain" | "gain"
    // TDEE activity coefficient selector, chosen by the user.
    val activityLevel: String = "moderate",     // "sedentary" | "light" | "moderate" | "very" | "extra"
    // Optional self-declared focus areas (checkboxes), e.g. "menopause". Never derived from age/gender.
    val focusAreas: List<String> = emptyList(),
    // Optional manual overrides of computed goals.
    val goalOverrides: GoalOverrides? = null,
    val quickActionsEnabled: Boolean = true,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
)

/** Reads a Firestore map (numbers arrive as Long) into a [GoalOverrides], keeping only present fields. */
private fun parseGoalOverrides(map: Map<*, *>): GoalOverrides? {
    fun int(key: String): Int? = (map[key] as? Long)?.toInt() ?: (map[key] as? Double)?.toInt()
    val overrides = GoalOverrides(
        caloriesKcal = int("caloriesKcal"),
        steps = int("steps"),
        proteinG = int("proteinG"),
        sleepHours = int("sleepHours"),
        waterMl = int("waterMl")
    )
    val isEmpty = overrides.caloriesKcal == null && overrides.steps == null &&
        overrides.proteinG == null && overrides.sleepHours == null && overrides.waterMl == null
    return if (isEmpty) null else overrides
}

/**
 * Maps a Firestore `profile` map into a [UserProfile]. Numbers arrive as Long/Double and
 * missing fields fall back to the data-class defaults. Pure and side-effect free so it can
 * be unit tested without the Firestore SDK.
 */
fun mapProfile(profileMap: Map<*, *>): UserProfile {
    val createdAtTimestamp = profileMap["createdAt"] as? Timestamp
    val updatedAtTimestamp = profileMap["updatedAt"] as? Timestamp
    val focusAreas = (profileMap["focusAreas"] as? List<*>)
        ?.filterIsInstance<String>() ?: emptyList()
    val overridesMap = profileMap["goalOverrides"] as? Map<*, *>
    val goalOverrides = overridesMap?.let { parseGoalOverrides(it) }
    return UserProfile(
        birthYear = (profileMap["birthYear"] as? Long)?.toInt() ?: 0,
        weightKg = (profileMap["weightKg"] as? Double) ?: ((profileMap["weightKg"] as? Long)?.toDouble() ?: 0.0),
        heightCm = (profileMap["heightCm"] as? Double) ?: ((profileMap["heightCm"] as? Long)?.toDouble() ?: 0.0),
        gender = (profileMap["gender"] as? String) ?: "",
        themePreference = (profileMap["themePreference"] as? String) ?: "system",
        primaryGoal = (profileMap["primaryGoal"] as? String) ?: "maintain",
        activityLevel = (profileMap["activityLevel"] as? String) ?: "moderate",
        focusAreas = focusAreas,
        goalOverrides = goalOverrides,
        quickActionsEnabled = (profileMap["quickActionsEnabled"] as? Boolean) ?: true,
        createdAt = createdAtTimestamp?.toDate()?.toInstant(),
        updatedAt = updatedAtTimestamp?.toDate()?.toInstant()
    )
}

fun genderToHebrew(gender: String): String {
    return when (gender) {
        "male" -> "זכר"
        "female" -> "נקבה"
        else -> gender
    }
}


interface ProfileRepository {
    fun getUserProfile(uid: String): Flow<Result<UserProfile?>>
    fun saveUserProfile(uid: String, profile: UserProfile): Flow<Result<Unit>>
    fun validateProfile(profile: UserProfile): Result<Unit>
    fun calculateAge(birthYear: Int, currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)): Int
}

class FirestoreProfileRepository(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) : ProfileRepository {

    override fun getUserProfile(uid: String): Flow<Result<UserProfile?>> = callbackFlow {
        val docRef = firestore.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profileMap = snapshot.get("profile") as? Map<*, *>
                if (profileMap != null) {
                    trySend(Result.success(mapProfile(profileMap)))
                } else {
                    trySend(Result.success(null))
                }
            } else {
                trySend(Result.success(null))
            }
        }
        awaitClose { listener.remove() }
    }

    override fun saveUserProfile(uid: String, profile: UserProfile): Flow<Result<Unit>> = callbackFlow {
        val validation = validateProfile(profile)
        if (validation.isFailure) {
            trySend(Result.failure(validation.exceptionOrNull() ?: Exception("Validation failed")))
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(uid)
        
        docRef.get()
            .addOnSuccessListener { snapshot ->
                try {
                    val existingProfile = snapshot.get("profile") as? Map<*, *>
                    val existingCreatedAt = existingProfile?.get("createdAt") as? Timestamp
                    val finalCreatedAt = existingCreatedAt ?: Timestamp.now()

                    val profileData = mutableMapOf<String, Any>(
                        "birthYear" to profile.birthYear,
                        "weightKg" to profile.weightKg,
                        "heightCm" to profile.heightCm,
                        "gender" to profile.gender,
                        "themePreference" to profile.themePreference,
                        "primaryGoal" to profile.primaryGoal,
                        "activityLevel" to profile.activityLevel,
                        "focusAreas" to profile.focusAreas,
                        "quickActionsEnabled" to profile.quickActionsEnabled,
                        "createdAt" to finalCreatedAt,
                        "updatedAt" to Timestamp.now()
                    )
                    if (profile.goalOverrides == null) {
                        profileData["goalOverrides"] = com.google.firebase.firestore.FieldValue.delete()
                    } else {
                        val o = profile.goalOverrides
                        val overridesMap = mutableMapOf<String, Any>()
                        overridesMap["caloriesKcal"] = o.caloriesKcal ?: com.google.firebase.firestore.FieldValue.delete()
                        overridesMap["steps"] = o.steps ?: com.google.firebase.firestore.FieldValue.delete()
                        overridesMap["proteinG"] = o.proteinG ?: com.google.firebase.firestore.FieldValue.delete()
                        overridesMap["sleepHours"] = o.sleepHours ?: com.google.firebase.firestore.FieldValue.delete()
                        overridesMap["waterMl"] = o.waterMl ?: com.google.firebase.firestore.FieldValue.delete()
                        profileData["goalOverrides"] = overridesMap
                    }
                    val data = mapOf("profile" to profileData)

                    docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            trySend(Result.success(Unit))
                            close()
                        }
                        .addOnFailureListener { exception ->
                            trySend(Result.failure(exception))
                            close()
                        }
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                    close()
                }
            }
            .addOnFailureListener { exception ->
                trySend(Result.failure(exception))
                close()
            }
        awaitClose()
    }

    override fun validateProfile(profile: UserProfile): Result<Unit> {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (profile.birthYear < 1900 || profile.birthYear > currentYear) {
            return Result.failure(IllegalArgumentException("Birth year must be between 1900 and $currentYear"))
        }
        if (profile.gender.isEmpty()) {
            return Result.failure(IllegalArgumentException("Gender is required"))
        }
        if (profile.gender != "male" && profile.gender != "female") {
            return Result.failure(IllegalArgumentException("Gender must be 'male' or 'female'"))
        }
        if (profile.weightKg < 30.0 || profile.weightKg > 300.0) {
            return Result.failure(IllegalArgumentException("Weight must be between 30.0 kg and 300.0 kg"))
        }
        if (profile.heightCm < 100.0 || profile.heightCm > 250.0) {
            return Result.failure(IllegalArgumentException("Height must be between 100.0 cm and 250.0 cm"))
        }
        return Result.success(Unit)
    }

    override fun calculateAge(birthYear: Int, currentYear: Int): Int {
        if (birthYear <= 0) return 0
        val age = currentYear - birthYear
        return if (age >= 0) age else 0
    }
}
