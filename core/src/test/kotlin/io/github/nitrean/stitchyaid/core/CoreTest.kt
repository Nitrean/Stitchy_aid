package io.github.nitrean.stitchyaid.core

import io.github.nitrean.stitchyaid.core.color.ColorMath
import io.github.nitrean.stitchyaid.core.io.FileKind
import io.github.nitrean.stitchyaid.core.io.FormatDetector
import io.github.nitrean.stitchyaid.core.io.ImportResult
import io.github.nitrean.stitchyaid.core.io.OxsFormat
import io.github.nitrean.stitchyaid.core.io.PatternImporter
import io.github.nitrean.stitchyaid.core.io.ProjectCodec
import io.github.nitrean.stitchyaid.core.progress.CellSet
import io.github.nitrean.stitchyaid.core.progress.ProgressTracker
import io.github.nitrean.stitchyaid.core.threads.SkeinCalculator
import io.github.nitrean.stitchyaid.core.threads.ThreadCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CoreTest {
    private val sampleOxs = """
        <?xml version="1.0" encoding="UTF-8"?>
        <chart>
          <properties oxsversion="1.0" chartwidth="4" chartheight="3" charttitle="Tiny heart" author="Test" stitchesperinch="16"/>
          <palette>
            <palette_item index="0" number="cloth" name="cloth" color="FFFFFF"/>
            <palette_item index="1" number="DMC    321" name="Red" color="C72B3B" symbol="x" strands="2"/>
            <palette_item index="2" number="DMC 310" name="Black" color="000000" symbol=""/>
          </palette>
          <fullstitches>
            <stitch x="0" y="0" palindex="1"/>
            <stitch x="1" y="0" palindex="1"/>
            <stitch x="3" y="2" palindex="2"/>
          </fullstitches>
          <partstitches>
            <partstitch x="2" y="1" palindex1="1" palindex2="0" direction="4"/>
          </partstitches>
          <backstitches>
            <backstitch x1="0" y1="1" x2="2.5" y2="1" palindex="2" objecttype="backstitch"/>
          </backstitches>
          <ornaments_inc_knots_and_beads>
            <object x1="1.5" y1="1.5" palindex="2" objecttype="knot"/>
          </ornaments_inc_knots_and_beads>
        </chart>
    """.trimIndent().toByteArray()

    @Test fun detectsFormatsWithoutAskingTheUser() {
        assertEquals(FileKind.OXS, FormatDetector.detect(sampleOxs, "whatever.dat"))
        assertEquals(FileKind.PDF, FormatDetector.detect("%PDF-1.7 ...".toByteArray(), null))
        assertEquals(FileKind.IMAGE, FormatDetector.detect(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0), "x"))
        assertEquals(FileKind.PATTERN_MAKER, FormatDetector.detect(byteArrayOf(1, 0, 2, 0), "Roses.XSD"))
        assertEquals(FileKind.PCSTITCH, FormatDetector.detect(byteArrayOf(1, 0, 2, 0), "roses.pat"))
        assertEquals(FileKind.UNKNOWN, FormatDetector.detect(byteArrayOf(1, 0, 2, 0), "roses.doc"))
    }

    @Test fun readsOxs() {
        val p = OxsFormat.read(sampleOxs)
        assertEquals("Tiny heart", p.title)
        assertEquals(4, p.width); assertEquals(3, p.height)
        assertEquals(2, p.palette.size)
        assertEquals("DMC", p.palette[0].thread.brand)
        assertEquals("321", p.palette[0].thread.code)
        assertEquals("310", p.palette[1].thread.code)
        assertTrue(p.palette[1].symbol.isNotBlank(), "missing symbols are filled in")
        assertEquals(0, p.colorAt(0, 0)); assertEquals(1, p.colorAt(3, 2)); assertEquals(-1, p.colorAt(2, 2))
        assertEquals(3, p.totalStitches)
        assertEquals(1, p.partials.size)
        assertEquals(2.5f, p.backstitches.single().x2)
        assertEquals(1, p.knots.size)
        assertEquals(16, p.fabricCount)
    }

    @Test fun oxsRoundTrip() {
        val p = OxsFormat.read(sampleOxs)
        val again = OxsFormat.read(OxsFormat.write(p))
        assertEquals(p.width, again.width)
        assertTrue(p.cells.contentEquals(again.cells))
        assertEquals(p.palette.map { it.thread.code }, again.palette.map { it.thread.code })
        assertEquals(p.backstitches, again.backstitches)
        assertEquals(p.partials, again.partials)
        assertEquals(p.knots, again.knots)
    }

    @Test fun rejectsXmlThatIsNotAChart() {
        val r = PatternImporter.import("<chart></chart>".toByteArray(), "x.oxs")
        assertIs<ImportResult.Failed>(r)
    }

    @Test fun pdfIsRecognisedButNotYetRead() {
        val r = PatternImporter.import("%PDF-1.4".toByteArray(), "pattern.pdf")
        assertIs<ImportResult.NotYetSupported>(r)
        assertEquals(FileKind.PDF, r.kind)
    }

    @Test fun progressWithUndo() {
        val project = (PatternImporter.import(sampleOxs, "heart.oxs") as ImportResult.Success).project
        val t = ProgressTracker(project)
        assertEquals(3, t.totalStitches)
        assertTrue(t.toggle(0))
        assertTrue(t.mark(intArrayOf(1, 11, 5), true)) // 5 is empty and ignored
        assertEquals(3, t.doneStitches)
        assertEquals(2, t.colorProgress(0).done)
        assertEquals(1, t.lastColor)
        assertTrue(t.undo())
        assertEquals(1, t.doneStitches)
        assertTrue(t.redo())
        assertEquals(3, t.doneStitches)
        assertFalse(t.mark(intArrayOf(0), true), "already done: nothing to change")

        val saved = t.writeTo(project, 99)
        val reloaded = ProgressTracker(ProjectCodec.decode(ProjectCodec.encode(saved)))
        assertEquals(3, reloaded.doneStitches)
        assertTrue(reloaded.isDone(11))
        assertEquals(saved.recentCells, reloaded.recentCells)
    }

    @Test fun cellSetGrowsAndCounts() {
        val s = CellSet()
        s.set(3, true); s.set(700, true); s.set(64, true)
        assertEquals(3, s.count())
        assertTrue(700 in s)
        s.set(700, false)
        assertEquals(listOf(3, 64), buildList { s.forEach { add(it) } })
    }

    @Test fun dmcCatalog() {
        val dmc = ThreadCatalog.DMC
        assertTrue(dmc.threads.size > 400)
        assertEquals("Black", dmc.find("310")?.name)
        assertEquals("310", dmc.nearest(0x050505).code)
        assertEquals(0.0, ColorMath.deltaE(0x336699, 0x336699), 1e-9)
    }

    @Test fun skeins() {
        // A skein covers roughly 1,800 to 2,000 stitches on 14 count with 2 strands.
        assertEquals(1, SkeinCalculator.skeins(1000, 14, 2))
        assertEquals(0, SkeinCalculator.skeins(0, 14, 2))
        assertTrue(SkeinCalculator.skeins(20000, 14, 2) in 8..12)
    }
}
