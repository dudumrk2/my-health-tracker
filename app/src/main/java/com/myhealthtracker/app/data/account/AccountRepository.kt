package com.myhealthtracker.app.data.account

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/** Thrown when account deletion fails; the message is user-facing Hebrew. */
class AccountDeletionException(message: String) : Exception(message)

/**
 * Pure mapping of a callable failure to a user-facing Hebrew message. No RESOURCE_EXHAUSTED branch
 * (unlike the AI-bound analyzers): deleteUserData is a lightweight admin op, so quota exhaustion
 * isn't an expected failure mode here.
 */
fun mapDeleteAccountError(e: FirebaseFunctionsException): String = when (e.code) {
    FirebaseFunctionsException.Code.UNAUTHENTICATED -> "נדרשת התחברות מחדש."
    else -> "לא ניתן למחוק את החשבון כרגע. נסה שוב מאוחר יותר."
}

/** Permanently deletes the signed-in user's account and all their data via the `deleteUserData` Cloud Function. */
interface AccountRepository {
    suspend fun deleteAccount()
}

class FunctionsAccountRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : AccountRepository {

    override suspend fun deleteAccount() {
        try {
            functions.getHttpsCallable("deleteUserData").call().await()
        } catch (e: FirebaseFunctionsException) {
            throw AccountDeletionException(mapDeleteAccountError(e))
        }
    }
}
