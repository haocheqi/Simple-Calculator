package com.hpcalc.feature.calculator

import com.hpcalc.core.model.MathError
import com.hpcalc.core.model.MathIssue
import com.hpcalc.core.model.UnsupportedFeature
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 错误文案本地化的完整性。
 *
 * 两条独立的保证：
 * 1. 每一个错误都映射到了某个资源。`when` 是穷尽的（新增变体会编译失败），
 *    但「映射到 0」「多个错误共用一个文案」这类复制粘贴错误编译期发现不了。
 * 2. 中文文案里不残留英文散文。这是 G01「零残留」在自动化里能做到的那一半：
 *    排版层只画这里给出的字符串，这里干净界面就干净。
 */
class MathErrorTextTest {

    private fun resourceFile(path: String): File {
        val candidates = listOf(path, "feature/calculator/$path", "calculator/$path")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("找不到资源文件：$path（工作目录 ${File(".").absolutePath}）")
    }

    private fun strings(file: File): Map<String, String> =
        Regex("""<string name="([^"]+)">(.*?)</string>""")
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private val english by lazy { strings(resourceFile("src/main/res/values/strings.xml")) }
    private val chinese by lazy { strings(resourceFile("src/main/res/values-zh-rCN/strings.xml")) }

    /** 逐个列出：sealed 接口没有 `entries`，新增成员时这里的缺口由编译器的穷尽性检查兜住。 */
    private val allIssues: List<MathIssue> = listOf(
        MathIssue.NeedsInteger,
        MathIssue.NeedsNonNegativeInteger,
        MathIssue.FactorialLimit(170),
        MathIssue.NeedsUnitInterval,
        MathIssue.NeedsPositive,
        MathIssue.LogBaseOne,
        MathIssue.MissingVariable("x"),
        MathIssue.ClampRange,
        MathIssue.Atan2Origin,
        MathIssue.NeedsSquareMatrix,
        MathIssue.MatrixShapeMismatch,
        MathIssue.ArgumentTooLarge
    )

    private val allErrors: List<MathError> = buildList {
        add(MathError.DivisionByZero)
        add(MathError.SingularMatrix)
        add(MathError.DepthLimitExceeded)
        add(MathError.InputTooLong)
        add(MathError.NotAvailable("Ans"))
        add(MathError.Undefined("detail"))
        add(MathError.ParseError("detail"))
        add(MathError.DomainError("detail"))
        add(MathError.DimensionMismatch("detail"))
        UnsupportedFeature.entries.forEach { add(MathError.Unsupported(it, "x")) }
        allIssues.forEach { add(MathError.DomainError("detail", it)) }
    }

    @Test
    fun `every error maps to a resource that exists in both locales`() {
        allErrors.forEach { error ->
            val message = error.toMessage()
            assertTrue("${error::class.simpleName} 未映射到资源", message.resId != 0)
            val name = resourceName(message.resId)
            assertTrue("values/ 缺少 $name", english.containsKey(name))
            assertTrue("values-zh-rCN/ 缺少 $name", chinese.containsKey(name))
        }
    }

    @Test
    fun `each distinct cause has its own resource`() {
        val seen = mutableMapOf<Int, MathError>()
        allErrors.forEach { error ->
            val previous = seen.put(error.toMessage().resId, error)
            assertTrue(
                "两个错误共用了 ${resourceName(error.toMessage().resId)}：$previous / $error",
                previous == null || previous == error
            )
        }
        // 9 个基类型 + 9 个不支持能力 + 12 条具体原因
        assertEquals(30, seen.size)
    }

    @Test
    fun `a specific issue overrides the generic category`() {
        assertNotEquals(
            MathError.DomainError("d").toMessage().resId,
            MathError.DomainError("d", MathIssue.FactorialLimit(170)).toMessage().resId
        )
        assertEquals(
            "error_factorial_limit",
            resourceName(MathError.DomainError("d", MathIssue.FactorialLimit(170)).toMessage().resId)
        )
    }

    @Test
    fun `chinese error text carries no english prose`() {
        // 这些是数学符号/函数名，不是英文散文，允许出现在中文文案里。
        val symbolIdentifiers = listOf("ans", "atan2", "gcd", "lcm", "stdev", "clamp")
        (english.keys + chinese.keys)
            .filter { it.startsWith("error_") || it.startsWith("unsupported_") }
            .forEach { key ->
                val text = chinese.getValue(key)
                var stripped = text.replace(Regex("""%1\$[ds]"""), "")
                symbolIdentifiers.forEach { stripped = stripped.replace(it, "", ignoreCase = true) }
                val leftover = Regex("""[A-Za-z]{2,}""").findAll(stripped).map { it.value }.toList()
                assertTrue("中文文案 $key 残留英文：$leftover（原文「$text」）", leftover.isEmpty())
            }
    }

    @Test
    fun `both locales define the same error keys`() {
        val errorKeys = (english.keys + chinese.keys).filter {
            it.startsWith("error_") || it.startsWith("unsupported_")
        }.toSet()
        assertEquals(errorKeys, english.keys.intersect(errorKeys))
        assertEquals(errorKeys, chinese.keys.intersect(errorKeys))
    }

    @Test
    fun `formatted messages keep their placeholders`() {
        assertEquals("Factorials are limited to %1\$d", english.getValue("error_factorial_limit"))
        assertEquals("No value given for %1\$s", english.getValue("error_missing_variable"))
        assertEquals("%1\$s is not an exact value", english.getValue("unsupported_variable"))
        assertEquals("阶乘最大支持 %1\$d", chinese.getValue("error_factorial_limit"))
    }

    private fun resourceName(resId: Int): String =
        RESOURCE_NAMES[resId] ?: error("未知资源 id $resId")

    private companion object {
        /**
         * 库模块里 `R.string.*` 不是编译期常量，没法写进注解，所以用 [R] 的运行时值建一张反查表。
         * 表与 XML 键集合的一致性由上面的测试对齐；漏一项会以「未知资源 id」失败。
         */
        val RESOURCE_NAMES: Map<Int, String> = mapOf(
            R.string.error_division_by_zero to "error_division_by_zero",
            R.string.error_undefined to "error_undefined",
            R.string.error_parse to "error_parse",
            R.string.error_domain to "error_domain",
            R.string.error_dimension to "error_dimension",
            R.string.error_singular_matrix to "error_singular_matrix",
            R.string.error_depth_limit to "error_depth_limit",
            R.string.error_input_too_long to "error_input_too_long",
            R.string.error_not_available to "error_not_available",
            R.string.error_needs_integer to "error_needs_integer",
            R.string.error_needs_non_negative to "error_needs_non_negative",
            R.string.error_factorial_limit to "error_factorial_limit",
            R.string.error_needs_unit_interval to "error_needs_unit_interval",
            R.string.error_needs_positive to "error_needs_positive",
            R.string.error_log_base_one to "error_log_base_one",
            R.string.error_missing_variable to "error_missing_variable",
            R.string.error_clamp_range to "error_clamp_range",
            R.string.error_atan2_origin to "error_atan2_origin",
            R.string.error_needs_square to "error_needs_square",
            R.string.error_shape_mismatch to "error_shape_mismatch",
            R.string.error_argument_too_large to "error_argument_too_large",
            R.string.unsupported_matrix_panel to "unsupported_matrix_panel",
            R.string.unsupported_polynomial_division to "unsupported_polynomial_division",
            R.string.unsupported_polynomial_exponent to "unsupported_polynomial_exponent",
            R.string.unsupported_modulo to "unsupported_modulo",
            R.string.unsupported_fractional_exponent to "unsupported_fractional_exponent",
            R.string.unsupported_factorial to "unsupported_factorial",
            R.string.unsupported_functions to "unsupported_functions",
            R.string.unsupported_ans to "unsupported_ans",
            R.string.unsupported_variable to "unsupported_variable"
        )
    }
}