package com.karooridemeter

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.RemoteViews
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One on-screen instance of a graphical field: Karoo stream subscriptions, latest values,
 * and a render loop that sends the gauge bitmap through ViewEmitter.updateView.
 *
 * @param streams Karoo data type id -> DataType.Field key to read from its DataPoint.
 * @param buildModel builds the frame from the latest raw values (keyed by data type id;
 *        missing = not streaming) and the latest user profile (null until received).
 */
class GaugeViewSession<T : Any>(
    private val name: String,
    private val context: Context,
    private val config: ViewConfig,
    private val emitter: ViewEmitter,
    private val karooSystem: KarooSystemService,
    private val graphicConfig: UpdateGraphicConfig,
    private val streams: Map<String, String>,
    private val wantsProfile: Boolean,
    /** Draws one frame; default for the semicircle gauges is [GaugeRenderer.render]. */
    private val render: (model: T, width: Int, height: Int) -> Bitmap,
    private val buildModel: (values: Map<String, Double>, profile: UserProfile?) -> T,
) {
    private val values = ConcurrentHashMap<String, Double>()
    @Volatile private var profile: UserProfile? = null

    private val consumerIds = mutableListOf<String>()
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var lastRendered: T? = null
    private val loggedTypes = ConcurrentHashMap.newKeySet<String>()

    fun start() {
        emitter.onNext(graphicConfig)

        streams.forEach { (typeId, field) ->
            consumerIds += karooSystem.addConsumer(OnStreamState.StartStreaming(typeId)) { event: OnStreamState ->
                val state = event.state
                val point = (state as? StreamState.Streaming)?.dataPoint
                val v = point?.let { it.values[field] ?: it.singleValue }
                if (v != null) values[typeId] = v else values.remove(typeId)
                // Every field of the data point is also kept as "typeId/fieldKey" (e.g. gear teeth).
                point?.values?.forEach { (k, fv) -> values["$typeId/$k"] = fv }
                // Log the first raw values of each stream once, to check undocumented units on the device.
                if (point != null && loggedTypes.add(typeId)) {
                    Log.d(RideMeterExtension.TAG, "$name: first $typeId = ${point.values}")
                }
            }
        }
        if (wantsProfile) {
            consumerIds += karooSystem.addConsumer { p: UserProfile ->
                profile = p
                Log.d(RideMeterExtension.TAG, "$name: profile received (FTP ${p.ftp}, ${p.powerZones.size} power zones, max HR ${p.maxHr}, ${p.heartRateZones.size} HR zones)")
            }
        }

        // Render at most twice a second, and only when the frame changed.
        executor.scheduleWithFixedDelay({ renderIfChanged() }, 0, 500, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        consumerIds.forEach { karooSystem.removeConsumer(it) }
        consumerIds.clear()
        executor.shutdownNow()
    }

    private fun renderIfChanged() {
        try {
            val model = buildModel(HashMap(values), profile)
            if (model == lastRendered) return
            val (w, h) = renderSize()
            val bitmap = render(model, w, h)
            val views = RemoteViews(context.packageName, R.layout.view_gauge).apply {
                setImageViewBitmap(R.id.gauge_image, bitmap)
            }
            emitter.updateView(views)
            lastRendered = model
        } catch (e: Exception) {
            Log.e(RideMeterExtension.TAG, "$name: render failed", e)
        }
    }

    /**
     * Field size in pixels from ViewConfig, scaled down if very large so the bitmap sent
     * to the Karoo stays small (the ImageView scales it back up to fill the field).
     */
    private fun renderSize(): Pair<Int, Int> {
        val w = config.viewSize.first.takeIf { it > 0 } ?: 480
        val h = config.viewSize.second.takeIf { it > 0 } ?: 240
        val pixels = w.toLong() * h
        if (pixels <= MAX_PIXELS) return w to h
        val scale = sqrt(MAX_PIXELS.toDouble() / pixels)
        return (w * scale).roundToInt() to (h * scale).roundToInt()
    }

    companion object {
        /** About 400 KB per frame as RGB_565. */
        const val MAX_PIXELS = 200_000L
    }
}
