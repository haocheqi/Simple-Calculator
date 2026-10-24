package com.hpcalc.core.math

import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.SymbolicEvaluator
import com.hpcalc.core.model.SymbolicResult
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** 单项式：变量名到次数。空 map 表示常数项。 */
private typealias Mono = Map<String, Int>

/** 规范化的多项式：单项式 -> 系数。系数为零的项不保存，因此同类项天然合并。 */
private data class Poly(val terms: Map<Mono, BigDecimal>) {

    fun plus(other: Poly): Poly {
        val merged = terms.toMutableMap()
        other.terms.forEach { (mono, coef) ->
            val sum = (merged[mono] ?: BigDecimal.ZERO).add(coef)
            if (sum.compareTo(BigDecimal.ZERO) == 0) merged.remove(mono) else merged[mono] = sum
        }
        return Poly(merged)
    }

    fun negate(): Poly = Poly(terms.mapValues { (_, coef) -> coef.negate() })

    fun minus(other: Poly): Poly = plus(other.negate())

    fun times(other: Poly): Poly {
        val merged = mutableMapOf<Mono, BigDecimal>()
        for ((leftMono, leftCoef) in terms) {
            for ((rightMono, rightCoef) in other.terms) {
                val mono = (leftMono.keys + rightMono.keys)
                    .associateWith { (leftMono[it] ?: 0) + (rightMono[it] ?: 0) }
                    .filterValues { it != 0 }
                // 相乘会产生互相抵消的同类项（例如 (x+1)(x-1) 的 x 项）。
                // v2.0 在这里只做累加不清理，导致输出 "x^2 + 0x - 1"。
                val coefficient = (merged[mono] ?: BigDecimal.ZERO).add(leftCoef.multiply(rightCoef))
                if (coefficient.compareTo(BigDecimal.ZERO) == 0) {
                    merged.remove(mono)
                } else {
                    merged[mono] = coefficient
                }
            }
        }
        return Poly(merged)
    }

    fun pow(exponent: Int): Poly {
        require(exponent >= 0) { "Exponent must be a non-negative integer" }
        var result = constant(BigDecimal.ONE)
        repeat(exponent) { result = result.times(this) }
        return result
    }

    fun substitute(values: Map<String, BigDecimal>): BigDecimal {
        val mc = MathContext(50, RoundingMode.HALF_UP)
        return terms.entries.fold(BigDecimal.ZERO) { acc, (mono, coef) ->
            val termValue = mono.entries.fold(coef) { value, (name, power) ->
                val assigned = values[name] ?: throw MissingValue(name)
                value.multiply(assigned.pow(power, mc), mc)
            }
            acc.add(termValue, mc)
        }
    }

    override fun toString(): String {
        if (terms.isEmpty()) return "0"
        val sorted = terms.entries.sortedWith(
            compareByDescending<Map.Entry<Mono, BigDecimal>> { it.key.values.sum() }
                .thenBy { it.key.keys.joinToString() }
        )
        return sorted.mapIndexed { index, (mono, coef) ->
            val negative = coef < BigDecimal.ZERO
            val magnitude = coef.abs()
            val body = mono.entries.sortedBy { it.key }
                .joinToString("") { (name, power) -> if (power == 1) name else "$name^$power" }
            val coefficient = when {
                body.isEmpty() -> magnitude.stripTrailingZeros().toPlainString()
                magnitude == BigDecimal.ONE -> ""
                else -> magnitude.stripTrailingZeros().toPlainString()
            }
            val sign = when {
                index == 0 -> if (negative) "-" else ""
                negative -> " - "
                else -> " + "
            }
            sign + coefficient + body
        }.joinToString("")
    }

    class MissingValue(val name: String) : RuntimeException("Missing value for $name")

    companion object {
        fun constant(value: BigDecimal) = Poly(mapOf(emptyMap<String, Int>() to value))
        fun variable(name: String) = Poly(mapOf(mapOf(name to 1) to BigDecimal.ONE))
    }
}

/**
 * 多项式化简与代入求值。
 *
 * 支持：整数系数与变量、加减乘、整数幂、括号、隐式乘法（`2x`、`(x+1)(x-1)`）、`ans`。
 * 不支持：多项式除法、分数系数化简为分式、多变量同次项的函数记号。
 */
class PolynomialCalculator : SymbolicEvaluator {

    private sealed interface Token {
        data class Number(val value: BigDecimal) : Token
        data class Name(val value: String) : Token
        data class Op(val value: Char) : Token
        data object Left : Token
        data object Right : Token
        data object End : Token
    }

    private class SymbolicException(val error: MathError) : RuntimeException(error.message)

    private var tokens: List<Token> = emptyList()
    private var pos: Int = 0
    private var depth: Int = 0
    private var ans: BigDecimal? = null

    fun setAns(value: BigDecimal?) {
        ans = value
    }

    override fun simplify(expression: String): SymbolicResult =
        run(expression) { poly -> SymbolicResult.Success(poly.toString()) }

    override fun substitute(expression: String, values: Map<String, BigDecimal>): SymbolicResult =
        run(expression) { poly ->
            SymbolicResult.Success(poly.substitute(values).stripTrailingZeros().toPlainString())
        }

    private inline fun run(expression: String, block: (Poly) -> SymbolicResult): SymbolicResult {
        if (expression.length > MAX_INPUT_LENGTH) return SymbolicResult.Error(MathError.InputTooLong)
        return try {
            tokens = lex(expression)
            pos = 0
            depth = 0
            val poly = parseAdd()
            if (take() !is Token.End) fail(MathError.ParseError("unexpected trailing token"))
            block(poly)
        } catch (e: SymbolicException) {
            SymbolicResult.Error(e.error)
        } catch (e: Poly.MissingValue) {
            SymbolicResult.Error(MathError.DomainError("missing value for '${e.name}'"))
        } catch (e: Exception) {
            SymbolicResult.Error(MathError.ParseError(e.message ?: "invalid polynomial"))
        }
    }

    private fun lex(source: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == ' ' || c == '\t' || c == '\n' -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    i++
                    while (i < source.length && (source[i].isDigit() || source[i] == '.')) i++
                    val text = source.substring(start, i)
                    out += Token.Number(text.toBigDecimalOrNull() ?: fail(MathError.ParseError("invalid number '$text'")))
                }
                c.isLetter() -> {
                    val start = i
                    i++
                    while (i < source.length && source[i].isLetterOrDigit()) i++
                    out += Token.Name(source.substring(start, i))
                }
                else -> when (c) {
                    '+', '-', '*', '/', '^' -> { out += Token.Op(c); i++ }
                    '(' -> { out += Token.Left; i++ }
                    ')' -> { out += Token.Right; i++ }
                    else -> fail(MathError.ParseError("unsupported character '$c'"))
                }
            }
        }
        return out + Token.End
    }

    private fun peek(): Token = tokens[pos]

    private fun take(): Token = tokens[pos++]

    private fun parseAdd(): Poly {
        enter()
        try {
            var value = parseMul()
            while (true) {
                val t = peek()
                if (t is Token.Op && (t.value == '+' || t.value == '-')) {
                    take()
                    val rhs = parseMul()
                    value = if (t.value == '+') value.plus(rhs) else value.minus(rhs)
                } else {
                    return value
                }
            }
        } finally {
            leave()
        }
    }

    private fun parseMul(): Poly {
        var value = parseUnary()
        while (true) {
            val t = peek()
            when {
                t is Token.Op && (t.value == '*' || t.value == '/') -> {
                    take()
                    if (t.value == '/') fail(MathError.Unsupported("polynomial division"))
                    value = value.times(parseUnary())
                }
                // 隐式乘法：2x、(x+1)(x-1)、2(x+3)
                startsPrimary(t) -> value = value.times(parsePow())
                else -> return value
            }
        }
    }

    private fun startsPrimary(token: Token): Boolean =
        token is Token.Number || token is Token.Name || token is Token.Left

    private fun parseUnary(): Poly {
        enter()
        try {
            val t = peek()
            if (t is Token.Op && (t.value == '+' || t.value == '-')) {
                take()
                val value = parseUnary()
                return if (t.value == '-') value.negate() else value
            }
            return parsePow()
        } finally {
            leave()
        }
    }

    /** 与数值求值器同构：`-x^2` 解释为 `-(x^2)`，指数必须是非负整数。 */
    private fun parsePow(): Poly {
        val base = parsePrimary()
        val t = peek()
        if (t is Token.Op && t.value == '^') {
            take()
            val exponentToken = take()
            if (exponentToken !is Token.Number || exponentToken.value.stripTrailingZeros().scale() > 0) {
                fail(MathError.Unsupported("non-integer exponent in a polynomial"))
            }
            return base.pow(exponentToken.value.toInt())
        }
        return base
    }

    private fun parsePrimary(): Poly {
        val t = take()
        return when {
            t is Token.Number -> Poly.constant(t.value)
            t is Token.Name ->
                if (t.value.equals("ans", ignoreCase = true)) {
                    Poly.constant(ans ?: fail(MathError.NotAvailable("Ans")))
                } else {
                    Poly.variable(t.value)
                }
            t is Token.Left -> {
                val value = parseAdd()
                if (take() !is Token.Right) fail(MathError.ParseError("missing ')'"))
                value
            }
            t is Token.End -> fail(MathError.ParseError("expression ended unexpectedly"))
            else -> fail(MathError.ParseError("unexpected token"))
        }
    }

    private fun enter() {
        depth++
        if (depth > MAX_NESTING_DEPTH) fail(MathError.DepthLimitExceeded)
    }

    private fun leave() {
        depth--
    }

    private fun fail(error: MathError): Nothing = throw SymbolicException(error)

    companion object {
        const val MAX_INPUT_LENGTH: Int = 1000
        const val MAX_NESTING_DEPTH: Int = 64
    }
}