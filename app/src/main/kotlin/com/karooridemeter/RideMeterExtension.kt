package com.karooridemeter

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension

/**
 * Karoo extension service for the Ride Meter. The Karoo system binds to it (see AndroidManifest.xml)
 * and asks [types] for the data fields declared in res/xml/extension_info.xml (one per [DialValue]).
 */
class RideMeterExtension : KarooExtension(EXTENSION_ID, BuildConfig.VERSION_NAME) {

    private val karooSystem by lazy { KarooSystemService(applicationContext) }

    override val types by lazy { DialValue.entries.map { RideMeterDataType(karooSystem, extension, it) } }

    override fun onCreate() {
        super.onCreate()
        karooSystem.connect { connected ->
            Log.i(TAG, "Karoo system connected: $connected")
        }
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }

    companion object {
        /** Must match the id attribute in res/xml/extension_info.xml. */
        const val EXTENSION_ID = "karoo-ride-meter"
        const val TAG = "KarooRideMeter"
    }
}
