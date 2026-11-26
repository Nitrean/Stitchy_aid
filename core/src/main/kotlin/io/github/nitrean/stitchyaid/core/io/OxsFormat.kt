package io.github.nitrean.stitchyaid.core.io

import io.github.nitrean.stitchyaid.core.color.ColorMath
import io.github.nitrean.stitchyaid.core.model.Backstitch
import io.github.nitrean.stitchyaid.core.model.Knot
import io.github.nitrean.stitchyaid.core.model.KnotKind
import io.github.nitrean.stitchyaid.core.model.PaletteEntry
import io.github.nitrean.stitchyaid.core.model.PartialKind
import io.github.nitrean.stitchyaid.core.model.PartialStitch
import io.github.nitrean.stitchyaid.core.model.Pattern
import io.github.nitrean.stitchyaid.core.model.Symbols
import io.github.nitrean.stitchyaid.core.model.ThreadColor
import io.github.nitrean.stitchyaid.core.threads.ThreadCatalog
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Reads and writes the Open Cross Stitch (.oxs) XML format, the open format
 * shared by KG-Chart, Ursa Software, Pattern Keeper and others.
 *
 * Palette item 0 is the fabric ("cloth"); stitches refer to palette items by index.
 */
object OxsFormat {

    fun read(bytes: ByteArray, fallbackTitle: String = "My pattern"): Pattern {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Never fetch anything from the internet while reading a file.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val root = doc.documentElement
        if (!root.tagName.equals("chart", ignoreCase = true)) throw PatternReadException("This file is not a cross stitch chart.")

        val props = root.children("properties").firstOrNull()
        val width = props?.intAttr("chartwidth") ?: 0
        val height = props?.intAttr("chartheight") ?: 0

        // Palette: keep the OXS index so stitches can be mapped.
        val oxsPalette = root.children("palette").flatMap { it.children("palette_item") }
        var fabricColor = 0xFFFFFF
        val oxsToOurs = HashMap<Int, Int>()
        val palette = mutableListOf<PaletteEntry>()
        val usedSymbols = mutableSetOf<String>()
        oxsPalette.forEachIndexed { position, item ->
            val oxsIndex = item.intAttr("index") ?: position
            val number = item.attr("number").orEmpty()
            val rgb = ColorMath.parseHex(item.attr("color").orEmpty()) ?: 0x888888
            if (oxsIndex == 0 || number.equals("cloth", true) || item.attr("name").equals("cloth", true)) {
                fabricColor = rgb
                return@forEachIndexed
            }
            val thread = parseThread(number, item.attr("name").orEmpty(), rgb)
            val symbol = parseSymbol(item.attr("symbol"))?.takeIf { it !in usedSymbols }
            oxsToOurs[oxsIndex] = palette.size
            palette += PaletteEntry(thread, symbol ?: "", item.intAttr("strands") ?: 2)
            symbol?.let { usedSymbols += it }
        }
        // Give a clear symbol to any colour that had none.
        val missing = palette.count { it.symbol.isBlank() }
        if (missing > 0) {
            val fresh = Symbols.pick(missing, usedSymbols).iterator()
            for (i in palette.indices) if (palette[i].symbol.isBlank()) palette[i] = palette[i].copy(symbol = fresh.next())
        }

        val full = root.children("fullstitches").flatMap { it.children("stitch") }
        val partNodes = root.children("partstitches").flatMap { it.children("partstitch") }
        val backNodes = root.children("backstitches").flatMap { it.children("backstitch") }
        val ornaments = root.children("ornaments_inc_knots_and_beads").flatMap { it.children("object") }

        // Some files leave out the size; work it out from the stitches.
        val w = if (width > 0) width else (full.maxOfOrNull { it.intAttr("x") ?: 0 } ?: 0) + 1
        val h = if (height > 0) height else (full.maxOfOrNull { it.intAttr("y") ?: 0 } ?: 0) + 1
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_CELLS) throw PatternReadException("This chart's size could not be read.")

        val cells = IntArray(w * h) { Pattern.EMPTY }
        for (s in full) {
            val x = s.intAttr("x") ?: continue
            val y = s.intAttr("y") ?: continue
            val c = oxsToOurs[s.intAttr("palindex") ?: continue] ?: continue
            if (x in 0 until w && y in 0 until h) cells[y * w + x] = c
        }

        val partials = mutableListOf<PartialStitch>()
        for (p in partNodes) {
            val x = p.intAttr("x") ?: continue
            val y = p.intAttr("y") ?: continue
            val c1 = oxsToOurs[p.intAttr("palindex1") ?: -1]
            val c2 = oxsToOurs[p.intAttr("palindex2") ?: -1]
            // direction: 1 = top-left/bottom-right split, 2 = top-right/bottom-left, 3 = "\" half, 4 = "/" half
            when (p.intAttr("direction")) {
                1 -> { c1?.let { partials += PartialStitch(x, y, PartialKind.QUARTER_TOP_LEFT, it) }; c2?.let { partials += PartialStitch(x, y, PartialKind.QUARTER_BOTTOM_RIGHT, it) } }
                2 -> { c1?.let { partials += PartialStitch(x, y, PartialKind.QUARTER_TOP_RIGHT, it) }; c2?.let { partials += PartialStitch(x, y, PartialKind.QUARTER_BOTTOM_LEFT, it) } }
                3 -> (c1 ?: c2)?.let { partials += PartialStitch(x, y, PartialKind.HALF_BACK, it) }
                4 -> (c1 ?: c2)?.let { partials += PartialStitch(x, y, PartialKind.HALF_FORWARD, it) }
                else -> (c1 ?: c2)?.let { partials += PartialStitch(x, y, PartialKind.HALF_FORWARD, it) }
            }
        }

        val backstitches = backNodes.mapNotNull { b ->
            val c = oxsToOurs[b.intAttr("palindex") ?: return@mapNotNull null] ?: return@mapNotNull null
            Backstitch(
                b.floatAttr("x1") ?: return@mapNotNull null, b.floatAttr("y1") ?: return@mapNotNull null,
                b.floatAttr("x2") ?: return@mapNotNull null, b.floatAttr("y2") ?: return@mapNotNull null,
                c, b.intAttr("strands") ?: 1,
            )
        }

        val knots = ornaments.mapNotNull { o ->
            val c = oxsToOurs[o.intAttr("palindex") ?: return@mapNotNull null] ?: return@mapNotNull null
            val type = o.attr("objecttype").orEmpty().lowercase()
            val kind = if (type.contains("bead")) KnotKind.BEAD else KnotKind.FRENCH_KNOT
            Knot(o.floatAttr("x1") ?: return@mapNotNull null, o.floatAttr("y1") ?: return@mapNotNull null, c, kind)
        }

        if (palette.isEmpty() || (cells.none { it >= 0 } && backstitches.isEmpty() && partials.isEmpty())) {
            throw PatternReadException("This chart has no stitches in it.")
        }

        return Pattern(
            title = props?.attr("charttitle")?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            width = w,
            height = h,
            palette = palette,
            cells = cells,
            partials = partials,
            backstitches = backstitches,
            knots = knots,
            designer = props?.attr("author").orEmpty(),
            fabricCount = props?.intAttr("stitchesperinch")?.takeIf { it in 6..40 } ?: 14,
            fabricColor = fabricColor,
            notes = props?.attr("instructions").orEmpty(),
        )
    }

    fun write(pattern: Pattern): ByteArray {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val chart = doc.createElement("chart").also { doc.appendChild(it) }
        fun el(parent: Element, name: String, vararg attrs: Pair<String, Any>): Element =
            doc.createElement(name).also { e -> attrs.forEach { (k, v) -> e.setAttribute(k, v.toString()) }; parent.appendChild(e) }

        el(chart, "format", "comments01" to "Designed to allow interchange of basic pattern data between any cross stitch style software",
            "comments02" to "the 'properties' section establishes size, copyright, authorship and software used")
        el(chart, "properties",
            "oxsversion" to "1.0", "software" to "Stitchy Aid", "software_version" to "1",
            "chartheight" to pattern.height, "chartwidth" to pattern.width,
            "charttitle" to pattern.title, "author" to pattern.designer, "copyright" to "",
            "instructions" to pattern.notes, "stitchesperinch" to pattern.fabricCount, "stitchesperinch_y" to pattern.fabricCount,
            "palettecount" to pattern.palette.size + 1)
        val pal = el(chart, "palette")
        el(pal, "palette_item", "index" to 0, "number" to "cloth", "name" to "cloth", "color" to ColorMath.toHex(pattern.fabricColor),
            "printcolor" to ColorMath.toHex(pattern.fabricColor), "blendcolor" to "nil", "comments" to "aida", "strands" to 2, "symbol" to "0")
        pattern.palette.forEachIndexed { i, p ->
            el(pal, "palette_item", "index" to i + 1, "number" to "${p.thread.brand} ${p.thread.code}".trim(), "name" to p.thread.name,
                "color" to ColorMath.toHex(p.thread.rgb), "printcolor" to ColorMath.toHex(p.thread.rgb), "blendcolor" to "nil",
                "strands" to p.strands, "symbol" to p.symbol)
        }
        val full = el(chart, "fullstitches")
        for (y in 0 until pattern.height) for (x in 0 until pattern.width) {
            val c = pattern.colorAt(x, y)
            if (c >= 0) el(full, "stitch", "x" to x, "y" to y, "palindex" to c + 1)
        }
        val parts = el(chart, "partstitches")
        for (p in pattern.partials) {
            val (dir, first) = when (p.kind) {
                PartialKind.QUARTER_TOP_LEFT -> 1 to true
                PartialKind.QUARTER_BOTTOM_RIGHT -> 1 to false
                PartialKind.QUARTER_TOP_RIGHT -> 2 to true
                PartialKind.QUARTER_BOTTOM_LEFT -> 2 to false
                PartialKind.HALF_BACK -> 3 to true
                PartialKind.HALF_FORWARD -> 4 to true
            }
            el(parts, "partstitch", "x" to p.x, "y" to p.y,
                "palindex1" to if (first) p.color + 1 else 0, "palindex2" to if (first) 0 else p.color + 1, "direction" to dir)
        }
        val backs = el(chart, "backstitches")
        pattern.backstitches.forEachIndexed { i, b ->
            el(backs, "backstitch", "x1" to fmt(b.x1), "y1" to fmt(b.y1), "x2" to fmt(b.x2), "y2" to fmt(b.y2),
                "palindex" to b.color + 1, "objecttype" to "backstitch", "sequence" to i, "strands" to b.strands)
        }
        val orn = el(chart, "ornaments_inc_knots_and_beads")
        for (k in pattern.knots) {
            el(orn, "object", "x1" to fmt(k.x), "y1" to fmt(k.y), "palindex" to k.color + 1,
                "objecttype" to if (k.kind == KnotKind.BEAD) "bead" else "knot")
        }
        el(chart, "commentboxes")

        val out = StringWriter()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }.transform(DOMSource(doc), StreamResult(out))
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    private fun fmt(f: Float): String = if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString()

    /** "DMC    310", "DMC-310", "Anchor 403" or just "310". */
    private fun parseThread(number: String, name: String, rgb: Int): ThreadColor {
        val cleaned = number.trim()
        val match = Regex("^([A-Za-z][A-Za-z .&'-]*?)[\\s:_-]*([A-Za-z]?\\d+[A-Za-z]?|[A-Za-z]+)$").find(cleaned)
        val (brand, code) = when {
            match != null && match.groupValues[1].isNotBlank() -> match.groupValues[1].trim() to match.groupValues[2]
            cleaned.isNotEmpty() -> "DMC" to cleaned
            else -> "" to name.ifBlank { ColorMath.toHex(rgb) }
        }
        val known = if (brand.equals("DMC", true)) ThreadCatalog.DMC.find(code) else null
        return ThreadColor(brand.let { if (it.equals("dmc", true)) "DMC" else it }, code, name.ifBlank { known?.name ?: code }, rgb)
    }

    /** OXS symbols are a character, or a number that some programs use as a font code. */
    private fun parseSymbol(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "0") return null
        return if (raw.length == 1 || raw.toIntOrNull() == null) raw.take(2) else null
    }

    private fun Element.children(tag: String): List<Element> {
        val out = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n is Element && n.tagName.equals(tag, ignoreCase = true)) out += n
        }
        return out
    }

    private fun Element.attr(name: String): String? {
        val attrs = attributes
        for (i in 0 until attrs.length) {
            val a = attrs.item(i)
            if (a.nodeName.equals(name, ignoreCase = true)) return a.nodeValue
        }
        return null
    }

    private fun Element.intAttr(name: String): Int? = attr(name)?.trim()?.let { it.toIntOrNull() ?: it.toFloatOrNull()?.toInt() }
    private fun Element.floatAttr(name: String): Float? = attr(name)?.trim()?.toFloatOrNull()

    private const val MAX_CELLS = 4_000_000L
}

/** A file could not be read. [message] is plain language, safe to show to people. */
class PatternReadException(message: String, cause: Throwable? = null) : Exception(message, cause)
