package com.hpcalc.core.math

import ch.obermuhlner.math.big.BigDecimalMath
import com.hpcalc.core.model.AngleUnit
import com.hpcalc.core.model.EvaluationContext
import com.hpcalc.core.model.EvaluationResult
import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.MathIssue
import com.hpcalc.core.model.NumericEvaluator
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * 高精度数值求值器。
 *
 * 相对 v2.0 的行为变更（均为缺陷修复，见 docs/ACCEPTANCE.md）：
 *
 * 1. **错误不再逃逸**：v2.0 用 Kotlin `error(...)` 抛 `IllegalStateException`，
 *    而 `evaluate` 只捕获 `IllegalArgumentException` / `ArithmeticException`，
 *    于是 `1+`、`log(`、首次引用 `ans` 都会让进程崩溃。现在所有失败统一收敛为
 *    [MathError]，`evaluate` 保证返回而不抛出。
 * 2. **一元负号优先级**：`-2^2` 由 v2.0 的 `4` 修正为 `-4`（负号作用于整个幂）。
 * 3. **反三角函数角度还原**：v2.0 只把入参从度换到弧度，`asin/acos/atan` 的结果
 *    从未换回角度制。现在 DEGREES 下 `asin(0.5)` 得 `30`。
 * 4. **资源防护**：输入长度、嵌套深度、阶乘上限。深度守卫替代 `StackOverflowError`
 *    —— 那是 `Error` 而非 `Exception`，捕获不到，必须事先阻止。
 */
class HighPrecisionEvaluator : NumericEvaluator {

    private sealed interface Token {
        data class Number(val value: BigDecimal) : Token
        data class Name(val value: String) : Token
        data class Op(val value: Char) : Token
        data object Left : Token
        data object Right : Token
        data object End : Token
    }

    /** 内部错误载体。只在本类内流转，绝不越过 [evaluate] 的边界。 */
    private class MathException(val error: MathError) : RuntimeException(error.message)

    private var tokens: List<Token> = emptyList()
    private var pos: Int = 0
    private var depth: Int = 0
    private var context: EvaluationContext = EvaluationContext()

    private val mc: MathContext get() = context.mathContext

    override fun evaluate(expression: String, context: EvaluationContext): EvaluationResult {
        if (expression.length > MAX_INPUT_LENGTH) return EvaluationResult.Error(MathError.InputTooLong)
        return try {
            this.context = context
            tokens = tokenize(expression)
            pos = 0
            depth = 0
            val value = parseExpression()
            if (peek() !is Token.End) fail(MathError.ParseError("unexpected trailing token"))
            EvaluationResult.Success(value.round(context.mathContext))
        } catch (e: MathException) {
            EvaluationResult.Error(e.error)
        } catch (e: ArithmeticException) {
            EvaluationResult.Error(
                if (isDivisionByZero(e)) MathError.DivisionByZero
                else MathError.Undefined(e.message ?: "arithmetic error")
            )
        } catch (e: Exception) {
            // 兜底：任何未预料到的异常都转成可读错误，绝不冒泡到调用方。
            EvaluationResult.Error(MathError.ParseError(e.message ?: "invalid expression"))
        }
    }

    // ---------------------------------------------------------------- 词法

    private fun tokenize(source: String): List<Token> {
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
                    out += Token.Number(text.toBigDecimalOrNull() ?: fail(MathError.ParseError("invalid number '$text'")))
                }

                c.isLetter() || c == 'π' -> {
                    val start = i
                    i++
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    out += Token.Name(source.substring(start, i))
                }

                else -> when (c) {
                    '+', '-', '*', '/', '^', '%', '!', ',' -> { out += Token.Op(c); i++ }
                    '(' -> { out += Token.Left; i++ }
                    ')' -> { out += Token.Right; i++ }
                    else -> fail(MathError.ParseError("unsupported character '$c'"))
                }
            }
        }
        return out + Token.End
    }

    // ---------------------------------------------------------------- 语法
    //
    // parseExpression -> parseTerm (('+'|'-') parseTerm)*
    // parseTerm       -> parseUnary (('*'|'/'|'%') parseUnary)*
    // parseUnary      -> ('+'|'-') parseUnary | parsePower
    // parsePower      -> parsePostfix ('^' parseUnary)?
    // parsePostfix    -> parsePrimary ('!')*
    // parsePrimary    -> Number | Name ['(' parseExpression ')'] | '(' parseExpression ')'

    private fun peek(): Token = tokens[pos]

    private fun take(): Token = tokens[pos++]

    private fun parseExpression(): BigDecimal {
        enter()
        try {
            var value = parseTerm()
            while (true) {
                val t = peek()
                if (t is Token.Op && (t.value == '+' || t.value == '-')) {
                    take()
                    val rhs = parseTerm()
                    value = if (t.value == '+') value.add(rhs, mc) else value.subtract(rhs, mc)
                } else {
                    return value
                }
            }
        } finally {
            leave()
        }
    }

    private fun parseTerm(): BigDecimal {
        var value = parseUnary()
        while (true) {
            val t = peek()
            if (t !is Token.Op || (t.value != '*' && t.value != '/' && t.value != '%')) return value
            take()
            val rhs = parseUnary()
            value = when (t.value) {
                '*' -> value.multiply(rhs, mc)
                '/' -> {
                    if (rhs.signum() == 0) fail(MathError.DivisionByZero)
                    value.divide(rhs, mc)
                }
                else -> {
                    if (rhs.signum() == 0) fail(MathError.DivisionByZero)
                    value.remainder(rhs, mc)
                }
            }
        }
    }

    private fun parseUnary(): BigDecimal {
        enter()
        try {
            val t = peek()
            if (t is Token.Op && (t.value == '+' || t.value == '-')) {
                take()
                val value = parseUnary()
                return if (t.value == '-') value.negate(mc) else value
            }
            return parsePower()
        } finally {
            leave()
        }
    }

    private fun parsePower(): BigDecimal {
        val base = parsePostfix()
        val t = peek()
        if (t is Token.Op && t.value == '^') {
            take()
            return power(base, parseUnary())
        }
        return base
    }

    private fun parsePostfix(): BigDecimal {
        var value = parsePrimary()
        while (true) {
            val t = peek()
            if (t is Token.Op && t.value == '!') {
                take()
                value = factorial(value)
            } else {
                return value
            }
        }
    }

    private fun parsePrimary(): BigDecimal {
        return when (val t = take()) {
            is Token.Number -> t.value

            is Token.Name -> {
                if (peek() is Token.Left) {
                    take()
                    val args = mutableListOf<BigDecimal>()
                    if (peek() !is Token.Right) {
                        args += parseExpression()
                        while (true) {
                            val separator = peek()
                            if (separator is Token.Op && separator.value == ',') {
                                take()
                                args += parseExpression()
                            } else {
                                break
                            }
                        }
                    }
                    if (take() !is Token.Right) fail(MathError.ParseError("missing ')' after '${t.value}'"))
                    function(t.value, args)
                } else {
                    when (t.value.lowercase()) {
                        "pi", "π" -> BigDecimalMath.pi(mc)
                        "e" -> BigDecimalMath.e(mc)
                        "ans" -> context.ans ?: fail(MathError.NotAvailable("Ans"))
                        else -> fail(MathError.ParseError("unknown name '${t.value}'"))
                    }
                }
            }

            Token.Left -> {
                val value = parseExpression()
                if (take() !is Token.Right) fail(MathError.ParseError("missing ')'"))
                value
            }

            Token.End -> fail(MathError.ParseError("expression ended unexpectedly"))

            else -> fail(MathError.ParseError("unexpected token"))
        }
    }

    // ---------------------------------------------------------------- 运算

    private fun power(base: BigDecimal, exponent: BigDecimal): BigDecimal {
        if (base.signum() == 0 && exponent.signum() == 0) return BigDecimal.ONE
        if (base.signum() == 0 && exponent.signum() < 0) fail(MathError.DivisionByZero)
        val integralExponent = exponent.stripTrailingZeros().scale() <= 0
        if (base.signum() < 0 && !integralExponent) {
            fail(MathError.Undefined("negative base with a fractional exponent"))
        }
        return try {
            BigDecimalMath.pow(base, exponent, mc)
        } catch (e: ArithmeticException) {
            fail(MathError.Undefined(e.message ?: "power"))
        }
    }

    private fun factorial(x: BigDecimal): BigDecimal {
        if (x.stripTrailingZeros().scale() > 0) fail(MathError.DomainError("factorial needs an integer", MathIssue.NeedsInteger))
        if (x.signum() < 0) fail(MathError.DomainError("factorial needs a non-negative integer", MathIssue.NeedsNonNegativeInteger))
        if (x > BigDecimal(MAX_FACTORIAL)) fail(MathError.DomainError("factorial is limited to $MAX_FACTORIAL", MathIssue.FactorialLimit(MAX_FACTORIAL)))
        var result = BigDecimal.ONE
        for (i in 2..x.toInt()) result = result.multiply(BigDecimal(i), mc)
        return result
    }

    /**
     * 函数分派。
     *
     * 参数个数错误的报 `ParseError`（语法层面就不成立），
     * 参数越界的报 `DomainError`（语法成立但数学上不允许），两者在界面上文案不同。
     */
    private fun function(name: String, args: List<BigDecimal>): BigDecimal {
        val n = name.lowercase()
        return when (n) {
            // ---------------------------------------------------------- 三角
            "sin" -> BigDecimalMath.sin(toRadians(unary(n, args)), mc)
            "cos" -> BigDecimalMath.cos(toRadians(unary(n, args)), mc)
            "tan" -> BigDecimalMath.tan(toRadians(unary(n, args)), mc)

            // 反三角：入参是无量纲比值，结果必须按当前角度单位还原。
            "asin" -> {
                val x = unary(n, args)
                requireUnitInterval(x, n)
                fromRadians(BigDecimalMath.asin(x, mc))
            }
            "acos" -> {
                val x = unary(n, args)
                requireUnitInterval(x, n)
                fromRadians(BigDecimalMath.acos(x, mc))
            }
            "atan" -> fromRadians(BigDecimalMath.atan(unary(n, args), mc))
            "atan2" -> {
                val (y, x) = binary(n, args)
                fromRadians(atan2Radians(y, x))
            }

            // 双曲函数不涉及角度换算。
            "sinh" -> BigDecimalMath.sinh(unary(n, args), mc)
            "cosh" -> BigDecimalMath.cosh(unary(n, args), mc)
            "tanh" -> BigDecimalMath.tanh(unary(n, args), mc)

            // ---------------------------------------------------------- 对数与指数
            "ln" -> {
                val x = unary(n, args)
                requirePositive(x, n)
                BigDecimalMath.log(x, mc)
            }
            "log", "log10" -> {
                val x = unary(n, args)
                requirePositive(x, n)
                BigDecimalMath.log10(x, mc)
            }
            "logb" -> {
                val (base, x) = binary(n, args)
                requirePositive(base, n)
                requirePositive(x, n)
                if (base.compareTo(BigDecimal.ONE) == 0) {
                    fail(MathError.DomainError("logarithm base must not be 1", MathIssue.LogBaseOne))
                }
                BigDecimalMath.log(x, mc).divide(BigDecimalMath.log(base, mc), mc)
            }
            "exp" -> BigDecimalMath.exp(unary(n, args), mc)

            // ---------------------------------------------------------- 幂与根
            "sqrt" -> {
                val x = unary(n, args)
                if (x.signum() < 0) fail(MathError.Undefined("square root of a negative number"))
                BigDecimalMath.sqrt(x, mc)
            }
            "cbrt" -> nthRoot(unary(n, args), 3, n)
            "root" -> {
                val (x, degree) = binary(n, args)
                nthRoot(x, requireCount(degree, n, positive = true), n)
            }

            // ---------------------------------------------------------- 舍入
            "abs" -> unary(n, args).abs(mc)
            "floor" -> unary(n, args).setScale(0, RoundingMode.FLOOR)
            "ceil" -> unary(n, args).setScale(0, RoundingMode.CEILING)
            "round" -> unary(n, args).setScale(0, RoundingMode.HALF_UP)

            // ---------------------------------------------------------- 极值
            "min" -> variadic(n, args).minOrNull() ?: fail(MathError.ParseError("min expects at least 1 argument"))
            "max" -> variadic(n, args).maxOrNull() ?: fail(MathError.ParseError("max expects at least 1 argument"))
            "clamp" -> {
                if (args.size != 3) fail(MathError.ParseError("clamp expects exactly 3 arguments"))
                val (x, low, high) = args
                if (low > high) fail(MathError.DomainError("clamp lower bound exceeds upper bound", MathIssue.ClampRange))
                x.coerceIn(low, high)
            }

            // ---------------------------------------------------------- 数论
            "gcd" -> {
                val (a, b) = binary(n, args)
                requireInteger(a, n)
                requireInteger(b, n)
                BigDecimal(a.toBigInteger().gcd(b.toBigInteger()))
            }
            "lcm" -> {
                val (a, b) = binary(n, args)
                requireInteger(a, n)
                requireInteger(b, n)
                if (a.signum() == 0 || b.signum() == 0) {
                    BigDecimal.ZERO
                } else {
                    val left = a.toBigInteger().abs()
                    val right = b.toBigInteger().abs()
                    BigDecimal(left.divide(left.gcd(right)).multiply(right))
                }
            }
            "comb" -> {
                val (total, chosen) = binary(n, args)
                binomial(requireCount(total, n), requireCount(chosen, n))
            }
            "perm" -> {
                val (total, chosen) = binary(n, args)
                permutations(requireCount(total, n), requireCount(chosen, n))
            }

            // ---------------------------------------------------------- 统计
            "sum" -> variadic(n, args).fold(BigDecimal.ZERO) { acc, value -> acc.add(value, mc) }
            "mean", "average" -> {
                val values = variadic(n, args)
                values.fold(BigDecimal.ZERO) { acc, value -> acc.add(value, mc) }
                    .divide(BigDecimal(values.size), mc)
            }
            "median" -> {
                val sorted = variadic(n, args).sorted()
                val middle = sorted.size / 2
                if (sorted.size % 2 == 1) {
                    sorted[middle]
                } else {
                    sorted[middle - 1].add(sorted[middle], mc).divide(BigDecimal(2), mc)
                }
            }
            "stdev" -> {
                val values = variadic(n, args)
                if (values.size < 2) fail(MathError.DomainError("stdev needs at least 2 values"))
                val average = values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v, mc) }
                    .divide(BigDecimal(values.size), mc)
                val squares = values.fold(BigDecimal.ZERO) { acc, v ->
                    val deviation = v.subtract(average, mc)
                    acc.add(deviation.multiply(deviation, mc), mc)
                }
                BigDecimalMath.sqrt(squares.divide(BigDecimal(values.size - 1), mc), mc)
            }

            // ---------------------------------------------------------- 角度换算
            // deg/rad 与当前角度单位无关：把弧度当量换成角度、把角度当量换成弧度。
            "deg" -> unary(n, args).multiply(BigDecimal(180), mc).divide(BigDecimalMath.pi(mc), mc)
            "rad" -> unary(n, args).multiply(BigDecimalMath.pi(mc), mc).divide(BigDecimal(180), mc)

            else -> fail(MathError.ParseError("unknown function '$name'"))
        }
    }

    private fun unary(name: String, args: List<BigDecimal>): BigDecimal {
        if (args.size != 1) fail(MathError.ParseError("$name expects exactly 1 argument"))
        return args[0]
    }

    private fun binary(name: String, args: List<BigDecimal>): Pair<BigDecimal, BigDecimal> {
        if (args.size != 2) fail(MathError.ParseError("$name expects exactly 2 arguments"))
        return args[0] to args[1]
    }

    private fun variadic(name: String, args: List<BigDecimal>): List<BigDecimal> {
        if (args.isEmpty()) fail(MathError.ParseError("$name expects at least 1 argument"))
        return args
    }

    private fun requireInteger(value: BigDecimal, name: String) {
        if (value.stripTrailingZeros().scale() > 0) fail(MathError.DomainError("$name expects integers", MathIssue.NeedsInteger))
    }

    /** 把参数收成一个小整数，超过上限直接拒绝，避免用巨大参数把 UI 卡死。 */
    private fun requireCount(value: BigDecimal, name: String, positive: Boolean = false): Int {
        requireInteger(value, name)
        if (positive && value.signum() <= 0) fail(MathError.DomainError("$name expects a positive integer", MathIssue.NeedsPositive))
        if (!positive && value.signum() < 0) fail(MathError.DomainError("$name expects a non-negative integer", MathIssue.NeedsNonNegativeInteger))
        if (value > BigDecimal(MAX_COUNT_ARGUMENT)) {
            fail(MathError.DomainError("$name argument is too large", MathIssue.ArgumentTooLarge))
        }
        return value.toInt()
    }

    private fun atan2Radians(y: BigDecimal, x: BigDecimal): BigDecimal {
        val pi = BigDecimalMath.pi(mc)
        val halfPi = pi.divide(BigDecimal(2), mc)
        val ratio = if (x.signum() == 0) BigDecimal.ZERO else y.divide(x, mc)
        return when {
            x.signum() > 0 -> BigDecimalMath.atan(ratio, mc)
            x.signum() < 0 && y.signum() >= 0 -> BigDecimalMath.atan(ratio, mc).add(pi, mc)
            x.signum() < 0 && y.signum() < 0 -> BigDecimalMath.atan(ratio, mc).subtract(pi, mc)
            y.signum() > 0 -> halfPi
            y.signum() < 0 -> halfPi.negate()
            else -> fail(MathError.DomainError("atan2(0, 0) is undefined", MathIssue.Atan2Origin))
        }
    }

    private fun nthRoot(x: BigDecimal, degree: Int, name: String): BigDecimal {
        if (degree <= 0) fail(MathError.DomainError("$name expects a positive degree"))
        if (x.signum() == 0) return BigDecimal.ZERO
        if (x.signum() < 0) {
            if (degree % 2 == 0) fail(MathError.Undefined("even root of a negative number"))
            return nthRoot(x.negate(), degree, name).negate()
        }
        return when (degree) {
            1 -> x
            2 -> BigDecimalMath.sqrt(x, mc)
            else -> BigDecimalMath.pow(x, BigDecimal.ONE.divide(BigDecimal(degree), mc), mc)
        }
    }

    /** 二项式系数，用 BigInteger 走整步除法，保证零误差。 */
    private fun binomial(total: Int, chosen: Int): BigDecimal {
        if (chosen < 0 || chosen > total) return BigDecimal.ZERO
        var result = BigInteger.ONE
        for (i in 1..chosen) {
            result = result.multiply(BigInteger.valueOf((total - chosen + i).toLong()))
            result = result.divide(BigInteger.valueOf(i.toLong()))
        }
        return BigDecimal(result)
    }

    private fun permutations(total: Int, chosen: Int): BigDecimal {
        if (chosen < 0 || chosen > total) return BigDecimal.ZERO
        var result = BigInteger.ONE
        for (i in 0 until chosen) {
            result = result.multiply(BigInteger.valueOf((total - i).toLong()))
        }
        return BigDecimal(result)
    }

    private val MAX_COUNT_ARGUMENT = 10_000

    // ---------------------------------------------------------------- 角度换算

    private fun toRadians(x: BigDecimal): BigDecimal = when (context.angleUnit) {
        AngleUnit.RADIANS -> x
        AngleUnit.DEGREES -> x.multiply(BigDecimalMath.pi(mc), mc).divide(BigDecimal(180), mc)
        AngleUnit.GRADIANS -> x.multiply(BigDecimalMath.pi(mc), mc).divide(BigDecimal(200), mc)
    }

    private fun fromRadians(x: BigDecimal): BigDecimal = when (context.angleUnit) {
        AngleUnit.RADIANS -> x
        AngleUnit.DEGREES -> x.multiply(BigDecimal(180), mc).divide(BigDecimalMath.pi(mc), mc)
        AngleUnit.GRADIANS -> x.multiply(BigDecimal(200), mc).divide(BigDecimalMath.pi(mc), mc)
    }

    // ---------------------------------------------------------------- 防护

    private fun enter() {
        depth++
        if (depth > MAX_NESTING_DEPTH) fail(MathError.DepthLimitExceeded)
    }

    private fun leave() {
        depth--
    }

    private fun requireUnitInterval(x: BigDecimal, fn: String) {
        if (x.abs() > BigDecimal.ONE) fail(MathError.DomainError("$fn needs an argument in [-1, 1]", MathIssue.NeedsUnitInterval))
    }

    private fun requirePositive(x: BigDecimal, fn: String) {
        if (x.signum() <= 0) fail(MathError.DomainError("$fn needs a positive argument", MathIssue.NeedsPositive))
    }

    private fun isDivisionByZero(e: ArithmeticException): Boolean {
        val m = e.message ?: return false
        return m.contains("by zero", ignoreCase = true)
    }

    private fun fail(error: MathError): Nothing = throw MathException(error)

    companion object {
        /** 输入长度上限，防止病态输入拖垮 UI 线程。 */
        const val MAX_INPUT_LENGTH: Int = 1000

        /** 嵌套/递归深度上限，替代无法捕获的 `StackOverflowError`。 */
        const val MAX_NESTING_DEPTH: Int = 64

        /**
         * 阶乘上限。v2.0 允许到 1000，但 `1000!` 有 2568 位数字，
         * 渲染层必然溢出；170! 已超出 `Double` 量级，是实用边界。
         */
        const val MAX_FACTORIAL: Int = 170
    }
}