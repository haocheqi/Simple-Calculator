package com.hpcalc.core.mathui

/**
 * 算出「把一块内容塞进给定区域」需要的等比缩放系数。
 *
 * 只缩小、不放大：内容本来就装得下时返回 1，避免小结果被拉得很大。
 * 任一方向无限（例如外层套了横向滚动）时就忽略那一维。
 */
internal fun fitScale(
    naturalWidth: Float,
    naturalHeight: Float,
    maxWidth: Float,
    maxHeight: Float
): Float {
    // 空内容或退化尺寸（含无穷大）无法按比例缩放，原样返回。
    if (!naturalWidth.isFinite() || !naturalHeight.isFinite()) return 1f
    if (naturalWidth <= 0f || naturalHeight <= 0f) return 1f
    val byWidth = if (maxWidth.isFinite() && maxWidth > 0f) maxWidth / naturalWidth else Float.MAX_VALUE
    val byHeight = if (maxHeight.isFinite() && maxHeight > 0f) maxHeight / naturalHeight else Float.MAX_VALUE
    return minOf(1f, byWidth, byHeight)
}