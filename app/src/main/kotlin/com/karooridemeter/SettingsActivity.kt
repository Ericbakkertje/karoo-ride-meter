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
 * Settings screen: dial scale, the line under the speed, the spots above the dial, dark-mode colours
 * and what the three round fields show.
 * Choices are saved immediately; an open Ride Meter updates within a second.
 */
class SettingsActivity : Activity() {

    /** Scale max (km/h) -> its radio button. Internal for tests. */
    internal val scaleButtons = linkedMapOf<Int, RadioButton>()

    /** Line under the speed -> its radio button. Internal for tests. */
    internal val underSpeedButtons = linkedMapOf<UnderSpeed, RadioButton>()

    /** Field index -> its dropdown. Internal for tests. */
    internal val slotSpinners = mutableListOf<Spinner>()

    /** Top-left and top-right dropdowns. Internal for tests. */
    internal lateinit var statusLeftSpinner: Spinner
    internal lateinit var statusRightSpinner: Spinner

    /** Colour style -> its radio button. Internal for tests. */
    internal val colorButtons = linkedMapOf<ColorStyle, RadioButton>()

    /** Round field style: false = number in a circle, true = small dial -> its radio button. Internal for tests. */
    internal val miniDialButtons = linkedMapOf<Boolean, RadioButton>()

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
        root.addView(TextView(this).apply { text = getString(R.string.settings_scale_imperial_note) })

        // Line under the speed
        root.addView(sectionTitle(R.string.settings_under_speed))
        val under = RadioGroup(this)
        UnderSpeed.entries.forEach { option ->
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = option.settingsName
                textSize = 18f
                minHeight = (48 * density).toInt()
            }
            under.addView(button)
            underSpeedButtons[option] = button
        }
        underSpeedButtons[RideSettings.underSpeed(this)]?.let { under.check(it.id) }
        under.setOnCheckedChangeListener { _, checkedId ->
            underSpeedButtons.entries.firstOrNull { it.value.id == checkedId }?.let { RideSettings.setUnderSpeed(this, it.key) }
        }
        root.addView(under)

        // Spots above the dial
        root.addView(sectionTitle(R.string.settings_status))
        root.addView(label(R.string.settings_status_left))
        statusLeftSpinner = statusSpinner(RideSettings.statusLeft(this)) { RideSettings.setStatusLeft(this, it) }
        root.addView(statusLeftSpinner)
        root.addView(label(R.string.settings_status_right))
        statusRightSpinner = statusSpinner(RideSettings.statusRight(this)) { RideSettings.setStatusRight(this, it) }
        root.addView(statusRightSpinner)

        // Colours
        root.addView(sectionTitle(R.string.settings_dark_colors))
        root.addView(radioGroup(
            listOf(
                ColorStyle.NEON to R.string.settings_dark_colors_normal,
                ColorStyle.BRIGHT to R.string.settings_dark_colors_bright,
                ColorStyle.CLASSIC to R.string.settings_colors_classic,
            ),
            colorButtons, RideSettings.colorStyle(this),
        ) { RideSettings.setColorStyle(this, it) })
        root.addView(TextView(this).apply { text = getString(R.string.settings_light_note) })

        // Round fields
        root.addView(sectionTitle(R.string.settings_ride_boxes))
        root.addView(radioGroup(
            listOf(false to R.string.settings_round_number, true to R.string.settings_round_dial),
            miniDialButtons, RideSettings.miniDials(this),
        ) { RideSettings.setMiniDials(this, it) })
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
            text = getString(R.string.settings_ride_boxes_note)
            setPadding(0, pad / 2, 0, 0)
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.settings_note)
            setPadding(0, pad, 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    /** One radio button per option; the current one checked; [save] on change. */
    private fun <T> radioGroup(options: List<Pair<T, Int>>, buttons: MutableMap<T, RadioButton>, current: T, save: (T) -> Unit): RadioGroup {
        val group = RadioGroup(this)
        options.forEach { (value, res) ->
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = getString(res)
                textSize = 18f
                minHeight = (48 * density).toInt()
            }
            group.addView(button)
            buttons[value] = button
        }
        buttons[current]?.let { group.check(it.id) }
        group.setOnCheckedChangeListener { _, checkedId ->
            buttons.entries.firstOrNull { it.value.id == checkedId }?.let { save(it.key) }
        }
        return group
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

    private fun label(textRes: Int) = TextView(this).apply {
        text = getString(textRes)
        textSize = 16f
        setPadding(0, pad / 2, 0, 0)
    }

    private fun statusSpinner(selected: StatusValue, save: (StatusValue) -> Unit): Spinner {
        val options = StatusValue.entries
        return Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                options.map { it.settingsName },
            )
            setSelection(options.indexOf(selected), false)
            minimumHeight = (48 * density).toInt()
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = save(options[position])
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }

    private fun sectionTitle(textRes: Int) = TextView(this).apply {
        text = getString(textRes)
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, pad, 0, pad / 2)
    }
}
