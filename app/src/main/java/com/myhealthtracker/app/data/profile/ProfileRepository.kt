package com.myhealthtracker.app.data.profile

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import java.util.Calendar

data class UserProfile(
    val birthYear: Int = 0,
    val weightKg: Double = 0.0,
    val heightCm: Double = 0.0,
    val gender: String = "",
    val themePreference: String = "system",
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
)

fun genderToHebrew(gender: String): String {
    return when (gender) {
        "male" -> "זכר"
        "female" -> "נקבה"
        "other" -> "אחר"
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
                    val createdAtTimestamp = profileMap["createdAt"] as? Timestamp
                    val updatedAtTimestamp = profileMap["updatedAt"] as? Timestamp
                    val profile = UserProfile(
                        birthYear = (profileMap["birthYear"] as? Long)?.toInt() ?: 0,
                        weightKg = (profileMap["weightKg"] as? Double) ?: ((profileMap["weightKg"] as? Long)?.toDouble() ?: 0.0),
                        heightCm = (profileMap["heightCm"] as? Double) ?: ((profileMap["heightCm"] as? Long)?.toDouble() ?: 0.0),
                        gender = (profileMap["gender"] as? String) ?: "",
                        themePreference = (profileMap["themePreference"] as? String) ?: "system",
                        createdAt = createdAtTimestamp?.toDate()?.toInstant(),
                        updatedAt = updatedAtTimestamp?.toDate()?.toInstant()
                    )
                    trySend(Result.success(profile))
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

                    val data = mapOf(
                        "profile" to mapOf(
                            "birthYear" to profile.birthYear,
                            "weightKg" to profile.weightKg,
                            "heightCm" to profile.heightCm,
                            "gender" to profile.gender,
                            "themePreference" to profile.themePreference,
                            "createdAt" to finalCreatedAt,
                            "updatedAt" to Timestamp.now()
                        )
                    )

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
        if (profile.gender != "male" && profile.gender != "female" && profile.gender != "other") {
            return Result.failure(IllegalArgumentException("Gender must be 'male', 'female', or 'other'"))
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
