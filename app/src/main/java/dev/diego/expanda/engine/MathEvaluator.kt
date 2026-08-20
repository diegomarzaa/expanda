package dev.diego.expanda.engine

import kotlin.math.pow

object MathEvaluator {
    fun evaluate(expression: String): Result<Double> = runCatching {
        Parser(expression).parse()
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun parse(): Double {
            require(source.isNotBlank()) { "Expression is empty" }
            val result = expression()
            skipWhitespace()
            require(index == source.length) { "Unexpected '${source[index]}' at position $index" }
            require(result.isFinite()) { "Result is not finite" }
            return result
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipWhitespace()
                value = when {
                    consume('+') -> value + term()
                    consume('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = unary()
            while (true) {
                skipWhitespace()
                value = when {
                    consume('*') -> value * unary()
                    consume('/') -> value / unary()
                    consume('%') -> value % unary()
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            val base = primary()
            skipWhitespace()
            return if (consume('^')) base.pow(unary()) else base
        }

        private fun unary(): Double {
            skipWhitespace()
            return when {
                consume('+') -> unary()
                consume('-') -> -unary()
                else -> power()
            }
        }

        private fun primary(): Double {
            skipWhitespace()
            return when {
                consume('(') -> expression().also {
                    skipWhitespace()
                    require(consume(')')) { "Missing ')'" }
                }
                else -> number()
            }
        }

        private fun number(): Double {
            skipWhitespace()
            val start = index
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
            require(start != index) { "Expected a number at position $index" }
            return source.substring(start, index).toDouble()
        }

        private fun consume(expected: Char): Boolean {
            if (index < source.length && source[index] == expected) {
                index++
                return true
            }
            return false
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }
    }
}
