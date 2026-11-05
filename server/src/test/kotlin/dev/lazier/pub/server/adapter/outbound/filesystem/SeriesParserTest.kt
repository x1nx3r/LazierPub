package dev.lazier.pub.server.adapter.outbound.filesystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeriesParserTest {

    @Test
    fun readsTheCommaVolumeShape() {
        val result = SeriesParser.parse("Sword Art Online, Vol. 03: Fairy Dance")

        assertEquals("Sword Art Online", result.series)
        assertEquals("3", result.volume)
    }

    @Test
    fun readsTheDashVolumeShape() {
        val result = SeriesParser.parse("A Certain Magical Index - Volume 21")

        assertEquals("A Certain Magical Index", result.series)
        assertEquals("21", result.volume)
    }

    @Test
    fun keepsASubSeriesInTheName() {
        val result = SeriesParser.parse("A Certain Magical Index: New Testament - Volume 01")

        assertEquals("A Certain Magical Index New Testament", result.series)
        assertEquals("1", result.volume)
    }

    @Test
    fun readsANonNumericVolume() {
        val result = SeriesParser.parse("A Certain Magical Index - Volume SS1")

        assertEquals("A Certain Magical Index", result.series)
        assertEquals("SS1", result.volume)
    }

    @Test
    fun acceptsAnEnDash() {
        val result = SeriesParser.parse("A Certain Magical Index – Volume 12")

        assertEquals("A Certain Magical Index", result.series)
        assertEquals("12", result.volume)
    }

    @Test
    fun readsThePlainVolumeShape() {
        val result = SeriesParser.parse("Sword Art Online Volume 7")

        assertEquals("Sword Art Online", result.series)
        assertEquals("7", result.volume)
    }

    @Test
    fun refusesToSplitTitlesWithoutAVolume() {
        // Splitting on any dash would turn these into false series.
        assertNull(SeriesParser.parse("A Certain Magical Index - Love Letter SS").series)
        assertNull(SeriesParser.parse("Dengeki Island - A Girl's Battle (Still Growing)").series)
        assertNull(SeriesParser.parse("A Certain March 201st Volume").series)
        assertNull(SeriesParser.parse("Standalone Story").series)
    }

    @Test
    fun leavesAVolumeWithoutASeriesAlone() {
        assertNull(SeriesParser.parse("Volume 3").series)
        assertNull(SeriesParser.parse("").volume)
    }
}
