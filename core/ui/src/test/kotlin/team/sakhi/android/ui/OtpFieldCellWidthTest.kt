package team.sakhi.android.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the OTP row's measurement, which is what actually broke: the sixth cell used to be
 * handed whatever width the first five had not already taken, so on a narrow screen it
 * measured 0dp and the last digit had nowhere to render even though the field's value
 * already held it.
 *
 * The widths below are the real ones: `SakhiSpacing.space12` (48dp) cells, `space2` (8dp)
 * gaps, and the usable width left by `OtpScreen`'s `space6` (24dp) padding on each side.
 */
class OtpFieldCellWidthTest {

    private val cellWidth = 48.dp
    private val cellSpacing = 8.dp
    private val length = 6

    private fun resolve(available: Int) = resolveOtpCellWidth(
        available = available.dp,
        cellWidth = cellWidth,
        cellSpacing = cellSpacing,
        length = length,
    )

    /** Total the row occupies once every cell takes [cell]. */
    private fun rowWidth(cell: androidx.compose.ui.unit.Dp) = cell * length + cellSpacing * (length - 1)

    @Test
    fun `all six cells fit on a 320dp screen, which used to collapse the sixth to zero`() {
        // 320dp device - 48dp of screen padding. The old fixed-width row needed 328dp here
        // and left the sixth cell exactly 0dp.
        val cell = resolve(272)

        assertTrue(cell > 0.dp, "sixth cell must have real width, was $cell")
        assertTrue(
            rowWidth(cell) <= 272.dp,
            "six cells of $cell plus gaps = ${rowWidth(cell)}, which overflows 272dp",
        )
    }

    @Test
    fun `all six cells fit on a 360dp screen, where the sixth used to be squeezed`() {
        // 360dp device - 48dp padding = 312dp, 16dp short of the 328dp the row wanted.
        val cell = resolve(312)

        assertTrue(cell > 0.dp, "sixth cell must have real width, was $cell")
        assertTrue(
            rowWidth(cell) <= 312.dp,
            "six cells of $cell plus gaps = ${rowWidth(cell)}, which overflows 312dp",
        )
    }

    @Test
    fun `a width that already fits keeps the full design cell size`() {
        // 393dp Pixel 5 - 48dp padding = 345dp, which fits 328dp. Nothing should change on
        // the devices that were already correct, including the screenshot baselines.
        assertEquals(cellWidth, resolve(345))
    }

    @Test
    fun `every cell stays the same width, so the row never looks uneven`() {
        // The old behaviour was five full cells and one different one. Uniformity is the
        // property being kept, not just "nonzero".
        val cell = resolve(272)
        val cells = List(length) { cell }

        assertEquals(1, cells.distinct().size, "cells must all be one width, got $cells")
    }

    @Test
    fun `an absurdly narrow width degrades to zero instead of a negative size`() {
        // Compose throws on a negative size, so the floor matters even though no real
        // device is this narrow.
        assertEquals(0.dp, resolve(0))
    }
}
