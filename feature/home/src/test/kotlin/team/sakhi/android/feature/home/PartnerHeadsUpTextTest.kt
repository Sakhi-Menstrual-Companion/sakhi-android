package team.sakhi.android.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import team.sakhi.models.CyclePhase

/**
 * First real JVM unit test in this module -- covers all 5 branches of
 * `partnerHeadsUpText`, ported from iOS's `partnerHeadsUpText` switch this
 * session. Pure function, no Android/Compose dependency, so a plain
 * `junit` test is the right tool here (not a reason to decide the whole
 * app's screenshot/instrumentation testing strategy).
 */
class PartnerHeadsUpTextTest {

    @Test
    fun `follicular shows countdown within 7 days`() {
        val result = partnerHeadsUpText(CyclePhase.FOLLICULAR, dayInCycle = 8, daysUntilNextPeriod = 3)
        assertEquals(PartnerHeadsUpText("Period in 3 days", 3), result)
    }

    @Test
    fun `follicular says tomorrow for 1 day`() {
        val result = partnerHeadsUpText(CyclePhase.FOLLICULAR, dayInCycle = 8, daysUntilNextPeriod = 1)
        assertEquals(PartnerHeadsUpText("Period tomorrow", 1), result)
    }

    @Test
    fun `follicular hides countdown beyond 7 days`() {
        val result = partnerHeadsUpText(CyclePhase.FOLLICULAR, dayInCycle = 8, daysUntilNextPeriod = 8)
        assertNull(result)
    }

    @Test
    fun `ovulation shares the same 7-day window as follicular`() {
        val result = partnerHeadsUpText(CyclePhase.OVULATION, dayInCycle = 14, daysUntilNextPeriod = 7)
        assertEquals(PartnerHeadsUpText("Period in 7 days", 7), result)
    }

    @Test
    fun `luteal hides countdown beyond its tighter 5-day window`() {
        val result = partnerHeadsUpText(CyclePhase.LUTEAL, dayInCycle = 20, daysUntilNextPeriod = 6)
        assertNull(result)
    }

    @Test
    fun `luteal shows countdown within 5 days`() {
        val result = partnerHeadsUpText(CyclePhase.LUTEAL, dayInCycle = 24, daysUntilNextPeriod = 4)
        assertEquals(PartnerHeadsUpText("Period in 4 days", 4), result)
    }

    @Test
    fun `menstrual hides long-period flag before day 6`() {
        val result = partnerHeadsUpText(CyclePhase.MENSTRUAL, dayInCycle = 5, daysUntilNextPeriod = null)
        assertNull(result)
    }

    @Test
    fun `menstrual shows long-period flag from day 6`() {
        val result = partnerHeadsUpText(CyclePhase.MENSTRUAL, dayInCycle = 6, daysUntilNextPeriod = null)
        assertEquals(PartnerHeadsUpText("Long period, day 6", null), result)
    }

    @Test
    fun `delayed shows singular day past expected date`() {
        val result = partnerHeadsUpText(CyclePhase.DELAYED, dayInCycle = null, daysUntilNextPeriod = -1)
        assertEquals(PartnerHeadsUpText("1 day past expected date", null), result)
    }

    @Test
    fun `delayed shows plural days past expected date`() {
        val result = partnerHeadsUpText(CyclePhase.DELAYED, dayInCycle = null, daysUntilNextPeriod = -4)
        assertEquals(PartnerHeadsUpText("4 days past expected date", null), result)
    }

    @Test
    fun `unknown phase always hides the card`() {
        val result = partnerHeadsUpText(CyclePhase.UNKNOWN, dayInCycle = 10, daysUntilNextPeriod = 3)
        assertNull(result)
    }

    @Test
    fun `null inputs hide the card instead of crashing`() {
        assertNull(partnerHeadsUpText(CyclePhase.FOLLICULAR, dayInCycle = null, daysUntilNextPeriod = null))
        assertNull(partnerHeadsUpText(CyclePhase.MENSTRUAL, dayInCycle = null, daysUntilNextPeriod = null))
        assertNull(partnerHeadsUpText(CyclePhase.DELAYED, dayInCycle = null, daysUntilNextPeriod = null))
    }
}
