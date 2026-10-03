package com.vx.anymaker.feature.resize

import com.vx.anymaker.R
import com.vx.anymaker.core.data.ResizeSettings
import com.vx.anymaker.core.image.Shrinker
import com.vx.anymaker.ui.text.UiText

/** What the user typed, kept as text so half-typed values survive until Convert. */
data class ResizeForm(
    val width: String = "1000",
    val height: String = "1000",
    val maxKb: String = "100",
    val keepAspect: Boolean = true,
    val neverReducePixels: Boolean = false,
    val webp: Boolean = false,
    val locked: Boolean = false,
) {
    /** Keep-aspect only matters when both sides are given. */
    val aspectApplies: Boolean get() = width.isNotBlank() && height.isNotBlank()

    fun toSettings(v: Valid): ResizeSettings = ResizeSettings(
        width = v.width,
        height = v.height,
        keepAspect = keepAspect,
        neverReducePixels = neverReducePixels,
        maxKb = v.maxKb,
        webp = webp,
        locked = locked,
    )

    data class Valid(val width: Int?, val height: Int?, val maxKb: Double)

    data class Errors(val width: UiText? = null, val height: UiText? = null, val maxKb: UiText? = null) {
        val any: Boolean get() = width != null || height != null || maxKb != null
    }

    fun validate(): Pair<Valid?, Errors> {
        val (w, wErr) = parseDimension(width)
        val (h, hErr) = parseDimension(height)
        val kb = maxKb.trim().toDoubleOrNull()?.takeIf { it >= MIN_KB && it <= MAX_KB && !it.isNaN() }
        val kbErr = if (kb == null) UiText.Res(R.string.error_kb_range) else null
        val errors = Errors(wErr, hErr, kbErr)
        return if (errors.any) null to errors else Valid(w, h, kb!!) to errors
    }

    fun toOptions(v: Valid): Shrinker.Options = Shrinker.Options().also {
        it.width = v.width ?: 0
        it.height = v.height ?: 0
        it.keepAspect = keepAspect
        it.strictResolution = neverReducePixels
        it.maxKb = v.maxKb
        it.webp = webp
    }

    companion object {
        const val MIN_KB = 1.0
        const val MAX_KB = 100_000.0

        fun from(s: ResizeSettings) = ResizeForm(
            width = s.width?.toString().orEmpty(),
            height = s.height?.toString().orEmpty(),
            maxKb = Shrinker.formatKb(s.maxKb),
            keepAspect = s.keepAspect,
            neverReducePixels = s.neverReducePixels,
            webp = s.webp,
            locked = s.locked,
        )

        /** Blank means "not set"; otherwise an integer inside the Shrinker's limits. */
        private fun parseDimension(text: String): Pair<Int?, UiText?> {
            val t = text.trim()
            if (t.isEmpty()) return null to null
            val v = t.toIntOrNull()
            return if (v != null && v in Shrinker.MIN_DIMENSION..Shrinker.MAX_DIMENSION) {
                v to null
            } else {
                null to UiText.Res(R.string.error_dimension_range, listOf(Shrinker.MIN_DIMENSION, Shrinker.MAX_DIMENSION))
            }
        }
    }
}
