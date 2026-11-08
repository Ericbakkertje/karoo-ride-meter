package com.karooridemeter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One data field: a circle (centre and diameter), or a one-line "LABEL: value" when [line] is set. */
data class RideField(val cx: Float, val cy: Float, val diameter: Float, val line: RectF? = null)

/** Where the Ride Meter parts go inside a field. */
data class RideLayout(
    val cx: Float,
    val cy: Float,
    /** Outer radius of the dial (the thin ring). */
    val radius: Float,
    /** The three data fields (top/left first); empty in small fields. */
    val fields: List<RideField>,
    val padding: Float,
    /** Status row above the dial: battery on the left, gear on the right. Top edge and icon height. */
    val statusTop: Float = padding,
    val statusIcon: Float = 8f,
    /** False when both status spots are set to "Nothing": no status row, the dial starts at [statusTop]. */
    val hasStatus: Boolean = true,
) {
    /** Bottom of the status row (icon + one text line). */
    val statusBottom: Float get() = if (hasStatus) statusTop + statusIcon * STATUS_ROW else statusTop
    val dialLeft: Float get() = cx - radius
    val dialRight: Float get() = cx + radius
}

/** Status row height as a multiple of the icon height (icon + gap + text line). */
private const val STATUS_ROW = 2.85f

/**
 * Draws the Ride Meter: a 270° dial face with ticks and numbers (ticks up to the speed lit white),
 * a red pointer, speed in the middle with gear and distance under it, the AXS battery top-left,
 * and three round data fields beside the dial (wide fields) or under it (tall fields).
 */
object RideRenderer {
    /** Dial runs clockwise from bottom-left (135°) over the top to bottom-right (405°). */
    const val START_DEG = 135f
    const val SWEEP_DEG = 270f
    /** Dial height below the centre: the ends are at sin(45°) of the radius. */
    private const val BELOW = 0.7072f

    /** Dark-mode colours (the light ones are in [RideTheme]). */
    const val NEEDLE = 0xFFFF3B30.toInt()
    const val TICK_LIT = 0xFFFFFFFF.toInt()
    const val TICK_DIM = 0xFF5B6B85.toInt()
    const val RING = 0xFF33415C.toInt()

    /** Share of a full-page field covered by the Karoo's ride bar while recording or paused (about 45 of 721 px). */
    const val RIDE_BAR = 0.063f

    /** Three stacked circles in a wide field need at least this diameter; otherwise circle / line / circle. */
    private const val MIN_STACKED = 90f

    fun angleFor(position: Float) = START_DEG + SWEEP_DEG * position.coerceIn(0f, 1f)

    fun layout(width: Int, height: Int, statusRow: Boolean = true): RideLayout {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = maxOf(3f, min(w, h) * 0.03f)
        // Status row: icon height from the field size. On a full-page field the Karoo's ride bar (time, paused)
        // covers about the top 45 px (6.3 %) of the field, so the row starts right below it there.
        val icon = maxOf(8f, min(w, h) * 0.047f)
        val statusTop = if (h >= 600f) maxOf(pad, h * RIDE_BAR) else pad
        val statusBottom = if (statusRow) statusTop + icon * STATUS_ROW else statusTop
        val gap = pad * 0.5f
        fun fit(aw: Float, ah: Float) = min(aw / 2f, ah / (1f + BELOW))

        if (h < 150f || w < 200f) {
            val r = fit(w - 2 * pad, h - pad - statusBottom - gap)
            val top = statusBottom + gap + (h - pad - statusBottom - gap - r * (1f + BELOW)) / 2f
            return RideLayout(w / 2f, top + r, r, emptyList(), pad, statusTop, icon, statusRow)
        }
        if (h > w * 1.15f) {
            // Tall: status row, dial right under it, then three circles in a row; spare height stays at the bottom.
            val cw = (w - 4 * pad) / 3f
            val rowH = min(h * 0.26f, cw)
            val avail = h - pad - statusBottom - gap
            val r = fit(w - 2 * pad, avail - pad - rowH)
            val top = statusBottom + gap
            val rowCy = top + r * (1f + BELOW) + pad + rowH / 2f
            val fields = (0..2).map { RideField(pad + it * (cw + pad) + cw / 2f, rowCy, min(cw, rowH)) }
            return RideLayout(w / 2f, top + r, r, fields, pad, statusTop, icon, statusRow)
        }
        // Wide: status row + dial on the left, column of fields on the right.
        val colW = min(w * 0.36f, h * 0.9f)
        val arcW = w - colW - 3 * pad
        val avail = h - pad - statusBottom - gap
        val r = fit(arcW, avail)
        val cx = pad + arcW / 2f
        val cy = statusBottom + gap + r + (avail - r * (1f + BELOW)) / 2f
        val colCx = w - pad - colW / 2f
        val d3 = min(colW, (h - 2 * pad - 2 * gap) / 3f)
        val fields = if (d3 >= MIN_STACKED) {
            val y0 = (h - (3 * d3 + 2 * gap)) / 2f + d3 / 2f
            (0..2).map { RideField(colCx, y0 + it * (d3 + gap), d3) }
        } else {
            val lineH = (h - 2 * pad) * 0.16f
            val d = min(colW, (h - 2 * pad - lineH - 2 * gap) / 2f)
            listOf(
                RideField(colCx, pad + d / 2f, d),
                RideField(colCx, h / 2f, 0f, RectF(w - pad - colW, h / 2f - lineH / 2f, w - pad, h / 2f + lineH / 2f)),
                RideField(colCx, h - pad - d / 2f, d),
            )
        }
        return RideLayout(cx, cy, r, fields, pad, statusTop, icon, statusRow)
    }

    /** Point at [angleDeg] on a circle of radius [r] around the dial centre. */
    fun pointAt(l: RideLayout, angleDeg: Float, r: Float): Pair<Float, Float> {
        val rad = Math.toRadians(angleDeg.toDouble())
        return (l.cx + r * cos(rad).toFloat()) to (l.cy + r * sin(rad).toFloat())
    }

    /** Major tick step and minor tick step for a scale: every 10 (minor 2) on 0-60, 10/5 on 0-90, 5/1 on 0-30 and 0-45. */
    fun tickSteps(scaleMax: Int): Pair<Int, Int> = if (scaleMax >= 60) 10 to (if (scaleMax >= 90) 5 else 2) else 5 to 1

    /** Status text size as a multiple of the icon height. */
    private const val STATUS_TEXT = 1.25f

    /** Radii (as a share of the dial radius) used by the tests to find ticks and the pointer. */
    const val TICK_OUTER = 0.985f
    const val MAJOR_INNER = 0.84f
    const val MINOR_INNER = 0.91f

    fun render(model: RideModel, width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.RGB_565): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, config)
        val canvas = Canvas(bmp)
        val t = model.theme
        canvas.drawColor(t.background)
        val l = layout(width, height, model.statusLeft != null || model.statusRight != null)
        drawDial(canvas, l, model.position, model.scaleMaxKmh, t)
        drawCentre(canvas, l, model, t)
        model.statusLeft?.let { drawStatus(canvas, l, it, left = true, t) }
        model.statusRight?.let { drawStatus(canvas, l, it, left = false, t) }
        l.fields.zip(model.slots).forEach { (f, slot) ->
            if (f.line != null) drawLine(canvas, f.line, slot, t) else drawCircleField(canvas, f, slot, t)
        }
        return bmp
    }

    private fun text(size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            textAlign = align
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

    /** Shrinks [p] so [s] fits in [maxW]. */
    private fun fit(p: Paint, s: String, maxW: Float): Paint {
        val m = p.measureText(s)
        if (m > maxW && m > 0f) p.textSize *= maxW / m
        return p
    }

    private fun drawDial(c: Canvas, l: RideLayout, pos: Float?, max: Int, t: RideTheme) {
        val r = l.radius
        c.drawArc(RectF(l.cx - r, l.cy - r, l.cx + r, l.cy + r), START_DEG, SWEEP_DEG, false,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = maxOf(1.5f, r * 0.018f); color = t.ring })
        val (major, minor) = tickSteps(max)
        val num = text(r * 0.13f, t.textSecondary)
        for (v in 0..max step minor) {
            val f = v.toFloat() / max
            val deg = START_DEG + SWEEP_DEG * f
            val isMajor = v % major == 0
            val lit = pos != null && f <= pos + 1e-4f
            val (x1, y1) = pointAt(l, deg, r * (if (isMajor) MAJOR_INNER else MINOR_INNER))
            val (x2, y2) = pointAt(l, deg, r * TICK_OUTER)
            c.drawLine(x1, y1, x2, y2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (lit) t.tickLit else t.tickDim
                strokeWidth = if (isMajor) maxOf(2f, r * 0.03f) else maxOf(1f, r * 0.013f)
                strokeCap = Paint.Cap.BUTT
            })
            if (isMajor) {
                val (nx, ny) = pointAt(l, deg, r * 0.70f)
                num.color = if (lit) t.text else t.textSecondary
                c.drawText(v.toString(), nx, ny + num.textSize * 0.36f, num)
            }
        }
        if (pos != null) drawNeedle(c, l, angleFor(pos), t)
    }

    /** Red tapered pointer across the ticks, with a dark outline so it stands out on lit ticks. */
    private fun drawNeedle(c: Canvas, l: RideLayout, deg: Float, t: RideTheme) {
        val r = l.radius
        val (tx, ty) = pointAt(l, deg, r * 1.03f)
        val (bx, by) = pointAt(l, deg, r * 0.80f)
        val half = r * 0.035f
        val rad = Math.toRadians(deg.toDouble())
        val nx = -sin(rad).toFloat() * half
        val ny = cos(rad).toFloat() * half
        val path = Path().apply {
            moveTo(tx, ty); lineTo(bx + nx, by + ny); lineTo(bx - nx, by - ny); close()
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = maxOf(2f, r * 0.025f); color = t.background
            strokeJoin = Paint.Join.ROUND
        })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.needle })
    }

    /** Free half width inside the numbers at height [dy] from the centre. */
    private fun free(l: RideLayout, dy: Float): Float {
        val rr = l.radius * 0.60f
        return sqrt(maxOf(0f, rr * rr - dy * dy))
    }

    private fun drawCentre(c: Canvas, l: RideLayout, m: RideModel, t: RideTheme) {
        val r = l.radius
        c.drawText(m.unit, l.cx, l.cy - r * 0.27f, fit(text(r * 0.11f, t.textSecondary), m.unit, free(l, r * 0.30f) * 2f))
        val speed = m.speedText ?: "--"
        val col = if (m.speedText != null) t.text else t.textSecondary
        c.drawText(speed, l.cx, l.cy + r * 0.17f, fit(text(r * 0.44f, col), speed, free(l, r * 0.05f) * 1.85f))
        // Line under the speed (distance or average speed, chosen in Settings) where the gear used to be.
        // Average speed gets a small grey "AVG" label above its value, like GEAR in the status row.
        m.underSpeedLabel?.let { label ->
            c.drawText(label, l.cx, l.cy + r * 0.33f, fit(text(r * 0.10f, t.textSecondary), label, r * 0.6f))
        }
        c.drawText(m.underSpeedText, l.cx, l.cy + r * 0.50f, fit(text(r * 0.16f, t.text), m.underSpeedText, r * 0.78f))
    }

    /** One status spot: the battery block, or a value with its label under it. Left spots start at the dial's left edge, right spots end at its right edge. */
    private fun drawStatus(c: Canvas, l: RideLayout, item: StatusItem, left: Boolean, t: RideTheme) {
        when (item) {
            is StatusItem.Battery -> drawShiftBattery(c, l, item.battery, left, t)
            is StatusItem.Value -> drawValue(c, l, item, left, t)
        }
    }

    /** Where a status block of width [blockW] is centred. */
    private fun statusCentre(l: RideLayout, blockW: Float, left: Boolean) =
        if (left) maxOf(l.padding, l.dialLeft) + blockW / 2f else l.dialRight - blockW / 2f

    /**
     * Battery block: icon filled to the shifting battery, and "Di2 70%" / "AXS FULL" under it (grey "SHIFT --"
     * when unknown). Icon and text are centred on each other; long text ("AXS CRITICAL") shrinks to fit.
     */
    private fun drawShiftBattery(c: Canvas, l: RideLayout, b: ShiftBattery?, left: Boolean, t: RideTheme) {
        val h = l.statusIcon
        val w = h * 1.9f
        val nub = h * 0.15f
        val name = "${b?.system ?: "SHIFT"} "
        val value = b?.text ?: "--"
        val levelColor = b?.level?.color?.let { t.color(it) }
        val nameP = text(h * STATUS_TEXT, t.textSecondary, Paint.Align.LEFT)
        val valueP = text(h * STATUS_TEXT, levelColor ?: t.textSecondary, Paint.Align.LEFT)
        val maxTextW = l.radius * 0.95f
        val rawW = nameP.measureText(name) + valueP.measureText(value)
        if (rawW > maxTextW) {
            nameP.textSize *= maxTextW / rawW
            valueP.textSize *= maxTextW / rawW
        }
        val textW = nameP.measureText(name) + valueP.measureText(value)
        val centre = statusCentre(l, maxOf(w + nub, textW), left)

        val x = centre - (w + nub) / 2f
        val y = l.statusTop
        val stroke = maxOf(1.5f, h * 0.12f)
        val outline = if (b == null) t.tickDim else t.textSecondary
        c.drawRect(RectF(x, y, x + w, y + h), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; color = outline
        })
        c.drawRect(x + w, y + h * 0.3f, x + w + nub, y + h * 0.7f, Paint().apply { color = outline })
        if (b != null && levelColor != null) {
            val inset = stroke * 1.5f
            c.drawRect(x + inset, y + inset, x + inset + (w - 2 * inset) * b.fill, y + h - inset, Paint().apply { color = levelColor })
        }
        val base = y + h + h * 1.4f
        val tx = centre - textW / 2f
        c.drawText(name, tx, base, nameP)
        c.drawText(value, tx + nameP.measureText(name), base, valueP)
    }

    /** Value block, level with the battery: "52 / 14" with "GEAR" centred under it. */
    private fun drawValue(c: Canvas, l: RideLayout, item: StatusItem.Value, left: Boolean, t: RideTheme) {
        val h = l.statusIcon
        val valueP = fit(text(h * 1.6f, if (item.known) t.text else t.textSecondary), item.text, l.radius * 0.8f)
        val labelP = fit(text(h * 1.15f, t.textSecondary), item.label, l.radius * 0.8f)
        val blockW = maxOf(valueP.measureText(item.text), labelP.measureText(item.label))
        val centre = statusCentre(l, blockW, left)
        c.drawText(item.text, centre, l.statusTop + h * 1.25f, valueP)
        c.drawText(item.label, centre, l.statusTop + h * 1.25f + h * 1.4f, labelP)
    }

    private fun drawCircleField(c: Canvas, f: RideField, s: RideSlot, t: RideTheme) {
        val d = f.diameter
        val border = maxOf(2.5f, d * 0.045f)
        c.drawCircle(f.cx, f.cy, d / 2f - border / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = border; color = t.color(s.color)
        })
        c.drawText(s.label, f.cx, f.cy - d * 0.17f, fit(text(d * 0.13f, t.textSecondary), s.label, d * 0.58f))
        val v = s.value ?: "--"
        val vp = fit(text(d * 0.33f, if (s.value != null) t.text else t.textSecondary), v, d * 0.72f)
        c.drawText(v, f.cx, f.cy + d * 0.10f + vp.textSize * 0.36f, vp)
    }

    /** Middle field in short wide fields: "LABEL: value" on one line, value in the field color if it has one. */
    private fun drawLine(c: Canvas, box: RectF, slot: RideSlot, t: RideTheme) {
        val label = "${slot.label}:"
        val v = slot.value ?: "--"
        val valueColor = when {
            slot.value == null -> t.textSecondary
            slot.color == RideGauge.NEUTRAL -> t.text
            else -> t.color(slot.color)
        }
        val valueP = text(box.height() * 0.72f, valueColor, Paint.Align.RIGHT)
        val labelP = text(valueP.textSize * 0.75f, t.textSecondary, Paint.Align.LEFT)
        val needed = labelP.measureText(label) + valueP.measureText(v) + valueP.textSize * 0.4f
        val avail = box.width() * 0.9f
        if (needed > avail) {
            valueP.textSize *= avail / needed
            labelP.textSize *= avail / needed
        }
        val base = box.centerY() + valueP.textSize * 0.36f
        c.drawText(label, box.left + box.width() * 0.05f, base, labelP)
        c.drawText(v, box.right - box.width() * 0.05f, base, valueP)
    }
}
