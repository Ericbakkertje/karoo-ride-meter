package com.karooridemeter

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsActivityTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun defaultScaleIsSixtyAndPreselected() {
        assertEquals(60, RideSettings.scaleMaxKmh(context))
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertEquals(RideSettings.SCALE_OPTIONS_KMH, activity.scaleButtons.keys.toList())
        assertTrue(activity.scaleButtons.getValue(60).isChecked)
        assertEquals("0–60 km/h (numbers every 10)", activity.scaleButtons.getValue(60).text.toString())
        assertEquals("0–45 km/h (numbers every 5)", activity.scaleButtons.getValue(45).text.toString())
    }

    @Test
    fun choosingAScaleSavesItImmediately() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        activity.scaleButtons.getValue(90).performClick()
        assertEquals(90, RideSettings.scaleMaxKmh(context))
        assertEquals(90, RideGauge.model(RideState(kmh = 30.0, scaleMaxKmh = RideSettings.scaleMaxKmh(context))).scaleMax)
    }

    @Test
    fun underSpeedDefaultsToDistanceAndSavesChoice() {
        assertEquals(UnderSpeed.DISTANCE, RideSettings.underSpeed(context))
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertEquals(UnderSpeed.entries, activity.underSpeedButtons.keys.toList())
        assertTrue(activity.underSpeedButtons.getValue(UnderSpeed.DISTANCE).isChecked)
        activity.underSpeedButtons.getValue(UnderSpeed.AVG_SPEED).performClick()
        assertEquals(UnderSpeed.AVG_SPEED, RideSettings.underSpeed(context))
    }

    @Test
    fun fieldsDefaultAndSaveChoice() {
        assertEquals(RideSlotValue.DEFAULTS, RideSettings.rideSlots(context))
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertEquals(3, activity.slotSpinners.size)
        assertEquals(RideSlotValue.CLOCK.ordinal, activity.slotSpinners[1].selectedItemPosition)
        activity.slotSpinners[1].setSelection(RideSlotValue.HEART_RATE.ordinal)
        ShadowLooper.idleMainLooper()
        assertEquals(
            listOf(RideSlotValue.SLOPE, RideSlotValue.HEART_RATE, RideSlotValue.CADENCE),
            RideSettings.rideSlots(context),
        )
    }

    @Test
    fun statusSpotsDefaultAndSaveChoice() {
        assertEquals(StatusValue.SHIFT_BATTERY, RideSettings.statusLeft(context))
        assertEquals(StatusValue.GEAR, RideSettings.statusRight(context))
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertEquals(StatusValue.GEAR.ordinal, activity.statusRightSpinner.selectedItemPosition)
        activity.statusLeftSpinner.setSelection(StatusValue.TEMPERATURE.ordinal)
        activity.statusRightSpinner.setSelection(StatusValue.NONE.ordinal)
        ShadowLooper.idleMainLooper()
        assertEquals(StatusValue.TEMPERATURE, RideSettings.statusLeft(context))
        assertEquals(StatusValue.NONE, RideSettings.statusRight(context))
    }

    @Test
    fun colorsDefaultToNeonAndSaveChoice() {
        assertEquals(ColorStyle.NEON, RideSettings.colorStyle(context))
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertTrue(activity.colorButtons.getValue(ColorStyle.NEON).isChecked)
        activity.colorButtons.getValue(ColorStyle.CLASSIC).performClick()
        assertEquals(ColorStyle.CLASSIC, RideSettings.colorStyle(context))
        activity.colorButtons.getValue(ColorStyle.BRIGHT).performClick()
        assertEquals(ColorStyle.BRIGHT, RideSettings.colorStyle(context))
    }

    @Test
    fun oldBrightSettingStillRead() {
        context.getSharedPreferences("ride_meter_settings", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("dark_bright_colors", true).commit()
        assertEquals(ColorStyle.BRIGHT, RideSettings.colorStyle(context))
    }

    @Test
    fun roundFieldsDefaultToNumberAndSaveDialChoice() {
        assertEquals(false, RideSettings.miniDials(context))
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertTrue(activity.miniDialButtons.getValue(false).isChecked)
        activity.miniDialButtons.getValue(true).performClick()
        assertEquals(true, RideSettings.miniDials(context))
    }

    @Test
    fun doneButtonsCloseTheScreen() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertEquals(2, activity.doneButtons.size)
        activity.doneButtons.first().performClick()
        assertTrue(activity.isFinishing)
        val again = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        again.doneButtons.last().performClick()
        assertTrue(again.isFinishing)
    }
}
