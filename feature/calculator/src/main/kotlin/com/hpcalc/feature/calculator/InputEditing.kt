package com.hpcalc.feature.calculator

/** 一次编辑之后的算式与光标位置。 */
internal data class Editable(val expression: String, val caret: Int)

/**
 * 在光标处插入文本。
 *
 * 光标可以被点到算式中间，所以插入不能简单地 `+` 到末尾；
 * 超过 [limit] 的部分直接丢弃，并把光标收回到实际长度。
 */
internal fun insertAt(expression: String, caret: Int, text: String, limit: Int): Editable {
    val at = caret.coerceIn(0, expression.length)
    val room = limit - expression.length
    if (room <= 0) return Editable(expression, at)
    val accepted = text.take(room)
    val next = expression.substring(0, at) + accepted + expression.substring(at)
    return Editable(next, at + accepted.length)
}

/** 退格：只删光标左边那一个字符。光标在开头时什么也不做。 */
internal fun deleteBefore(expression: String, caret: Int): Editable {
    val at = caret.coerceIn(0, expression.length)
    if (at == 0) return Editable(expression, at)
    return Editable(expression.removeRange(at - 1, at), at - 1)
}

/** 点击算式时把光标放到第 [index] 个字符处，越界自动收拢。 */
internal fun moveCaret(expression: String, index: Int): Int = index.coerceIn(0, expression.length)