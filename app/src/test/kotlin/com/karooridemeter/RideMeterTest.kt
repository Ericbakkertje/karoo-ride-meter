package com.karooridemeter

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RideMeterTest {
    private val outDir = File("build/gauge-previews").apply { mkdirs() }
    private val sizes = listOf(478 to 228, 478 to 456, 478 to 721, 480 to 400, 240 to 266, 240 to 133, 478 to 160)

    private fun state(kmh: Double? = 28.4) = RideState(
        kmh = kmh,
        scaleMaxKmh = 60,
        frontTeeth = kmh?.let { 40 },
        rearTeeth = kmh?.let { 15 },
        distanceKm = kmh?.let { 61.2 },
        rideTimeSec = kmh?.let { 2L * 3600 + 21 * 60 },
        gradePct = kmh?.let { -4.1 },
        cadenceRpm = kmh?.let { 88 },
        shiftBattery = ShiftBattery("AXS", DrivetrainBattery.GOOD),
        clock = "12:44",
    )

    @Test
    fun modelFormatsAllValues() {
        val m = RideGauge.model(state())
        assertEquals("28.4", m.speedText)
        assertEquals("40 / 15", m.gearText)
        assertEquals("61.2 km", m.underSpeedText)
        val avg = RideGauge.model(state().copy(underSpeed = UnderSpeed.AVG_SPEED, avgKmh = 26.08))
        assertEquals("26.08", avg.underSpeedText)
        assertEquals("AVG", avg.underSpeedLabel)
        assertEquals(null, m.underSpeedLabel)
        assertEquals(
            listOf(
                RideSlot("SLOPE %", "-4.1", GaugePalette.ZONES[0]),
                RideSlot("NOW", "12:44", RideGauge.NEUTRAL),
                RideSlot("CADENCE", "88", GaugePalette.ZONES[1]),
            ),
            m.slots,
        )
        assertEquals(28.4f / 60f, m.position!!, 0.0001f)
        assertEquals(60, m.scaleMax)
        assertEquals(ShiftBattery("AXS", DrivetrainBattery.GOOD), m.shiftBattery)
    }

    @Test
    fun modelWithoutDataShowsDashes() {
        val m = RideGauge.model(state(null))
        assertNull(m.speedText)
        assertNull(m.position)
        assertEquals("-- / --", m.gearText)
        assertEquals("-- km", m.underSpeedText)
        assertEquals("--", RideGauge.model(state(null).copy(underSpeed = UnderSpeed.AVG_SPEED)).underSpeedText)
        assertEquals(RideSlot("SLOPE %", null, RideGauge.NEUTRAL), m.slots[0])
    }

    @Test
    fun durationAndColors() {
        assertEquals("0:46", RideGauge.duration(46 * 60 + 5))
        assertEquals("10:00", RideGauge.duration(36000))
        assertEquals(GaugePalette.ZONES[0], RideGauge.slopeColor(-4.1))
        assertEquals(GaugePalette.ZONES[1], RideGauge.slopeColor(0.5))
        assertEquals(GaugePalette.ZONES[5], RideGauge.slopeColor(9.0))
        assertEquals(RideGauge.NEUTRAL, RideGauge.cadenceColor(0))
        assertEquals(GaugePalette.ZONES[1], RideGauge.cadenceColor(88))
    }

    @Test
    fun everyBoxChoiceHasAValueAndLabel() {
        val full = state().copy(
            heartRate = 150, power = 240, power3s = 230, powerToWeight = 3.1234, avgPower = 167, np = 181,
            avgKmh = 26.1, maxKmh = 38.3, climbM = 412.6, temperatureC = 4.5, calories = 1234,
            powerZones = PowerZones.fromFtp(199), hrZones = HeartRateZones.fromMaxHr(182),
        )
        for (v in RideSlotValue.entries) {
            val slot = RideGauge.slot(v, full)
            assertEquals(v.label, slot.label)
            assertTrue("$v has no value", slot.value != null)
            assertNull("$v shows a value without data", RideGauge.slot(v, state(null).copy(clock = "--")).value?.takeIf { it != "--" })
        }
        assertEquals("3.12", RideGauge.slot(RideSlotValue.POWER_TO_WEIGHT, full).value)
        assertEquals("413", RideGauge.slot(RideSlotValue.CLIMB, full).value)
        assertEquals("40/15", RideGauge.slot(RideSlotValue.GEAR, full).value)
        assertEquals("2:21", RideGauge.slot(RideSlotValue.RIDE_TIME, full).value)
        // 240 W at FTP 199 (121 %) is Z5; 150 bpm of max 182 (82 %) is Z4 of 5.
        assertEquals(GaugePalette.ZONES[4], RideGauge.slot(RideSlotValue.POWER, full).color)
        assertEquals(GaugePalette.HR_ZONES[3], RideGauge.slot(RideSlotValue.HEART_RATE, full).color)
    }

    @Test
    fun writesChosenBoxesPreview() {
        val s = state().copy(
            slots = listOf(RideSlotValue.POWER, RideSlotValue.HEART_RATE, RideSlotValue.CLIMB),
            power = 240, heartRate = 150, climbM = 412.0,
            powerZones = PowerZones.fromFtp(199), hrZones = HeartRateZones.fromMaxHr(182),
        )
        for ((w, h) in listOf(478 to 228, 478 to 721)) {
            val bmp = RideRenderer.render(RideGauge.model(s), w, h, Bitmap.Config.ARGB_8888)
            File(outDir, "ride_custom_${w}x$h.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun writesPreviewsForAllFieldSizes() {
        val cases = listOf("28kmh" to state(), "0kmh" to state(0.0), "70kmh_over" to state(70.0), "nodata" to state(null))
        for ((w, h) in sizes) for ((name, s) in cases) {
            val bmp = RideRenderer.render(RideGauge.model(s), w, h, Bitmap.Config.ARGB_8888)
            assertEquals(w, bmp.width)
            val f = File(outDir, "ride_${name}_${w}x$h.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun drivetrainBatteryFromRawValue() {
        // SDK BatteryStatus ordinals: 0 NEW, 1 GOOD, 2 OK, 3 LOW, 4 CRITICAL, 5 INVALID.
        assertEquals(DrivetrainBattery.NEW, DrivetrainBattery.fromRaw(0.0))
        assertEquals(DrivetrainBattery.GOOD, DrivetrainBattery.fromRaw(1.0))
        assertEquals(DrivetrainBattery.OK, DrivetrainBattery.fromRaw(2.0))
        assertEquals(DrivetrainBattery.LOW, DrivetrainBattery.fromRaw(3.0))
        assertEquals(DrivetrainBattery.CRITICAL, DrivetrainBattery.fromRaw(4.0))
        assertNull(DrivetrainBattery.fromRaw(5.0))
        assertNull(DrivetrainBattery.fromRaw(null))
        // Values above 5 are read as a percentage, with the SDK's own thresholds.
        assertEquals(DrivetrainBattery.NEW, DrivetrainBattery.fromRaw(98.0))
        assertEquals(DrivetrainBattery.GOOD, DrivetrainBattery.fromRaw(85.0))
        assertEquals(DrivetrainBattery.OK, DrivetrainBattery.fromRaw(60.0))
        assertEquals(DrivetrainBattery.LOW, DrivetrainBattery.fromRaw(30.0))
        assertEquals(DrivetrainBattery.CRITICAL, DrivetrainBattery.fromRaw(10.0))
    }

    @Test
    fun shiftBatteryFromAxsOrDi2() {
        assertEquals(ShiftBattery("AXS", DrivetrainBattery.OK), ShiftBattery.pick(axsRaw = 2.0, di2Pct = null))
        assertEquals("OK", ShiftBattery.axs(2.0)!!.text)
        assertEquals(0.6f, ShiftBattery.axs(2.0)!!.fill, 0.001f)
        // Di2 via Ki2: exact percentage, level from the same thresholds.
        val di2 = ShiftBattery.pick(axsRaw = null, di2Pct = 87.4)!!
        assertEquals("Di2", di2.system)
        assertEquals(87, di2.percent)
        assertEquals("87%", di2.text)
        assertEquals(DrivetrainBattery.GOOD, di2.level)
        assertEquals(0.87f, di2.fill, 0.001f)
        assertEquals(DrivetrainBattery.LOW, ShiftBattery.di2(20.0)!!.level)
        assertEquals(DrivetrainBattery.CRITICAL, ShiftBattery.di2(3.0)!!.level) // low % is not read as a status
        // Di2 wins when both report; nothing / out of range = unknown.
        assertEquals("Di2", ShiftBattery.pick(axsRaw = 1.0, di2Pct = 50.0)!!.system)
        assertNull(ShiftBattery.pick(axsRaw = null, di2Pct = null))
        assertNull(ShiftBattery.di2(140.0))
        assertNull(ShiftBattery.axs(5.0))
        assertEquals("TYPE_EXT::ki2::DATATYPE_SHIFTING_BATTERY_PERCENTAGE", RideMeterDataType.KI2_BATTERY)
    }

    @Test
    fun di2IsRememberedWhenKi2PercentPauses() {
        var t = 0L
        val tracker = ShiftBatteryTracker { t }
        // Page start: no data yet, then Ki2 reports 70 %.
        assertNull(tracker.update(axsRaw = null, di2Pct = null))
        t += 1_000
        assertEquals(ShiftBattery.di2(70.0), tracker.update(axsRaw = 1.0, di2Pct = 70.0))
        // Ki2's % stream pauses; only the generic status (fed by Ki2 too) is left: still Di2 70 %, not "AXS GOOD".
        t += 60_000
        assertEquals(ShiftBattery.di2(70.0), tracker.update(axsRaw = 1.0, di2Pct = null))
        // A short gap with no battery data at all keeps Di2 as well.
        t += 30_000
        assertEquals(ShiftBattery.di2(70.0), tracker.update(axsRaw = null, di2Pct = null))
        t += 60_000
        assertEquals("Di2", tracker.update(axsRaw = null, di2Pct = null)!!.system)
    }

    @Test
    fun di2IsForgottenAfterTwoMinutesWithoutBatteryData() {
        var t = 0L
        val tracker = ShiftBatteryTracker { t }
        tracker.update(axsRaw = null, di2Pct = 70.0)
        tracker.update(axsRaw = null, di2Pct = null) // silence starts
        t += ShiftBatteryTracker.FORGET_AFTER_MS + 1
        assertNull(tracker.update(axsRaw = null, di2Pct = null))
        // Now on the AXS bike: shown as AXS.
        assertEquals(ShiftBattery("AXS", DrivetrainBattery.GOOD), tracker.update(axsRaw = 1.0, di2Pct = null))
    }

    @Test
    fun axsOnlyStaysAxs() {
        val tracker = ShiftBatteryTracker { 0L }
        assertEquals(ShiftBattery("AXS", DrivetrainBattery.OK), tracker.update(axsRaw = 2.0, di2Pct = null))
        assertNull(ShiftBatteryTracker { 0L }.update(axsRaw = null, di2Pct = null))
    }

    @Test
    fun tickStepsGiveWholeNumberLabels() {
        assertEquals(10 to 2, RideRenderer.tickSteps(60))
        assertEquals(10 to 5, RideRenderer.tickSteps(90))
        assertEquals(5 to 1, RideRenderer.tickSteps(30))
        assertEquals(5 to 1, RideRenderer.tickSteps(45))
        for (max in RideSettings.SCALE_OPTIONS_KMH) {
            val (major, minor) = RideRenderer.tickSteps(max)
            assertEquals("scale $max ends on a numbered tick", 0, max % major)
            assertEquals(0, major % minor)
        }
    }

    @Test
    fun tickStepsForPowerHeartRateAndCadenceScales() {
        assertEquals(20 to 5, RideRenderer.tickSteps(140)) // cadence 0-140, heart rate 60-200
        assertEquals(50 to 10, RideRenderer.tickSteps(400))
        assertEquals(100 to 20, RideRenderer.tickSteps(800))
        assertEquals(5 to 1, RideRenderer.tickSteps(40)) // 0-40 mph
    }

    @Test
    fun powerHeartRateAndCadenceDials() {
        val s = state().copy(
            power = 245, power3s = 230, avgPower = 212, heartRate = 150, avgHeartRate = 138, avgCadence = 84,
            slots = listOf(RideSlotValue.CADENCE, RideSlotValue.HEART_RATE, RideSlotValue.POWER_3S),
        )
        val pw = RideGauge.model(s.copy(dial = DialValue.POWER, ftp = 199))
        assertEquals("245", pw.speedText)
        assertEquals("watt", pw.unit)
        assertEquals(0 to 400, pw.scaleMin to pw.scaleMax)
        assertEquals(245f / 400f, pw.position!!, 0.0001f)
        assertEquals("212" to "AVG", pw.underSpeedText to pw.underSpeedLabel)
        // Speed takes field 1; the other two keep the choices (power 3s stays: it is not this dial's value).
        assertEquals(listOf("SPEED", "CADENCE", "HR"), pw.slots.map { it.label })
        assertEquals("28.4", pw.slots[0].value)

        val p3 = RideGauge.model(s.copy(dial = DialValue.POWER_3S))
        assertEquals("230", p3.speedText)
        assertEquals(RideGauge.DEFAULT_POWER_SCALE, p3.scaleMax)
        assertEquals(listOf("SPEED", "CADENCE", "HR"), p3.slots.map { it.label })

        val hr = RideGauge.model(s.copy(dial = DialValue.HEART_RATE, maxHr = 182))
        assertEquals("150" to "bpm", hr.speedText to hr.unit)
        assertEquals(60 to 200, hr.scaleMin to hr.scaleMax)
        assertEquals(90f / 140f, hr.position!!, 0.0001f)
        assertEquals("138", hr.underSpeedText)
        assertEquals(listOf("SPEED", "CADENCE", "POWER 3s"), hr.slots.map { it.label })
        // Below 60 bpm the needle rests at the start; a high max HR widens the scale.
        assertEquals(0f, RideGauge.model(s.copy(dial = DialValue.HEART_RATE, heartRate = 50)).position!!, 0f)
        assertEquals(220, RideGauge.model(s.copy(dial = DialValue.HEART_RATE, maxHr = 205)).scaleMax)

        val cd = RideGauge.model(s.copy(dial = DialValue.CADENCE))
        assertEquals("88" to "rpm", cd.speedText to cd.unit)
        assertEquals(0 to 140, cd.scaleMin to cd.scaleMax)
        assertEquals("84", cd.underSpeedText)
        assertEquals(listOf("SPEED", "HR", "POWER 3s"), cd.slots.map { it.label })

        // Power scale: twice the FTP rounded up to 100 W, between 300 and 1000.
        assertEquals(600, RideGauge.scale(s.copy(dial = DialValue.POWER, ftp = 280)).second)
        assertEquals(300, RideGauge.scale(s.copy(dial = DialValue.POWER, ftp = 100)).second)
        assertEquals(1000, RideGauge.scale(s.copy(dial = DialValue.POWER, ftp = 600)).second)

        // Every scale ends on a numbered tick.
        for (m in listOf(pw, p3, hr, cd)) {
            val (major, _) = RideRenderer.tickSteps(m.scaleMax - m.scaleMin)
            assertEquals(0, m.scaleMin % major)
            assertEquals(0, m.scaleMax % major)
        }
    }

    @Test
    fun imperialUnitsFollowTheKaroo() {
        val s = state().copy(
            units = Units.IMPERIAL, avgKmh = 32.0, temperatureC = 20.0, altitudeM = 100.0, climbM = 500.0, toFinishKm = 16.09344,
            statusLeft = StatusValue.TEMPERATURE, statusRight = StatusValue.TO_FINISH,
            slots = listOf(RideSlotValue.AVG_SPEED, RideSlotValue.CLIMB, RideSlotValue.DISTANCE),
        )
        val m = RideGauge.model(s)
        assertEquals("17.6" to "mph", m.speedText to m.unit) // 28.4 km/h
        assertEquals(40, m.scaleMax) // 60 km/h scale -> 40 mph
        assertEquals(17.647f / 40f, m.position!!, 0.001f)
        assertEquals("38.0 mi", m.underSpeedText) // 61.2 km
        assertEquals(StatusItem.Value("68.0", "TEMP °F", true), m.statusLeft)
        assertEquals(StatusItem.Value("10.0", "TO FINISH mi", true), m.statusRight)
        assertEquals(
            listOf(RideSlot("AVG mph", "19.9", RideGauge.NEUTRAL), RideSlot("CLIMB ft", "1640", RideGauge.NEUTRAL), RideSlot("DIST mi", "38.0", RideGauge.NEUTRAL)),
            m.slots,
        )
        assertEquals(listOf(20, 30, 40, 60), RideSettings.SCALE_OPTIONS_KMH.map { Units.mphScale(it) })
        // Mixed units: imperial distance, metric elevation and temperature.
        val mixed = RideGauge.model(s.copy(units = Units(imperialDistance = true)))
        assertEquals(StatusItem.Value("20.0", "TEMP °C", true), mixed.statusLeft)
        assertEquals("CLIMB m", mixed.slots[1].label)
    }

    @Test
    fun shiftingBatteryShowsTheBrandOfItsSensor() {
        fun sensor(id: String, name: String?, maker: String?, enabled: Boolean = true, types: List<String> = listOf("TYPE_SHIFTING_BATTERY_ID")) =
            ShiftingBrand.Sensor(id, name, maker, enabled, types)
        val axs = sensor("ble-1", "RD", "SRAM")
        val campa = sensor("ble-2", "Rear derailleur", "Campagnolo S.r.l.")
        val hrm = sensor("ant-3", "HRM", "Garmin", types = listOf("TYPE_HEART_RATE_ID"))
        // The sensor the value came from wins.
        assertEquals("Campa", ShiftingBrand.label("ble-2", listOf(axs, campa, hrm)))
        assertEquals("AXS", ShiftingBrand.label("ble-1", listOf(axs, campa, hrm)))
        // Unknown source: the only shifting brand among the enabled sensors.
        assertEquals("Campa", ShiftingBrand.label(null, listOf(campa, hrm)))
        assertEquals("Campa", ShiftingBrand.label("", listOf(campa, axs.copy(enabled = false), hrm)))
        // Two brands, a brand we do not know, or nothing known yet: neutral name.
        assertEquals("Batt", ShiftingBrand.label(null, listOf(axs, campa)))
        assertEquals("Batt", ShiftingBrand.label("x", listOf(sensor("x", "Shifter", "Acme"))))
        assertEquals("Batt", ShiftingBrand.label(null, emptyList()))
        // Brand from the name when the manufacturer is missing.
        assertEquals("AXS", ShiftingBrand.label(null, listOf(sensor("y", "SRAM AXS RD", null))))
        // The tracker shows the level with that name.
        assertEquals(ShiftBattery("Campa", DrivetrainBattery.GOOD), ShiftBatteryTracker { 0L }.update(axsRaw = 1.0, di2Pct = null, system = "Campa"))
        assertEquals("Campa GOOD", ShiftBattery("Campa", DrivetrainBattery.GOOD).let { "${it.system} ${it.text}" })
    }

    @Test
    fun everyDialHasAFieldInExtensionInfo() {
        val xml = File("src/main/res/xml/extension_info.xml").readText()
        val typeIds = Regex("typeId=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }.toList()
        assertEquals(DialValue.entries.map { it.typeId }, typeIds)
    }

    @Test
    fun heartRateDialLightsTicksFromSixty() {
        val (w, h) = 478 to 721
        val l = RideRenderer.layout(w, h)
        val m = RideGauge.model(state().copy(dial = DialValue.HEART_RATE, heartRate = 150))
        val bmp = RideRenderer.render(m, w, h, Bitmap.Config.ARGB_8888)
        fun px(bpm: Int) = RideRenderer.pointAt(l, RideRenderer.angleFor((bpm - 60) / 140f), l.radius * (RideRenderer.MAJOR_INNER + RideRenderer.TICK_OUTER) / 2f)
            .let { (x, y) -> bmp.getPixel(x.toInt(), y.toInt()) }
        assertNear("60 bpm tick lit", RideRenderer.TICK_LIT, px(60))
        assertNear("140 bpm tick lit", RideRenderer.TICK_LIT, px(140))
        assertNear("180 bpm tick dim", RideRenderer.TICK_DIM, px(180))
    }

    @Test
    fun dialAndRoundFieldsStayInsideTheField() {
        for ((w, h) in sizes) {
            val l = RideRenderer.layout(w, h)
            assertTrue("$w x $h dial left", l.cx - l.radius >= 0f)
            assertTrue("$w x $h dial top", l.cy - l.radius >= 0f)
            assertTrue("$w x $h dial bottom", l.cy + l.radius * 0.7072f <= h)
            for (f in l.fields) {
                if (f.line != null) {
                    val b = f.line!!
                    assertTrue("$w x $h line $b", b.left >= 0 && b.top >= 0 && b.right <= w && b.bottom <= h)
                    assertTrue("$w x $h line overlaps dial", b.left >= l.cx + l.radius)
                    continue
                }
                val rr = f.diameter / 2f
                assertTrue("$w x $h circle $f", f.cx - rr >= 0 && f.cy - rr >= 0 && f.cx + rr <= w + 0.5f && f.cy + rr <= h + 0.5f)
                assertTrue("$w x $h circle overlaps dial", f.cx - rr >= l.cx + l.radius || f.cy - rr >= l.cy + l.radius * 0.7f)
            }
        }
        // Status row (battery left, gear right) sits above the dial in every size.
        for ((w, h) in sizes) {
            val l = RideRenderer.layout(w, h)
            assertTrue("$w x $h status row above dial", l.statusBottom <= l.cy - l.radius + 0.5f)
            assertTrue("$w x $h status row inside", l.statusTop >= 0f)
        }
        // Full-page field: the row starts right below the Karoo's ride bar (top ~6.3 %), and the dial follows
        // straight after it, so the content sits high with the spare height at the bottom.
        val full = RideRenderer.layout(478, 721)
        assertTrue(full.statusTop >= 721 * 0.06f && full.statusTop <= 721 * 0.07f)
        assertTrue(full.cy - full.radius - full.statusBottom <= full.padding)
        // Wide half-screen field: three stacked circles; short strip: circle, line, circle.
        assertTrue(RideRenderer.layout(478, 456).fields.all { it.line == null })
        assertTrue(RideRenderer.layout(478, 228).fields[1].line != null)
    }

    @Test
    fun ticksLightUpToTheSpeedAndPointerIsRed() {
        val (w, h) = 478 to 456
        val l = RideRenderer.layout(w, h)
        val m = RideGauge.model(state()) // 28.4 of 60 km/h
        val bmp = RideRenderer.render(m, w, h, Bitmap.Config.ARGB_8888)
        fun px(pos: Float, r: Float) = RideRenderer.pointAt(l, RideRenderer.angleFor(pos), l.radius * r)
            .let { (x, y) -> bmp.getPixel(x.toInt(), y.toInt()) }
        val majorMid = (RideRenderer.MAJOR_INNER + RideRenderer.TICK_OUTER) / 2f
        assertNear("10 km/h tick lit", RideRenderer.TICK_LIT, px(10f / 60f, majorMid))
        assertNear("20 km/h tick lit", RideRenderer.TICK_LIT, px(20f / 60f, majorMid))
        assertNear("50 km/h tick dim", RideRenderer.TICK_DIM, px(50f / 60f, majorMid))
        assertNear("pointer", RideRenderer.NEEDLE, px(m.position!!, 0.88f))
    }

    @Test
    fun roundFieldsUseTheSlotColor() {
        val (w, h) = 478 to 721
        val l = RideRenderer.layout(w, h)
        val m = RideGauge.model(state())
        val bmp = RideRenderer.render(m, w, h, Bitmap.Config.ARGB_8888)
        for ((i, f) in l.fields.withIndex()) {
            val border = maxOf(2.5f, f.diameter * 0.045f)
            val x = (f.cx - f.diameter / 2f + border / 2f).toInt()
            assertNear("field ${i + 1} ring", m.slots[i].color, bmp.getPixel(x, f.cy.toInt()))
        }
    }

    @Test
    fun writesDrivetrainBatteryPreviews() {
        val cases = DrivetrainBattery.entries.map { "axs_${it.name.lowercase()}" to ShiftBattery("AXS", it) } +
            listOf(87.0, 58.0, 22.0, 6.0).map { "di2_${it.toInt()}" to ShiftBattery.di2(it) } +
            listOf("unknown" to null)
        for ((name, b) in cases) {
            val bmp = RideRenderer.render(RideGauge.model(state().copy(shiftBattery = b)), 478, 721, Bitmap.Config.ARGB_8888)
            File(outDir, "ride_battery_${name}_478x721.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun axsFullAndStatusSpots() {
        assertEquals("FULL", ShiftBattery.axs(0.0)!!.text)
        assertEquals("CRITICAL", ShiftBattery.axs(4.0)!!.text)
        val m = RideGauge.model(state())
        assertEquals(StatusItem.Battery(ShiftBattery("AXS", DrivetrainBattery.GOOD)), m.statusLeft)
        assertEquals(StatusItem.Value("40 / 15", "GEAR", true), m.statusRight)
        val other = RideGauge.model(
            state().copy(statusLeft = StatusValue.TEMPERATURE, statusRight = StatusValue.TO_FINISH, temperatureC = 12.34),
        )
        assertEquals(StatusItem.Value("12.3", "TEMP °C", true), other.statusLeft)
        assertEquals(StatusItem.Value("--", "TO FINISH km", false), other.statusRight)
        val none = RideGauge.model(state().copy(statusLeft = StatusValue.NONE, statusRight = StatusValue.NONE))
        assertNull(none.statusLeft)
        assertNull(none.statusRight)
    }

    @Test
    fun withoutStatusRowTheDialMovesUp() {
        for ((w, h) in sizes) {
            val with = RideRenderer.layout(w, h)
            val without = RideRenderer.layout(w, h, statusRow = false)
            assertTrue("$w x $h dial not lower", without.cy - without.radius <= with.cy - with.radius + 0.5f)
            assertTrue("$w x $h dial inside", without.cy - without.radius >= 0f && without.cy + without.radius * 0.7072f <= h)
        }
    }

    @Test
    fun lightModeUsesLightCardAndBrightColors() {
        val (w, h) = 478 to 721
        val l = RideRenderer.layout(w, h)
        val m = RideGauge.model(state().copy(light = true))
        assertEquals(RideTheme(light = true), m.theme)
        val bmp = RideRenderer.render(m, w, h, Bitmap.Config.ARGB_8888)
        assertNear("light background", m.theme.background, bmp.getPixel(2, h - 2))
        val f = l.fields[2]
        val border = maxOf(2.5f, f.diameter * 0.045f)
        // Field 3 is cadence 88 rpm: turquoise, drawn in its bright variant.
        assertNear("bright ring", RideTheme.BRIGHT_ZONES[1], bmp.getPixel((f.cx - f.diameter / 2f + border / 2f).toInt(), f.cy.toInt()))
        // Dark mode keeps the neon colours unless bright is chosen.
        assertEquals(GaugePalette.ZONES[1], RideTheme.DARK.color(GaugePalette.ZONES[1]))
        assertEquals(RideTheme.BRIGHT_ZONES[1], RideTheme(brightColors = true).color(GaugePalette.ZONES[1]))
        File(outDir, "ride_light_478x721.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun assertNear(label: String, expected: Int, actual: Int) {
        val d = abs(Color.red(expected) - Color.red(actual)) + abs(Color.green(expected) - Color.green(actual)) +
            abs(Color.blue(expected) - Color.blue(actual))
        assertTrue("$label: expected #%06X got #%06X".format(expected and 0xFFFFFF, actual and 0xFFFFFF), d < 40)
    }
}
