package com.hpcalc.core.model

/**
 * 「不支持」的具体能力。用枚举而不是自由文本，界面才能逐条翻译。
 */
enum class UnsupportedFeature {
    MATRIX_PANEL,
    POLYNOMIAL_DIVISION,
    POLYNOMIAL_NON_INTEGER_EXPONENT,
    MODULO,
    FRACTIONAL_EXPONENT,
    FACTORIAL,
    FUNCTIONS,
    ANS,
    VARIABLE
}

/**
 * 错误的可本地化原因。
 *
 * [MathError] 里 data class 的 `detail` 是英文技术描述，**只用于日志与断言，不展示给用户**；
 * 需要让用户看到的具体原因放在这里，由界面映射到 `strings.xml`。
 */
sealed interface MathIssue {
    data object NeedsInteger : MathIssue
    data object NeedsNonNegativeInteger : MathIssue
    data class FactorialLimit(val max: Int) : MathIssue
    data object NeedsUnitInterval : MathIssue
    data object NeedsPositive : MathIssue
    data object LogBaseOne : MathIssue
    data class MissingVariable(val name: String) : MathIssue
    data object ClampRange : MathIssue
    data object Atan2Origin : MathIssue
    data object NeedsSquareMatrix : MathIssue
    data object MatrixShapeMismatch : MathIssue
    data object InvalidMatrixCell : MathIssue
    data object ArgumentTooLarge : MathIssue
}

/**
 * 结构化计算错误。
 *
 * v2.0 的错误是裸字符串，且解析器用 Kotlin `error(...)`（抛 `IllegalStateException`）
 * 而求值入口只捕获 `IllegalArgumentException` / `ArithmeticException`，
 * 导致 `1+` 这类输入直接把进程炸掉。v3.0 起错误一律表达为这个密封类型，
 * 求值入口不再允许任何异常逃逸到调用方。
 *
 * [message] 是英文兜底文案，供日志与未本地化的调用点使用；
 * 面向用户的文案由界面层按错误类型映射到 `strings.xml`（见 `:feature:calculator` 的
 * `MathError.toMessage()`）。
 */
sealed interface MathError {
    val message: String

    /** 除数为零，含 `/ 0` 与 `% 0`。 */
    data object DivisionByZero : MathError {
        override val message: String = "Division by zero"
    }

    /** 数学上无定义：负数的偶次根等。 */
    data class Undefined(val detail: String, val issue: MathIssue? = null) : MathError {
        override val message: String = "Undefined: $detail"
    }

    /** 表达式语法不成立。 */
    data class ParseError(val detail: String, val issue: MathIssue? = null) : MathError {
        override val message: String = "Invalid expression: $detail"
    }

    /** 语法成立但入参越界，例如 `asin(2)`、`171!`。 */
    data class DomainError(val detail: String, val issue: MathIssue? = null) : MathError {
        override val message: String = "Out of domain: $detail"
    }

    /** 语法成立但该能力尚未实现，例如多项式除法。 */
    data class Unsupported(
        val feature: UnsupportedFeature,
        val argument: String? = null
    ) : MathError {
        override val message: String =
            "Unsupported: $feature" + (if (argument == null) "" else " ($argument)")
    }

    /** 矩阵尺寸不满足运算前提。 */
    data class DimensionMismatch(val detail: String, val issue: MathIssue? = null) : MathError {
        override val message: String = "Matrix dimension mismatch: $detail"
    }

    /** 方阵不可逆。 */
    data object SingularMatrix : MathError {
        override val message: String = "Matrix is singular"
    }

    /** 嵌套层数超过上限，用于替代 `StackOverflowError`。 */
    data object DepthLimitExceeded : MathError {
        override val message: String = "Expression nests too deeply"
    }

    /** 输入长度超过上限。 */
    data object InputTooLong : MathError {
        override val message: String = "Expression is too long"
    }

    /** 依赖的前值尚不可用，例如首次计算就引用 `ans`。 */
    data class NotAvailable(val what: String) : MathError {
        override val message: String = "$what is not available yet"
    }
}