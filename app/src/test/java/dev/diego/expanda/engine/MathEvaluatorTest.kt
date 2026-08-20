package dev.diego.expanda.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathEvaluatorTest {
    @Test fun `respects precedence and parentheses`() {
        assertEquals(14.0, MathEvaluator.evaluate("2 + 3 * 4").getOrThrow(), 0.0)
        assertEquals(20.0, MathEvaluator.evaluate("(2 + 3) * 4").getOrThrow(), 0.0)
    }

    @Test fun `supports unary and right associative power`() {
        assertEquals(-4.0, MathEvaluator.evaluate("-2^2").getOrThrow(), 0.0)
        assertEquals(512.0, MathEvaluator.evaluate("2^3^2").getOrThrow(), 0.0)
    }

    @Test fun `rejects invalid input`() {
        assertTrue(MathEvaluator.evaluate("2 + nope").isFailure)
    }
}
