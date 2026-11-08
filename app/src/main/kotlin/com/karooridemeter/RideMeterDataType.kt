package com.karooridemeter

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ride Meter data field (typeId "ride-meter", graphical="true").
 *
 * Combined field: 270° dial face with numbers and a red pointer, speed, gear (front / rear teeth from
 * electronic shifting), distance and the shifting battery (SRAM AXS level from the Karoo, or Shimano Di2 %
 * from the Ki2 extension), plus three round fields whose
 * values are chosen in Settings (default slope %, time of day, cadence).
 *
 * Units the SDK does not document (checked against the device log, see GaugeViewSession):
 * speed m/s, distance m, ride time ms, grade % and elevation m (confirmed from the ride file),
 * temperature °C, power to weight W/kg. AXS battery: assumed BatteryStatus ordinal (see DrivetrainBattery).
 */
class RideMeterDataType(
    private val karooSystem: KarooSystemService,
    extension: String,
) : DataTypeImpl(extension, TYPE_ID) {

    /** Passes the Karoo speed through as this field's numeric value. */
    override fun startStream(emitter: Emitter<StreamState>) {
        val consumerId = karooSystem.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.SPEED),
        ) { event: OnStreamState ->
            val state = event.state
            emitter.onNext(
                if (state is StreamState.Streaming) {
                    val v = state.dataPoint.values[DataType.Field.SPEED] ?: state.dataPoint.singleValue
                    if (v == null) {
                        StreamState.NotAvailable
                    } else {
                        StreamState.Streaming(DataPoint(dataTypeId, values = mapOf(DataType.Field.SINGLE to v)))
                    }
                } else {
                    state
                },
            )
        }
        emitter.setCancellable { karooSystem.removeConsumer(consumerId) }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(RideMeterExtension.TAG, "Ride view started: $config")
        val appContext = context.applicationContext
        val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val session = GaugeViewSession<RideModel>(
            name = "Ride",
            context = context,
            config = config,
            emitter = emitter,
            karooSystem = karooSystem,
            graphicConfig = UpdateGraphicConfig(showHeader = false, formatDataTypeId = DataType.Type.SPEED),
            streams = mapOf(
                DataType.Type.SPEED to DataType.Field.SPEED,
                DataType.Type.DISTANCE to DataType.Field.DISTANCE,
                DataType.Type.RIDE_TIME to DataType.Field.RIDE_TIME,
                DataType.Type.ELEVATION_GRADE to DataType.Field.ELEVATION_GRADE,
                DataType.Type.CADENCE to DataType.Field.CADENCE,
                // AXS battery level; the rear derailleur field is also stored as "typeId/field".
                DataType.Type.SHIFTING_BATTERY to DataType.Field.SHIFTING_BATTERY_STATUS_REAR_DERAILLEUR,
                // Shimano Di2 battery % from the Ki2 extension (Hammerhead does not support Di2 itself).
                KI2_BATTERY to DataType.Field.SINGLE,
                DataType.Type.SHIFTING_FRONT_GEAR to DataType.Field.SHIFTING_FRONT_GEAR_TEETH,
                DataType.Type.SHIFTING_REAR_GEAR to DataType.Field.SHIFTING_REAR_GEAR_TEETH,
                // Values the three boxes can show (chosen in Settings, so all are subscribed).
                DataType.Type.HEART_RATE to DataType.Field.HEART_RATE,
                DataType.Type.POWER to DataType.Field.POWER,
                DataType.Type.SMOOTHED_3S_AVERAGE_POWER to DataType.Field.SMOOTHED_3S_AVERAGE_POWER,
                DataType.Type.POWER_TO_WEIGHT to DataType.Field.POWER_TO_WEIGHT,
                DataType.Type.AVERAGE_POWER to DataType.Field.AVERAGE_POWER,
                DataType.Type.NORMALIZED_POWER to DataType.Field.NORMALIZED_POWER,
                DataType.Type.AVERAGE_SPEED to DataType.Field.AVERAGE_SPEED,
                DataType.Type.MAX_SPEED to DataType.Field.MAX_SPEED,
                DataType.Type.ELEVATION_GAIN to DataType.Field.ELEVATION_GAIN,
                DataType.Type.TEMPERATURE to DataType.Field.TEMPERATURE,
                DataType.Type.CALORIES to DataType.Field.CALORIES,
                // Extra values for the spots above the dial.
                DataType.Type.PRESSURE_ELEVATION_CORRECTION to DataType.Field.PRESSURE_ELEVATION,
                DataType.Type.DISTANCE_TO_DESTINATION to DataType.Field.DISTANCE_TO_DESTINATION,
            ),
            wantsProfile = true,
            render = { m, w, h -> RideRenderer.render(m, w, h, Bitmap.Config.RGB_565) },
        ) { values, profile ->
            // Teeth come from the gear streams' TEETH fields (also stored as "typeId/field").
            fun teeth(typeId: String, field: String) =
                (values["$typeId/$field"] ?: values[typeId])?.roundToInt()?.takeIf { it > 0 }
            fun int(typeId: String) = values[typeId]?.roundToInt()?.coerceAtLeast(0)
            fun kmh(typeId: String) = values[typeId]?.let { (it * MS_TO_KMH).coerceAtLeast(0.0) }
            val powerZones = profile?.let { p -> PowerZones.resolve(p.powerZones.map { PowerZone(it.min, it.max) }, p.ftp) }
            val hrZones = profile?.let { p -> HeartRateZones.resolve(p.heartRateZones.map { HrZone(it.min, it.max) }, p.maxHr) }
            RideGauge.model(
                RideState(
                    kmh = kmh(DataType.Type.SPEED),
                    scaleMaxKmh = RideSettings.scaleMaxKmh(appContext),
                    frontTeeth = teeth(DataType.Type.SHIFTING_FRONT_GEAR, DataType.Field.SHIFTING_FRONT_GEAR_TEETH),
                    rearTeeth = teeth(DataType.Type.SHIFTING_REAR_GEAR, DataType.Field.SHIFTING_REAR_GEAR_TEETH),
                    distanceKm = values[DataType.Type.DISTANCE]?.let { (it / 1000.0).coerceAtLeast(0.0) },
                    rideTimeSec = values[DataType.Type.RIDE_TIME]?.let { (it / 1000.0).toLong() },
                    gradePct = values[DataType.Type.ELEVATION_GRADE],
                    cadenceRpm = values[DataType.Type.CADENCE]?.roundToInt(),
                    shiftBattery = SHIFT_BATTERY.update(
                        axsRaw = values["${DataType.Type.SHIFTING_BATTERY}/${DataType.Field.SHIFTING_BATTERY_STATUS_REAR_DERAILLEUR}"]
                            ?: values["${DataType.Type.SHIFTING_BATTERY}/${DataType.Field.SHIFTING_BATTERY_STATUS}"],
                        di2Pct = values[KI2_BATTERY],
                    ),
                    clock = clockFormat.format(Date()),
                    // Read on every frame so a Settings change applies straight away.
                    slots = RideSettings.rideSlots(appContext),
                    underSpeed = RideSettings.underSpeed(appContext),
                    statusLeft = RideSettings.statusLeft(appContext),
                    statusRight = RideSettings.statusRight(appContext),
                    light = isLightMode(appContext),
                    brightColors = RideSettings.darkBrightColors(appContext),
                    altitudeM = values[DataType.Type.PRESSURE_ELEVATION_CORRECTION],
                    // Assumed metres, like DISTANCE; only streams while following a route.
                    toFinishKm = values[DataType.Type.DISTANCE_TO_DESTINATION]?.let { (it / 1000.0).coerceAtLeast(0.0) },
                    heartRate = int(DataType.Type.HEART_RATE),
                    power = int(DataType.Type.POWER),
                    power3s = int(DataType.Type.SMOOTHED_3S_AVERAGE_POWER),
                    powerToWeight = values[DataType.Type.POWER_TO_WEIGHT]?.coerceAtLeast(0.0),
                    avgPower = int(DataType.Type.AVERAGE_POWER),
                    np = int(DataType.Type.NORMALIZED_POWER),
                    avgKmh = kmh(DataType.Type.AVERAGE_SPEED),
                    maxKmh = kmh(DataType.Type.MAX_SPEED),
                    climbM = values[DataType.Type.ELEVATION_GAIN]?.coerceAtLeast(0.0),
                    temperatureC = values[DataType.Type.TEMPERATURE],
                    calories = int(DataType.Type.CALORIES),
                    powerZones = powerZones ?: emptyList(),
                    hrZones = hrZones ?: emptyList(),
                ),
            )
        }
        session.start()
        emitter.setCancellable {
            Log.d(RideMeterExtension.TAG, "Ride view stopped")
            session.stop()
        }
    }

    /**
     * True when the Karoo is in light mode. The Karoo sets the system night mode when the rider switches
     * between dark and light (checked on the device: "Night mode: yes" in dark, "no" in light).
     */
    private fun isLightMode(context: Context): Boolean {
        val ui = context.getSystemService(UiModeManager::class.java)
        return when (ui?.nightMode) {
            UiModeManager.MODE_NIGHT_YES -> false
            UiModeManager.MODE_NIGHT_NO -> true
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO
        }
    }

    companion object {
        /** Must match the typeId attribute in res/xml/extension_info.xml. */
        const val TYPE_ID = "ride-meter"

        /** Karoo speed is m/s (confirmed on rides). */
        const val MS_TO_KMH = 3.6

        /** Shared by all Ride Meter fields in this app, so Di2 is remembered across pages. */
        val SHIFT_BATTERY = ShiftBatteryTracker()

        /** Ki2 extension (id "ki2", com.valterc.ki2) numeric field: Shimano Di2 battery 0-100 %. */
        val KI2_BATTERY = DataType.dataTypeId("ki2", "DATATYPE_SHIFTING_BATTERY_PERCENTAGE")
    }
}
