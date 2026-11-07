package com.karooridemeter

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/**
 * Settings screen: dial scale and what the three round fields show.
 * Choices are saved immediately; an open Ride Meter updates within a second.
 */
class SettingsActivity : Activity() {

    /** Scale max (km/h) -> its radio button. Internal for tests. */
    internal val scaleButtons = linkedMapOf<Int, RadioButton>()

    /** Field index -> its dropdown. Internal for tests. */
    internal val slotSpinners = mutableListOf<Spinner>()

    private val density get() = resources.displayMetrics.density
    private val pad get() = (16 * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })

        // Dial scale
        root.addView(sectionTitle(R.string.settings_speed_scale))
        val group = RadioGroup(this)
        RideSettings.SCALE_OPTIONS_KMH.forEach { max ->
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = getString(R.string.settings_scale_option, max, RideRenderer.tickSteps(max).first)
                textSize = 18f
                minHeight = (48 * density).toInt()
            }
            group.addView(button)
            scaleButtons[max] = button
        }
        scaleButtons[RideSettings.scaleMaxKmh(this)]?.let { group.check(it.id) }
        group.setOnCheckedChangeListener { _, checkedId ->
            scaleButtons.entries.firstOrNull { it.value.id == checkedId }?.let { RideSettings.setScaleMaxKmh(this, it.key) }
        }
        root.addView(group)

        // Round fields
        root.addView(sectionTitle(R.string.settings_ride_boxes))
        val current = RideSettings.rideSlots(this)
        val names = listOf(R.string.settings_ride_box_1, R.string.settings_ride_box_2, R.string.settings_ride_box_3)
        names.forEachIndexed { i, nameRes ->
            root.addView(TextView(this).apply {
                text = getString(nameRes)
                textSize = 16f
                setPadding(0, pad / 2, 0, 0)
            })
            root.addView(slotSpinner(i, current[i]))
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.settings_note)
            setPadding(0, pad, 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun slotSpinner(index: Int, selected: RideSlotValue): Spinner {
        val options = RideSlotValue.entries
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                options.map { it.settingsName },
            )
            setSelection(options.indexOf(selected), false)
            minimumHeight = (48 * density).toInt()
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    RideSettings.setRideSlot(this@SettingsActivity, index, options[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        slotSpinners += spinner
        return spinner
    }

    private fun sectionTitle(textRes: Int) = TextView(this).apply {
        text = getString(textRes)
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, pad, 0, pad / 2)
    }
}
