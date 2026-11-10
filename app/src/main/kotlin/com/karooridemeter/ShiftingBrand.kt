package com.karooridemeter

/**
 * Which brand of shifting the Karoo's own shifting battery level comes from. The SHIFTING_BATTERY stream has no
 * brand, so the name is looked up in the Karoo's saved sensors: the sensor the data point came from (its sourceId),
 * or else the only kind of shifting among the enabled sensors.
 */
object ShiftingBrand {
    const val SRAM = "AXS"
    const val CAMPAGNOLO = "Campa"
    const val SHIMANO = "Di2"
    /** Brand not known: a neutral name instead of guessing AXS. */
    const val UNKNOWN = "Batt"

    /** One saved Karoo sensor, as far as this lookup needs it. */
    data class Sensor(
        val id: String,
        val name: String?,
        val manufacturer: String?,
        val enabled: Boolean,
        val dataTypes: List<String>,
    ) {
        val isShifting: Boolean get() = dataTypes.any { "SHIFTING" in it.uppercase() }
        /** Brand from the manufacturer, else from the sensor name ("SRAM RD", "Campagnolo ..."). */
        val brand: String? get() = brandOf(manufacturer) ?: brandOf(name)
    }

    fun brandOf(text: String?): String? {
        val t = text?.lowercase() ?: return null
        return when {
            "sram" in t || "axs" in t -> SRAM
            "campa" in t -> CAMPAGNOLO
            "shimano" in t || "di2" in t -> SHIMANO
            else -> null
        }
    }

    /** Name to show for the battery that came from [sourceId], given the saved sensors. */
    fun label(sourceId: String?, sensors: List<Sensor>): String {
        if (!sourceId.isNullOrEmpty()) {
            sensors.firstOrNull { it.id == sourceId || (it.id.isNotEmpty() && (it.id in sourceId || sourceId in it.id)) }
                ?.let { return it.brand ?: UNKNOWN }
        }
        val brands = sensors.filter { it.enabled && it.isShifting }.map { it.brand }.distinct()
        return brands.singleOrNull() ?: UNKNOWN
    }
}
