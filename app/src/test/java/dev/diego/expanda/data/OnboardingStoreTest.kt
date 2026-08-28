package dev.diego.expanda.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStoreTest {
    @Test fun `empty library starts tutorial`() {
        assertEquals(OnboardingStatus.ACTIVE, initialOnboardingStatus(hasMatches = false))
    }

    @Test fun `existing library skips tutorial for returning users`() {
        assertEquals(OnboardingStatus.SKIPPED, initialOnboardingStatus(hasMatches = true))
    }

    @Test fun `pre-0_3 upgrade starts tutorial even with snippets`() {
        assertEquals(
            OnboardingStatus.ACTIVE,
            initialOnboardingStatus(hasMatches = true, upgradedFromPre03 = true),
        )
    }

    @Test fun `should show tutorial for fresh install`() {
        assertTrue(
            shouldShowTutorial(
                OnboardingState(status = OnboardingStatus.UNINITIALIZED),
                hasMatches = false,
                upgradedFromPre03 = false,
            ),
        )
    }

    @Test fun `should show tutorial for 0_2 upgrade`() {
        assertTrue(
            shouldShowTutorial(
                OnboardingState(status = OnboardingStatus.UNINITIALIZED),
                hasMatches = true,
                upgradedFromPre03 = true,
            ),
        )
    }

    @Test fun `should replay tutorial when an early 0_3 build auto-skipped it`() {
        assertTrue(
            shouldShowTutorial(
                OnboardingState(status = OnboardingStatus.SKIPPED, tutorialGeneration = 0),
                hasMatches = true,
                upgradedFromPre03 = false,
            ),
        )
    }

    @Test fun `should not replay tutorial after generation is recorded`() {
        assertFalse(
            shouldShowTutorial(
                OnboardingState(
                    status = OnboardingStatus.SKIPPED,
                    tutorialGeneration = CURRENT_TUTORIAL_GENERATION,
                ),
                hasMatches = true,
                upgradedFromPre03 = true,
            ),
        )
    }
}
