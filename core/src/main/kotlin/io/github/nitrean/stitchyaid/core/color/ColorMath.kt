package io.github.nitrean.stitchyaid.core.color

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A colour in CIELAB space, which matches how people perceive colour differences. */
data class Lab(val l: Double, val a: Double, val b: Double)

object ColorMath {
    fun red(rgb: Int) = (rgb shr 16) and 0xFF
    fun green(rgb: Int) = (rgb shr 8) and 0xFF
    fun blue(rgb: Int) = rgb and 0xFF
    fun rgb(r: Int, g: Int, b: Int) = (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun linear(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    fun toLab(rgb: Int): Lab {
        val r = linear(red(rgb)); val g = linear(green(rgb)); val b = linear(blue(rgb))
        val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
        val y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750)
        val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883
        fun f(t: Double) = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0
        val fx = f(x); val fy = f(y); val fz = f(z)
        return Lab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    /** Relative luminance 0..1, used to pick black or white symbols. */
    fun luminance(rgb: Int): Double =
        0.2126 * linear(red(rgb)) + 0.7152 * linear(green(rgb)) + 0.0722 * linear(blue(rgb))

    /** CIEDE2000 colour difference. Below about 2 most people see no difference. */
    fun deltaE2000(c1: Lab, c2: Lab): Double {
        val avgL = (c1.l + c2.l) / 2
        val cA = sqrt(c1.a * c1.a + c1.b * c1.b)
        val cB = sqrt(c2.a * c2.a + c2.b * c2.b)
        val avgC = (cA + cB) / 2
        val g = 0.5 * (1 - sqrt(avgC.pow(7) / (avgC.pow(7) + 25.0.pow(7))))
        val a1 = c1.a * (1 + g); val a2 = c2.a * (1 + g)
        val c1p = sqrt(a1 * a1 + c1.b * c1.b); val c2p = sqrt(a2 * a2 + c2.b * c2.b)
        val avgCp = (c1p + c2p) / 2
        fun hue(b: Double, a: Double): Double { val h = Math.toDegrees(atan2(b, a)); return if (h < 0) h + 360 else h }
        val h1 = hue(c1.b, a1); val h2 = hue(c2.b, a2)
        val avgH = if (abs(h1 - h2) > 180) (h1 + h2 + 360) / 2 else (h1 + h2) / 2
        val t = 1 - 0.17 * cos(Math.toRadians(avgH - 30)) + 0.24 * cos(Math.toRadians(2 * avgH)) +
            0.32 * cos(Math.toRadians(3 * avgH + 6)) - 0.20 * cos(Math.toRadians(4 * avgH - 63))
        var dh = h2 - h1
        if (abs(dh) > 180) dh += if (h2 <= h1) 360 else -360
        val dL = c2.l - c1.l
        val dC = c2p - c1p
        val dH = 2 * sqrt(c1p * c2p) * sin(Math.toRadians(dh / 2))
        val sl = 1 + 0.015 * (avgL - 50).pow(2) / sqrt(20 + (avgL - 50).pow(2))
        val sc = 1 + 0.045 * avgCp
        val sh = 1 + 0.015 * avgCp * t
        val dTheta = 30 * exp(-((avgH - 275) / 25).pow(2))
        val rc = 2 * sqrt(avgCp.pow(7) / (avgCp.pow(7) + 25.0.pow(7)))
        val rt = -rc * sin(Math.toRadians(2 * dTheta))
        return sqrt((dL / sl).pow(2) + (dC / sc).pow(2) + (dH / sh).pow(2) + rt * (dC / sc) * (dH / sh))
    }

    fun deltaE(rgb1: Int, rgb2: Int): Double = deltaE2000(toLab(rgb1), toLab(rgb2))

    /** Mix [rgb] toward white by [amount] (0 = unchanged, 1 = white). */
    fun tint(rgb: Int, amount: Float): Int {
        fun m(c: Int) = (c + (255 - c) * amount).toInt()
        return rgb(m(red(rgb)), m(green(rgb)), m(blue(rgb)))
    }

    fun parseHex(hex: String): Int? {
        val h = hex.trim().removePrefix("#")
        if (h.length != 6) return null
        return h.toIntOrNull(16)
    }

    fun toHex(rgb: Int): String = "%06X".format(rgb and 0xFFFFFF)
}
