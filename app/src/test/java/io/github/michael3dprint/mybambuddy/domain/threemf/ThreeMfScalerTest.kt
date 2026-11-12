package io.github.michael3dprint.mybambuddy.domain.threemf

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThreeMfScalerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val cube = listOf(
        Triple(-5.0, -5.0, -5.0),
        Triple(5.0, -5.0, -5.0),
        Triple(5.0, 5.0, -5.0),
        Triple(-5.0, 5.0, -5.0),
        Triple(-5.0, -5.0, 5.0),
        Triple(5.0, -5.0, 5.0),
        Triple(5.0, 5.0, 5.0),
        Triple(-5.0, 5.0, 5.0),
    )

    private fun vertices(points: List<Triple<Double, Double, Double>>) =
        points.joinToString("\n") { "<vertex x=\"${it.first}\" y=\"${it.second}\" z=\"${it.third}\"/>" }

    private fun model(resources: String, items: String) = """<?xml version="1.0" encoding="UTF-8"?>
<model unit="millimeter" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02" xmlns:p="http://schemas.microsoft.com/3dmanufacturing/production/2015/06">
 <metadata name="Title">Test</metadata>
 <resources>
$resources
 </resources>
 <build p:UUID="x">
$items
 </build>
</model>"""

    private fun zip(entries: Map<String, String>): File {
        val f = tmp.newFile("in${System.nanoTime()}.3mf")
        ZipOutputStream(f.outputStream()).use { z ->
            for ((name, body) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray())
                z.closeEntry()
            }
        }
        return f
    }

    private fun out() = File(tmp.root, "out${System.nanoTime()}.3mf")

    private fun mainOf(file: File): String = ZipFile(file).use { z ->
        z.getInputStream(z.getEntry("3D/3dmodel.model")).readBytes().toString(Charsets.UTF_8)
    }

    private fun itemTransform(text: String, index: Int = 0): Matrix3x4 =
        Matrix3x4.parse(Regex("<item [^>]*transform=\"([^\"]+)\"").findAll(text).toList()[index].groupValues[1])!!

    /** The world extent of the cube-like points under [item] after the component chain [chain]. */
    private fun world(points: List<Triple<Double, Double, Double>>, item: Matrix3x4, chain: Matrix3x4 = Matrix3x4.IDENTITY): Bounds4 {
        val ps = points.map { item.apply(chain.apply(it.first, it.second, it.third)) }
        return Bounds4(ps.minOf { it.x }, ps.maxOf { it.x }, ps.minOf { it.y }, ps.maxOf { it.y }, ps.minOf { it.z }, ps.maxOf { it.z })
    }

    private data class Bounds4(val x0: Double, val x1: Double, val y0: Double, val y1: Double, val z0: Double, val z1: Double)

    private val eps = 1e-6

    @Test
    fun `a part with its mesh in the main file is scaled about its footprint centre and keeps its lowest point`() {
        val m = model(
            """<object id="1" type="model"><mesh><vertices>
${vertices(cube)}
</vertices></mesh></object>""",
            """<item objectid="1" transform="1 0 0 0 1 0 0 0 1 100 100 5" printable="1"/>""",
        )
        val result = ThreeMfScaler.scale(zip(mapOf("3D/3dmodel.model" to m)), out(), 2.0) as ScaleResult.Scaled
        val before = world(cube, Matrix3x4.parse("1 0 0 0 1 0 0 0 1 100 100 5")!!)
        val after = world(cube, itemTransform(mainOf(result.output)))
        assertEquals(0.0, before.z0, eps)
        assertEquals(before.z0, after.z0, eps)
        assertEquals((before.x0 + before.x1) / 2, (after.x0 + after.x1) / 2, eps)
        assertEquals((before.y0 + before.y1) / 2, (after.y0 + after.y1) / 2, eps)
        assertEquals((before.x1 - before.x0) * 2, after.x1 - after.x0, eps)
        assertEquals((before.z1 - before.z0) * 2, after.z1 - after.z0, eps)
    }

    @Test
    fun `a part built from a component file with a rotation is grounded and centred the same way`() {
        val rot = "0.6 0.8 0 -0.8 0.6 0 0 0 0.98 141.5 131.5 4.7"
        val main = model(
            """<object id="2" type="model"><components><component p:path="/3D/Objects/object_1.model" objectid="1" transform="1 0 0 0 1 0 0 0 1 0 0 0"/></components></object>""",
            """<item objectid="2" transform="$rot" printable="1"/>""",
        )
        val sub = """<model><resources><object id="1" type="model"><mesh><vertices>
${vertices(cube)}
</vertices></mesh></object></resources></model>"""
        val result = ThreeMfScaler.scale(
            zip(mapOf("3D/3dmodel.model" to main, "3D/Objects/object_1.model" to sub, "Metadata/other.txt" to "keep me")),
            out(),
            1.5,
        ) as ScaleResult.Scaled
        val before = world(cube, Matrix3x4.parse(rot)!!)
        val after = world(cube, itemTransform(mainOf(result.output)))
        assertEquals(before.z0, after.z0, eps)
        assertEquals((before.x0 + before.x1) / 2, (after.x0 + after.x1) / 2, eps)
        assertEquals((before.y0 + before.y1) / 2, (after.y0 + after.y1) / 2, eps)
        // Every linear number is multiplied by the factor and the rotation is kept.
        val t = itemTransform(mainOf(result.output))
        assertEquals(0.6 * 1.5, t.m[0], eps)
        assertEquals(0.98 * 1.5, t.m[8], eps)
        ZipFile(result.output).use { z ->
            assertEquals("keep me", z.getInputStream(z.getEntry("Metadata/other.txt")).readBytes().toString(Charsets.UTF_8))
            assertEquals(sub, z.getInputStream(z.getEntry("3D/Objects/object_1.model")).readBytes().toString(Charsets.UTF_8))
        }
    }

    @Test
    fun `scaling down works and several parts are each scaled about their own centre`() {
        val m = model(
            """<object id="1" type="model"><mesh><vertices>
${vertices(cube)}
</vertices></mesh></object>""",
            """<item objectid="1" transform="1 0 0 0 1 0 0 0 1 50 60 5" printable="1"/>
<item objectid="1" transform="1 0 0 0 1 0 0 0 1 150 160 5" printable="1"/>""",
        )
        val result = ThreeMfScaler.scale(zip(mapOf("3D/3dmodel.model" to m)), out(), 0.5) as ScaleResult.Scaled
        val text = mainOf(result.output)
        for ((i, c) in listOf(50.0 to 60.0, 150.0 to 160.0).withIndex()) {
            val b = world(cube, itemTransform(text, i))
            assertEquals(0.0, b.z0, eps)
            assertEquals(c.first, (b.x0 + b.x1) / 2, eps)
            assertEquals(c.second, (b.y0 + b.y1) / 2, eps)
            assertEquals(5.0, b.x1 - b.x0, eps)
        }
    }

    @Test
    fun `an item with no transform gets one`() {
        val m = model(
            """<object id="1" type="model"><mesh><vertices>
${vertices(cube)}
</vertices></mesh></object>""",
            """<item objectid="1" printable="1"/>""",
        )
        val result = ThreeMfScaler.scale(zip(mapOf("3D/3dmodel.model" to m)), out(), 2.0) as ScaleResult.Scaled
        val t = itemTransform(mainOf(result.output))
        assertEquals(2.0, t.m[0], eps)
        assertEquals(5.0, t.m[11], eps)
    }

    @Test
    fun `numbers are written in plain decimals`() {
        val text = Matrix3x4(doubleArrayOf(1e-7, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, -0.0, 12.5, -3.25, 0.000000001)).toText()
        assertTrue(text, !text.contains("E") && !text.contains("e"))
        assertEquals("0.0000001 0 0 0 1 0 0 0 0 12.5 -3.25 0.000000001", text)
    }

    @Test
    fun `files that cannot be scaled are refused with a reason and nothing is written`() {
        val noModel = ThreeMfScaler.scale(zip(mapOf("readme.txt" to "x")), out(), 2.0) as ScaleResult.Refused
        assertTrue(noModel.reason.contains("not a 3MF project"))
        val noItems = ThreeMfScaler.scale(zip(mapOf("3D/3dmodel.model" to model("", ""))), out(), 2.0) as ScaleResult.Refused
        assertTrue(noItems.reason.contains("nothing on its plate"))
        val noMesh = model("""<object id="1" type="model"><mesh><vertices></vertices></mesh></object>""", """<item objectid="1"/>""")
        assertTrue(
            (ThreeMfScaler.scale(zip(mapOf("3D/3dmodel.model" to noMesh)), out(), 2.0) as ScaleResult.Refused).reason.contains("geometry"),
        )
        val missingPart = model(
            """<object id="2"><components><component p:path="/3D/Objects/gone.model" objectid="1"/></components></object>""",
            """<item objectid="2"/>""",
        )
        assertTrue(
            (
                ThreeMfScaler.scale(
                    zip(mapOf("3D/3dmodel.model" to missingPart)),
                    out(),
                    2.0,
                ) as ScaleResult.Refused
                ).reason.contains("missing"),
        )
        val notZip = tmp.newFile("bad.3mf").apply { writeText("not a zip") }
        assertTrue(ThreeMfScaler.scale(notZip, out(), 2.0) is ScaleResult.Refused)
    }

    @Test
    fun `factors outside ten to four hundred percent are refused and the input is never changed`() {
        val m =
            model(
                """<object id="1" type="model"><mesh><vertices>
${vertices(cube)}
</vertices></mesh></object>""",
                """<item objectid="1"/>""",
            )
        val input = zip(mapOf("3D/3dmodel.model" to m))
        val before = input.readBytes()
        assertTrue(ThreeMfScaler.scale(input, out(), 0.05) is ScaleResult.Refused)
        assertTrue(ThreeMfScaler.scale(input, out(), 4.5) is ScaleResult.Refused)
        ThreeMfScaler.scale(input, out(), 1.5)
        assertTrue(before.contentEquals(input.readBytes()))
    }
}
