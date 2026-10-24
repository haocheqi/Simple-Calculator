package com.hpcalc.core.model

/**
 * 角度单位。三角与反三角函数按此单位解释入参、产出结果。
 *
 * 迁移自 v2.0 的 `com.example.calculator.domain.AngleUnit`，语义未变，
 * 去掉了 v2.0 中未使用的 `fullTurn` 辅助属性（由 `:core:math` 承担换算职责）。
 */
enum class AngleUnit {
    RADIANS,
    DEGREES,
    GRADIANS
}