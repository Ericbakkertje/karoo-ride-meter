package com.karooridemeter

import org.junit.Assert.assertEquals
import org.junit.Test

class ZonesTest {
    @Test
    fun powerZonesFromFtpAndProfile() {
        val z = PowerZones.fromFtp(200)
        assertEquals(7, z.size)
        assertEquals(listOf(0, 112, 152, 182, 212, 242, 302), z.map { it.min })
        assertEquals(4, PowerZones.zoneIndex(240, z)) // Z5
        // A profile with 7 increasing zones is used as is; anything else falls back to FTP.
        val profile = listOf(0, 100, 140, 170, 200, 230, 280).map { PowerZone(it, it + 29) }
        assertEquals(profile, PowerZones.resolve(profile, 250))
        assertEquals(PowerZones.fromFtp(250), PowerZones.resolve(profile.take(5), 250))
    }

    @Test
    fun heartRateZonesFromMaxHrAndProfile() {
        val z = HeartRateZones.fromMaxHr(182)
        assertEquals(listOf(0, 109, 127, 146, 164), z.map { it.min })
        assertEquals(3, HeartRateZones.zoneIndex(150, z)) // Z4 of 5
        val profile = listOf(HrZone(0, 120), HrZone(121, 140), HrZone(141, 182))
        assertEquals(profile, HeartRateZones.resolve(profile, 182))
    }
}
