package com.hpcalc.core.math

import com.hpcalc.core.model.MathBlock
import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.MathIssue
import com.hpcalc.core.model.UnsupportedFeature
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** 精确有理数结果。 */
sealed interface RationalResult {

    /** 已经约分、分母恒为正的既约分数。 */
    data class Exact(val numerator: BigInteger, val denominator: BigInteger) : RationalResult {
        val isInteger: Boolean get() = denominator == BigInteger.ONE

        /** 分母为 1 时退化成普通数字，否则交给排版层画真分式。 */
        fun toMathBlock(): MathBlock =
            if (isInteger) {
                MathBlock.Number(numerator.toString())
            } else {
                MathBlock.Fraction(
                    numerator = MathBlock.Number(numerator.toString()),
                    denominator = MathBlock.Number(denominator.toString())
                )
            }

        fun toBigDecimal(context: MathContext = MathContext.DECIMAL128): BigDecimal =
            BigDecimal(numerator).divide(BigDecimal(denominator), context)
    }

    data class Error(val error: MathError) : RationalResult
}

/**
 * 精确有理数求值器。
 *
 * 只做**能保证精确**的运算：整数与有限小数、加、减、乘、除、整数次幂、括号。
 * 一旦遇到 sin/ln/√ 这类必然产生无理数的函数，就报 [MathError.Unsupported]，
 * 由调用方回退到小数求值——绝不假装精确。
 *
 * 优先级与数值求值器保持一致（`-2^2 = -4`、`2^3^2 = 512`），
 * 否则同一个表达式在两个显示模式下会得到不同结果。
 */
class RationalEvaluator {

    fun evaluate(expression: String): RationalResult {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return RationalResult.Error(MathError.ParseError("empty expression"))
        if (trimmed.length > MAX_INPUT_LENGTH) return RationalResult.Error(MathError.InputTooLong)
        return try {
            val parser = Parser(trimmed)
            val value = parser.parseExpression().simplified()
            parser.expectEnd()
            RationalResult.Exact(value.numerator, value.denominator)
        } catch (e: ExactException) {
            RationalResult.Error(e.error)
        } catch (e: ArithmeticException) {
            RationalResult.Error(MathError.Undefined(e.message ?: "arithmetic error"))
        } catch (e: StackOverflowError) {
            RationalResult.Error(MathError.DepthLimitExceeded)
        }
    }

    // ------------------------------------------------------------------ 内部

    private data class Fraction(val numerator: BigInteger, val denominator: BigInteger) {

        fun simplified(): Fraction {
            if (denominator.signum() == 0) throw ExactException(MathError.DivisionByZero)
            val divisor = numerator.gcd(denominator)
            var n = numerator.divide(divisor)
            var d = denominator.divide(divisor)
            if (d.signum() < 0) {
                n = n.negate()
                d = d.negate()
            }
            return Fraction(n, d)
        }

        fun plus(other: Fraction): Fraction = Fraction(
            numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
            denominator.multiply(other.denominator)
        )

        fun minus(other: Fraction): Fraction = plus(Fraction(other.numerator.negate(), other.denominator))

        fun times(other: Fraction): Fraction = Fraction(
            numerator.multiply(other.numerator),
            denominator.multiply(other.denominator)
        )

        fun divideBy(other: Fraction): Fraction {
            if (other.numerator.signum() == 0) throw ExactException(MathError.DivisionByZero)
            return Fraction(numerator.multiply(other.denominator), denominator.multiply(other.numerator))
        }

        fun pow(exponent: BigInteger): Fraction {
            if (exponent.signum() == 0) return ONE
            val magnitude = exponent.abs()
            if (magnitude > MAX_EXPONENT) {
                throw ExactException(MathError.DomainError("exponent is too large for an exact result", MathIssue.ArgumentTooLarge))
            }
            val e = magnitude.toInt()
            val n = numerator.pow(e)
            val d = denominator.pow(e)
            return if (exponent.signum() < 0) {
                if (n.signum() == 0) throw ExactException(MathError.DivisionByZero)
                Fraction(d, n)
            } else {
                Fraction(n, d)
            }
        }

        val isInteger: Boolean get() = denominator == BigInteger.ONE

        companion object {
            val ONE = Fraction(BigInteger.ONE, BigInteger.ONE)
        }
    }

    private sealed interface Token {
        data class Num(val value: Fraction) : Token
        data class Name(val value: String) : Token
        data class Op(val value: Char) : Token
        data object Left : Token
        data object Right : Token
        data object End : Token
    }

    private class ExactException(val error: MathError) : RuntimeException(error.message)

    private class Parser(private val source: String) {

        private val tokens: List<Token> = tokenize()
        private var position = 0
        private var depth = 0

        fun expectEnd() {
            if (peek() !is Token.End) throw ExactException(MathError.ParseError("unexpected trailing token"))
        }

        private fun tokenize(): List<Token> {
            val out = mutableListOf<Token>()
            var i = 0
            while (i < source.length) {
                val c = source[i]
                when {
                    c == ' ' || c == '\t' || c == '\n' -> i++

                    c.isDigit() || c == '.' -> {
                        val start = i
                        i++
                        while (i < source.length) {
                            val d = source[i]
                            val exponentSign = (d == '+' || d == '-') && (source[i - 1] == 'e' || source[i - 1] == 'E')
                            if (d.isDigit() || d == '.' || d == 'e' || d == 'E' || exponentSign) i++ else break
                        }
                        val text = source.substring(start, i)
                        val decimal = text.toBigDecimalOrNull()
                            ?: throw ExactException(MathError.ParseError("invalid number '$text'"))
                        out += Token.Num(decimal.toFraction())
                    }

                    c.isLetter() || c == 'π' -> {
                        val start = i
                        i++
                        while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                        out += Token.Name(source.substring(start, i))
                    }

                    else -> when (c) {
                        '+', '-', '*', '/', '^', ',', '%', '!' -> { out += Token.Op(c); i++ }
                        '(' -> { out += Token.Left; i++ }
                        ')' -> { out += Token.Right; i++ }
                        else -> throw ExactException(MathError.ParseError("unsupported character '$c'"))
                    }
                }
            }
            return out + Token.End
        }

        private fun peek(): Token = tokens[position]

        private fun take(): Token = tokens[position++]

        /** 有限小数转精确分数：`0.25` → `1/4`，`1e3` → `1000`。 */
        private fun BigDecimal.toFraction(): Fraction {
            val stripped = stripTrailingZeros()
            return if (stripped.scale() <= 0) {
                Fraction(stripped.toBigIntegerExact(), BigInteger.ONE)
            } else {
                Fraction(stripped.unscaledValue(), BigInteger.TEN.pow(stripped.scale()))
            }
        }

        private fun enter() {
            depth++
            if (depth > MAX_NESTING_DEPTH) throw ExactException(MathError.DepthLimitExceeded)
        }

        private fun leave() {
            depth--
        }

        fun parseExpression(): Fraction {
            enter()
            try {
                var value = parseTerm()
                while (true) {
                    val t = peek()
                    if (t is Token.Op && (t.value == '+' || t.value == '-')) {
                        take()
                        val right = parseTerm()
                        value = if (t.value == '+') value.plus(right) else value.minus(right)
                    } else {
                        break
                    }
                }
                return value
            } finally {
                leave()
            }
        }

        private fun parseTerm(): Fraction {
            var value = parseUnary()
            while (true) {
                val t = peek()
                if (t is Token.Op && (t.value == '*' || t.value == '/' || t.value == '%')) {
                    take()
                    // 取模在有理数域上不成立，明确报不支持而不是悄悄给出小数。
                    if (t.value == '%') throw ExactException(MathError.Unsupported(UnsupportedFeature.MODULO))
                    val right = parseUnary()
                    value = if (t.value == '*') value.times(right) else value.divideBy(right)
                } else {
                    break
                }
            }
            return value
        }

        private fun parseUnary(): Fraction {
            val t = peek()
            if (t is Token.Op && (t.value == '+' || t.value == '-')) {
                enter()
                try {
                    take()
                    val operand = parseUnary()
                    return if (t.value == '-') {
                        Fraction(operand.numerator.negate(), operand.denominator)
                    } else {
                        operand
                    }
                } finally {
                    leave()
                }
            }
            return parsePower()
        }

        private fun parsePower(): Fraction {
            val base = parsePostfix()
            val t = peek()
            if (t is Token.Op && t.value == '^') {
                enter()
                try {
                    take()
                    val exponent = parseUnary()
                    if (!exponent.isInteger) {
                        throw ExactException(MathError.Unsupported(UnsupportedFeature.FRACTIONAL_EXPONENT))
                    }
                    return base.pow(exponent.numerator)
                } finally {
                    leave()
                }
            }
            return base
        }

        private fun parsePostfix(): Fraction {
            var value = parsePrimary()
            while (true) {
                val t = peek()
                if (t is Token.Op && t.value == '!') {
                    throw ExactException(MathError.Unsupported(UnsupportedFeature.FACTORIAL))
                }
                break
            }
            return value
        }

        private fun parsePrimary(): Fraction = when (val t = take()) {
            is Token.Num -> t.value

            is Token.Name -> {
                if (peek() is Token.Left) {
                    // 函数调用一律不支持：sin/ln/√ 都不可能给出精确有理数。
                    throw ExactException(MathError.Unsupported(UnsupportedFeature.FUNCTIONS))
                }
                when (t.value.lowercase()) {
                    "ans" -> throw ExactException(MathError.Unsupported(UnsupportedFeature.ANS))
                    else -> throw ExactException(MathError.Unsupported(UnsupportedFeature.VARIABLE, t.value))
                }
            }

            Token.Left -> {
                val value = parseExpression()
                if (take() !is Token.Right) throw ExactException(MathError.ParseError("missing ')'"))
                value
            }

            Token.End -> throw ExactException(MathError.ParseError("expression ended unexpectedly"))

            else -> throw ExactException(MathError.ParseError("unexpected token"))
        }
    }

    private companion object {
        const val MAX_INPUT_LENGTH = 1000
        const val MAX_NESTING_DEPTH = 64
        val MAX_EXPONENT = BigInteger.valueOf(10_000)
    }
}