package com.aifigurepaint.app.ui

import android.content.Context
import android.graphics.Color
import com.aifigurepaint.app.data.PaintEntity

internal enum class PaintSortMode(val label: String) {
    DEFAULT("기본순"),
    PRODUCT_CODE("상품번호순"),
    SIMILAR_COLOR("비슷한 색상순");

    companion object {
        fun fromStored(value: String?): PaintSortMode = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

internal object PaintSortPreferences {
    private const val PREFS = "paint_list_preferences"
    private const val KEY_SORT = "paint_sort_mode"

    fun read(context: Context): PaintSortMode = PaintSortMode.fromStored(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SORT, null),
    )

    fun save(context: Context, mode: PaintSortMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SORT, mode.name).apply()
    }
}

internal fun sortPaints(paints: List<PaintEntity>, mode: PaintSortMode): List<PaintEntity> = when (mode) {
    PaintSortMode.DEFAULT -> paints
    PaintSortMode.PRODUCT_CODE -> paints.sortedWith { left, right ->
        val brand = left.brand.compareTo(right.brand, ignoreCase = true)
        if (brand != 0) brand else {
            val leftCode = left.productCode?.trim().orEmpty()
            val rightCode = right.productCode?.trim().orEmpty()
            when {
                leftCode.isBlank() && rightCode.isNotBlank() -> 1
                leftCode.isNotBlank() && rightCode.isBlank() -> -1
                leftCode.isBlank() -> left.name.compareTo(right.name, ignoreCase = true)
                else -> naturalCompare(leftCode, rightCode).takeIf { it != 0 }
                    ?: left.name.compareTo(right.name, ignoreCase = true)
            }
        }
    }
    PaintSortMode.SIMILAR_COLOR -> paints.sortedWith(
        compareBy<PaintEntity> { colorKey(it.colorValue).family }
            .thenBy { colorKey(it.colorValue).hue }
            .thenBy { colorKey(it.colorValue).value }
            .thenBy { colorKey(it.colorValue).saturation }
            .thenBy { it.name.lowercase() },
    )
}

private data class PaintColorKey(
    val family: Int,
    val hue: Float,
    val value: Float,
    val saturation: Float,
)

private fun colorKey(color: Int): PaintColorKey {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    val hue = hsv[0]
    val saturation = hsv[1]
    val value = hsv[2]
    val family = when {
        saturation < 0.12f || value < 0.13f -> 8 // white / gray / black
        hue in 15f..55f && (value < 0.62f || saturation < 0.36f) -> 7 // brown / beige
        (hue >= 320f || hue < 12f) && value > 0.70f && saturation < 0.68f -> 6 // pink
        hue < 15f || hue >= 345f -> 0 // red
        hue < 45f -> 1 // orange
        hue < 70f -> 2 // yellow
        hue < 170f -> 3 // green
        hue < 255f -> 4 // blue / cyan
        hue < 320f -> 5 // purple
        else -> 6 // pink
    }
    return PaintColorKey(family, hue, value, saturation)
}

private val naturalToken = Regex("\\d+|\\D+")

private fun naturalCompare(left: String, right: String): Int {
    val leftTokens = naturalToken.findAll(left.trim()).map { it.value }.toList()
    val rightTokens = naturalToken.findAll(right.trim()).map { it.value }.toList()
    for (index in 0 until minOf(leftTokens.size, rightTokens.size)) {
        val a = leftTokens[index]
        val b = rightTokens[index]
        val aNumber = a.toLongOrNull()
        val bNumber = b.toLongOrNull()
        val compared = if (aNumber != null && bNumber != null) {
            aNumber.compareTo(bNumber).takeIf { it != 0 } ?: a.length.compareTo(b.length)
        } else {
            a.compareTo(b, ignoreCase = true)
        }
        if (compared != 0) return compared
    }
    return leftTokens.size.compareTo(rightTokens.size)
}
