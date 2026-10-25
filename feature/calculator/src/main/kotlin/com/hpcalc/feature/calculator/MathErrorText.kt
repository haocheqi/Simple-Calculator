package com.hpcalc.feature.calculator

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.hpcalc.core.mathui.LocalMathErrorText
import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.MathIssue
import com.hpcalc.core.model.UnsupportedFeature

/**
 * 一条待本地化的错误文案：资源 id 加上格式参数。
 *
 * 用结构化结果而不是字符串，是为了让「映射是否完整」可以被单元测试断言——
 * 反过来说，任何一条错误只要没有被映射，测试就会失败，不会悄悄漏成英文。
 */
data class ErrorMessage(@StringRes val resId: Int, val args: List<Any> = emptyList())

/** 按错误类型映射到资源。`detail` 是英文技术描述，刻意不参与展示。 */
fun MathError.toMessage(): ErrorMessage = when (this) {
    MathError.DivisionByZero -> ErrorMessage(R.string.error_division_by_zero)
    MathError.SingularMatrix -> ErrorMessage(R.string.error_singular_matrix)
    MathError.DepthLimitExceeded -> ErrorMessage(R.string.error_depth_limit)
    MathError.InputTooLong -> ErrorMessage(R.string.error_input_too_long)
    is MathError.Undefined -> issue.toMessageOr(R.string.error_undefined)
    is MathError.ParseError -> issue.toMessageOr(R.string.error_parse)
    is MathError.DomainError -> issue.toMessageOr(R.string.error_domain)
    is MathError.DimensionMismatch -> issue.toMessageOr(R.string.error_dimension)
    is MathError.NotAvailable -> ErrorMessage(R.string.error_not_available, listOf(what))
    is MathError.Unsupported -> feature.toMessage(argument)
}

private fun MathIssue?.toMessageOr(@StringRes fallback: Int): ErrorMessage =
    this?.toMessage() ?: ErrorMessage(fallback)

fun MathIssue.toMessage(): ErrorMessage = when (this) {
    MathIssue.NeedsInteger -> ErrorMessage(R.string.error_needs_integer)
    MathIssue.NeedsNonNegativeInteger -> ErrorMessage(R.string.error_needs_non_negative)
    is MathIssue.FactorialLimit -> ErrorMessage(R.string.error_factorial_limit, listOf(max))
    MathIssue.NeedsUnitInterval -> ErrorMessage(R.string.error_needs_unit_interval)
    MathIssue.NeedsPositive -> ErrorMessage(R.string.error_needs_positive)
    MathIssue.LogBaseOne -> ErrorMessage(R.string.error_log_base_one)
    is MathIssue.MissingVariable -> ErrorMessage(R.string.error_missing_variable, listOf(name))
    MathIssue.ClampRange -> ErrorMessage(R.string.error_clamp_range)
    MathIssue.Atan2Origin -> ErrorMessage(R.string.error_atan2_origin)
    MathIssue.NeedsSquareMatrix -> ErrorMessage(R.string.error_needs_square)
    MathIssue.MatrixShapeMismatch -> ErrorMessage(R.string.error_shape_mismatch)
    MathIssue.ArgumentTooLarge -> ErrorMessage(R.string.error_argument_too_large)
}

private fun UnsupportedFeature.toMessage(argument: String?): ErrorMessage = when (this) {
    UnsupportedFeature.MATRIX_PANEL -> ErrorMessage(R.string.unsupported_matrix_panel)
    UnsupportedFeature.POLYNOMIAL_DIVISION -> ErrorMessage(R.string.unsupported_polynomial_division)
    UnsupportedFeature.POLYNOMIAL_NON_INTEGER_EXPONENT ->
        ErrorMessage(R.string.unsupported_polynomial_exponent)
    UnsupportedFeature.MODULO -> ErrorMessage(R.string.unsupported_modulo)
    UnsupportedFeature.FRACTIONAL_EXPONENT -> ErrorMessage(R.string.unsupported_fractional_exponent)
    UnsupportedFeature.FACTORIAL -> ErrorMessage(R.string.unsupported_factorial)
    UnsupportedFeature.FUNCTIONS -> ErrorMessage(R.string.unsupported_functions)
    UnsupportedFeature.ANS -> ErrorMessage(R.string.unsupported_ans)
    UnsupportedFeature.VARIABLE ->
        ErrorMessage(R.string.unsupported_variable, listOfNotNull(argument))
}

/** 按当前语言解析成最终文案。 */
fun ErrorMessage.resolve(context: Context): String =
    if (args.isEmpty()) context.getString(resId)
    else context.getString(resId, *args.toTypedArray())

/**
 * 在子树里注入本地化的错误文案。
 *
 * 放在 `:app` 的根节点而不是单个界面里：错误可能从任何用 [com.hpcalc.core.mathui.MathBlockView]
 * 的地方冒出来，注入一次就全覆盖。语言切换会重建 Activity，`remember(context)` 因此自然失效重算。
 */
@Composable
fun LocalizedMathErrors(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val resolver = remember(context) {
        { error: MathError -> error.toMessage().resolve(context) }
    }
    CompositionLocalProvider(LocalMathErrorText provides resolver, content = content)
}