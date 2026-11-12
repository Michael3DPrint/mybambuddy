package io.github.michael3dprint.mybambuddy.domain.threemf

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** The outcome of scaling a 3MF: a new file, or the reason the file cannot be scaled (nothing is written then). */
sealed interface ScaleResult {
    /** [output] holds the scaled copy. */
    data class Scaled(val output: File) : ScaleResult

    /** The file cannot be scaled; [reason] is in plain words. */
    data class Refused(val reason: String) : ScaleResult
}

/**
 * Scales every object on the plate of a 3MF project by one factor (design 40). A 3MF places each object with a 3x4 transform in its
 * build list; this multiplies the linear part by the factor and moves the translation so the object keeps the centre of its footprint and its
 * lowest point stays where it was (usually on the bed). The slicer does not drop an object back onto the bed, so without that move the part that
 * grew below the bed would be cut off (seen with Bambu Studio on a real file). Only the build list's transforms change; every other file in the
 * archive is copied as it is, and the original is never modified. Meshes are read as a stream, so a file whose mesh is tens of megabytes does
 * not have to fit in memory.
 */
object ThreeMfScaler {
    /** The smallest factor accepted: 10 %. */
    const val MIN_FACTOR = 0.1

    /** The largest factor accepted: 400 %. */
    const val MAX_FACTOR = 4.0

    private const val MAIN_MODEL = "3D/3dmodel.model"
    private const val MAX_INPUT_BYTES = 120L * 1024 * 1024
    private const val MAX_UNCOMPRESSED_BYTES = 600L * 1024 * 1024

    /** Scales [input] by [factor] and writes the copy to [output]; the input and its other files are left as they are. */
    fun scale(input: File, output: File, factor: Double): ScaleResult {
        if (factor !in MIN_FACTOR..MAX_FACTOR) return ScaleResult.Refused("The scale must be between 10 % and 400 %.")
        if (input.length() > MAX_INPUT_BYTES) return ScaleResult.Refused("The file is too large to scale on the phone.")
        return try {
            ZipFile(input).use { zip -> scaleZip(zip, output, factor) }
        } catch (e: java.util.zip.ZipException) {
            output.delete()
            ScaleResult.Refused("The file is not a readable 3MF (${e.message}).")
        } catch (e: java.io.IOException) {
            output.delete()
            ScaleResult.Refused("The file could not be read: ${e.message}.")
        }
    }

    private fun scaleZip(zip: ZipFile, output: File, factor: Double): ScaleResult {
        if (zip.entries().asSequence().sumOf { it.size.coerceAtLeast(0L) } > MAX_UNCOMPRESSED_BYTES) {
            return ScaleResult.Refused("The file is too large to scale on the phone.")
        }
        val mainEntry = zip.getEntry(MAIN_MODEL) ?: return ScaleResult.Refused("This is not a 3MF project (no model description found).")
        val main = zip.getInputStream(mainEntry).use { it.readBytes() }.toString(Charsets.UTF_8)
        val model = ModelDescription.parse(main) ?: return ScaleResult.Refused("The model description could not be read.")
        if (model.items.isEmpty()) return ScaleResult.Refused("The file has nothing on its plate to scale.")

        val newTransforms = HashMap<Int, Matrix3x4>()
        for (item in model.items) {
            val leaves =
                model.leavesOf(item.objectId) ?: return ScaleResult.Refused("An object on the plate refers to a part that is missing.")
            val bounds = Bounds()
            for (leaf in leaves) {
                val entryName = leaf.path?.trimStart('/') ?: MAIN_MODEL
                val entry =
                    zip.getEntry(entryName) ?: return ScaleResult.Refused("A part of the model ($entryName) is missing from the file.")
                val chain = leaf.chain
                zip.getInputStream(entry).use { stream ->
                    VertexScanner.scan(stream, leaf.objectId) { x, y, z -> bounds.add(item.transform.linearOnly(chain.apply(x, y, z))) }
                }
            }
            if (!bounds.hasPoints) return ScaleResult.Refused("The model's geometry could not be found, so it cannot be scaled.")
            newTransforms[item.index] = item.transform.scaledKeeping(factor, bounds)
        }

        val rewritten = model.rewriteItems(main) { newTransforms.getValue(it.index) }
        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { out ->
            for (entry in zip.entries()) {
                val copy = ZipEntry(entry.name).apply { time = entry.time }
                out.putNextEntry(copy)
                if (entry.name ==
                    MAIN_MODEL
                ) {
                    out.write(rewritten.toByteArray(Charsets.UTF_8))
                } else {
                    zip.getInputStream(entry).use { it.copyTo(out) }
                }
                out.closeEntry()
            }
        }
        return ScaleResult.Scaled(output)
    }
}

/** A 3MF transform: a 3x3 matrix applied to a row vector, then a translation (the order 3MF writes its twelve numbers). */
internal data class Matrix3x4(val m: DoubleArray) {
    init {
        require(m.size == SIZE)
    }

    /** Applies this transform to a point. */
    fun apply(x: Double, y: Double, z: Double) = Point(
        x * m[0] + y * m[3] + z * m[6] + m[9],
        x * m[1] + y * m[4] + z * m[7] + m[10],
        x * m[2] + y * m[5] + z * m[8] + m[11],
    )

    /** Applies this transform to [p] (convenience for chains). */
    fun apply(p: Point) = apply(p.x, p.y, p.z)

    /** Applies only the 3x3 part (no translation) to [p]. */
    fun linearOnly(p: Point) = Point(
        p.x * m[0] + p.y * m[3] + p.z * m[6],
        p.x * m[1] + p.y * m[4] + p.z * m[7],
        p.x * m[2] + p.y * m[5] + p.z * m[8],
    )

    /** This transform followed by [next]. */
    fun then(next: Matrix3x4): Matrix3x4 {
        val out = DoubleArray(SIZE)
        for (i in 0 until 3) for (j in 0 until 3) out[i * 3 + j] = (0 until 3).sumOf { k -> m[i * 3 + k] * next.m[k * 3 + j] }
        for (j in 0 until 3) out[9 + j] = (0 until 3).sumOf { k -> m[9 + k] * next.m[k * 3 + j] } + next.m[9 + j]
        return Matrix3x4(out)
    }

    /**
     * This item transform with its linear part scaled by [factor] and its translation moved by (1 - factor) times the footprint centre and
     * lowest point of [bounds] (measured under the linear part alone), so the centre of the footprint and the lowest point do not move.
     */
    fun scaledKeeping(factor: Double, bounds: Bounds): Matrix3x4 {
        val out = DoubleArray(SIZE) { if (it < 9) m[it] * factor else m[it] }
        out[9] += (1 - factor) * bounds.centreX
        out[10] += (1 - factor) * bounds.centreY
        out[11] += (1 - factor) * bounds.minZ
        return Matrix3x4(out)
    }

    /** The twelve numbers as text, in plain decimal (no exponent) with trailing zeros trimmed. */
    fun toText(): String = m.joinToString(" ") { plain(it) }

    override fun equals(other: Any?) = other is Matrix3x4 && m.contentEquals(other.m)

    override fun hashCode() = m.contentHashCode()

    companion object {
        private const val SIZE = 12

        /** The transform that changes nothing, used for an item or component that has none. */
        val IDENTITY = Matrix3x4(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0))

        /** Reads twelve numbers; anything else gives null. */
        fun parse(text: String?): Matrix3x4? {
            val parts = text?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toDoubleOrNull() } ?: return null
            return if (parts.size == SIZE) Matrix3x4(parts.toDoubleArray()) else null
        }

        private fun plain(v: Double): String {
            val s = String.format(Locale.ROOT, "%.9f", v).trimEnd('0').trimEnd('.')
            return if (s == "-0" || s.isEmpty()) "0" else s
        }
    }
}

/** A point in space. */
internal data class Point(val x: Double, val y: Double, val z: Double)

/** The extent of the points added, for a footprint centre and a lowest point. */
internal class Bounds {
    /** True once at least one point was added. */
    var hasPoints = false
        private set
    private var minX = Double.MAX_VALUE
    private var maxX = -Double.MAX_VALUE
    private var minY = Double.MAX_VALUE
    private var maxY = -Double.MAX_VALUE

    /** The lowest z of the points added. */
    var minZ = Double.MAX_VALUE
        private set

    /** Adds the point [p] to the extent, widening the footprint or lowering the lowest point when it lies outside. */
    fun add(p: Point) {
        hasPoints = true
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
        if (p.z < minZ) minZ = p.z
    }

    /** The middle of the footprint along x. */
    val centreX: Double get() = (minX + maxX) / 2

    /** The middle of the footprint along y. */
    val centreY: Double get() = (minY + maxY) / 2
}

/** One build item: the object it places, its transform, and its position among the items (used to put the new transform back). */
internal data class BuildItem(val index: Int, val objectId: Int, val transform: Matrix3x4)

/** A mesh that belongs to a build item, with the transforms that lead from the mesh to the item's own coordinates. */
internal data class Leaf(val path: String?, val objectId: Int, val chain: Chain)

/** Component transforms applied in order, innermost first. */
internal class Chain(private val steps: List<Matrix3x4>) {
    /** Maps a mesh point through every component transform. */
    fun apply(x: Double, y: Double, z: Double): Point {
        var p = Point(x, y, z)
        for (s in steps) p = s.apply(p)
        return p
    }
}

/** What the main model file says: its objects (with their components), and the build list. */
internal class ModelDescription private constructor(
    private val objects: Map<Int, List<Component>>,
    private val meshObjects: Set<Int>,
    val items: List<BuildItem>,
    private val itemTags: List<IntRange>,
) {
    private class Component(val path: String?, val objectId: Int, val transform: Matrix3x4)

    /** The meshes behind object [id], following components; null when a component refers to nothing. */
    fun leavesOf(id: Int, path: String? = null, steps: List<Matrix3x4> = emptyList(), depth: Int = 0): List<Leaf>? {
        if (depth > MAX_DEPTH) return null
        val comps = objects[id]
        if (comps.isNullOrEmpty()) return if (id in meshObjects || path != null) listOf(Leaf(path, id, Chain(steps))) else null
        return comps.flatMap { c -> leavesOf(c.objectId, c.path ?: path, listOf(c.transform) + steps.map { it }, depth + 1) ?: return null }
    }

    /** The main model text with each build item's transform replaced by [newTransform]'s answer for it. */
    fun rewriteItems(text: String, newTransform: (BuildItem) -> Matrix3x4): String {
        val sb = StringBuilder()
        var cursor = 0
        items.forEachIndexed { i, item ->
            val range = itemTags[i]
            sb.append(text, cursor, range.first)
            sb.append(withTransform(text.substring(range.first, range.last + 1), newTransform(item).toText()))
            cursor = range.last + 1
        }
        sb.append(text, cursor, text.length)
        return sb.toString()
    }

    private fun withTransform(tag: String, value: String): String = if (TRANSFORM.containsMatchIn(tag)) {
        TRANSFORM.replace(tag) { "transform=\"$value\"" }
    } else {
        Regex("\\s*/?>$").find(tag)?.let { end -> tag.substring(0, end.range.first) + " transform=\"$value\"" + end.value } ?: tag
    }

    companion object {
        private const val MAX_DEPTH = 8
        private val TRANSFORM = Regex("transform=\"[^\"]*\"")
        private val OBJECT = Regex("<object\\s[^>]*?id=\"(\\d+)\"[^>]*?(?:/>|>(.*?)</object>)", RegexOption.DOT_MATCHES_ALL)
        private val COMPONENT = Regex("<component\\s[^>]*?/?>")
        private val ITEM = Regex("<item\\s[^>]*?/?>")

        private fun attr(tag: String, name: String): String? =
            Regex("(?:^|[\\s:])" + Regex.escape(name) + "=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)

        /** Reads the model description, or null when it cannot be understood. */
        fun parse(text: String): ModelDescription? {
            val resources = text.substringAfter("<resources", "").substringBefore("</resources>", "")
            val objects = HashMap<Int, List<Component>>()
            val meshes = HashSet<Int>()
            for (m in OBJECT.findAll(resources)) {
                val id = m.groupValues[1].toIntOrNull() ?: continue
                val body = m.groupValues[2]
                val comps = COMPONENT.findAll(body).mapNotNull { tag ->
                    val oid = attr(tag.value, "objectid")?.toIntOrNull() ?: return@mapNotNull null
                    Component(attr(tag.value, "path"), oid, Matrix3x4.parse(attr(tag.value, "transform")) ?: Matrix3x4.IDENTITY)
                }.toList()
                objects[id] = comps
                if (body.contains("<mesh")) meshes += id
            }
            val buildStart = text.indexOf("<build")
            if (buildStart < 0) return null
            val items = ArrayList<BuildItem>()
            val ranges = ArrayList<IntRange>()
            for ((i, m) in ITEM.findAll(text, buildStart).withIndex()) {
                val oid = attr(m.value, "objectid")?.toIntOrNull() ?: return null
                items += BuildItem(i, oid, Matrix3x4.parse(attr(m.value, "transform")) ?: Matrix3x4.IDENTITY)
                ranges += m.range
            }
            return ModelDescription(objects, meshes, items, ranges)
        }
    }
}

/** Reads the vertices of one object from a model file as a stream, without holding the file in memory. */
internal object VertexScanner {
    private const val BUFFER = 64 * 1024

    /** Calls [onVertex] for every vertex of object [objectId] in [input]. */
    fun scan(input: InputStream, objectId: Int, onVertex: (Double, Double, Double) -> Unit) {
        val reader = BufferedInputStream(input, BUFFER).reader(Charsets.UTF_8).buffered(BUFFER)
        var currentObject = -1
        val tag = StringBuilder()
        var inTag = false
        while (true) {
            val c = reader.read()
            if (c < 0) break
            if (!inTag) {
                if (c == '<'.code) {
                    inTag = true
                    tag.setLength(0)
                    tag.append('<')
                }
            } else {
                tag.append(c.toChar())
                if (c == '>'.code) {
                    inTag = false
                    if (tag.startsWith("<object ")) {
                        currentObject = Regex("[\\s]id=\"(\\d+)\"").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                    } else if (tag.startsWith("</object")) {
                        currentObject = -1
                    } else if (tag.startsWith("<vertex ") && currentObject == objectId) {
                        val x = number(tag, " x=\"")
                        val y = number(tag, " y=\"")
                        val z = number(tag, " z=\"")
                        if (x != null && y != null && z != null) onVertex(x, y, z)
                    }
                }
            }
        }
    }

    private fun number(tag: CharSequence, key: String): Double? {
        val start = tag.indexOf(key)
        if (start < 0) return null
        val from = start + key.length
        val end = tag.indexOf("\"", from)
        return if (end < 0) null else tag.substring(from, end).toDoubleOrNull()
    }
}
