package dev.diego.expanda.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingStoreTest {
    @Test
    fun emptyLibraryStartsTutorial() {
        assertEquals(OnboardingStatus.ACTIVE, initialOnboardingStatus(hasMatches = false))
    }

    @Test
    fun existingLibraryDoesNotInterruptUser() {
        assertEquals(OnboardingStatus.SKIPPED, initialOnboardingStatus(hasMatches = true))
    }
}
