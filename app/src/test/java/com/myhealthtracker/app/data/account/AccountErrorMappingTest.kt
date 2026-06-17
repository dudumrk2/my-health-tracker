package com.myhealthtracker.app.data.account

import com.google.firebase.functions.FirebaseFunctionsException
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountErrorMappingTest {

    private fun exceptionWith(code: FirebaseFunctionsException.Code): FirebaseFunctionsException {
        val e = mockk<FirebaseFunctionsException>()
        every { e.code } returns code
        return e
    }

    @Test
    fun unauthenticatedMapsToReauthMessage() {
        val msg = mapDeleteAccountError(exceptionWith(FirebaseFunctionsException.Code.UNAUTHENTICATED))
        assertEquals("נדרשת התחברות מחדש.", msg)
    }

    @Test
    fun otherCodesMapToGenericMessage() {
        val msg = mapDeleteAccountError(exceptionWith(FirebaseFunctionsException.Code.INTERNAL))
        assertEquals("לא ניתן למחוק את החשבון כרגע. נסה שוב מאוחר יותר.", msg)
    }
}
