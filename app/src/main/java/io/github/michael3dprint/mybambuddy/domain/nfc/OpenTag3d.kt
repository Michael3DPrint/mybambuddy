package io.github.michael3dprint.mybambuddy.domain.nfc

/** MIME type of an OpenTag3D NDEF record. */
const val OPENTAG3D_MIME = "application/opentag3d"

/** The fields an OpenTag3D tag carries that this app uses. Text fields are already sanitised. */
data class OpenTag3dData(
    val material: String,
    val modifiers: String,
    val manufacturer: String,
    val colorName: String,
    /** RRGGBBAA hex, upper case. */
    val rgba: String,
    val diameterMm: Double,
    val weightGrams: Int,
    val printTempC: Int,
)

/**
 * Encoder/decoder for the OpenTag3D core payload (102 bytes), byte-for-byte compatible with Bambuddy's
 * SpoolBuddy encoder (`backend/app/services/opentag3d.py`). Android adds the NDEF record framing itself.
 *
 * Layout (offsets): 0x00 version u16 BE | 0x02 material 5 | 0x07 modifiers 5 | 0x1B manufacturer 16 |
 * 0x2B colour name 32 | 0x4B colour 1 RGBA 4 | 0x5C diameter u16 (µm) | 0x5E weight u16 (g) | 0x60 print temp (value/5).
 * Tag content is untrusted: every read is bounds-checked and text is stripped of control characters.
 */
object OpenTag3d {
    /** Size in bytes of an encoded OpenTag3D payload. */
    const val PAYLOAD_SIZE = 102

    /** The most bytes read from a tag. A tag tapped outside the app is untrusted, so anything larger is ignored, not parsed. */
    const val MAX_PAYLOAD_SIZE = 1024
    private const val TAG_VERSION = 1000
    private const val OFF_MATERIAL = 0x02
    private const val OFF_MODIFIERS = 0x07
    private const val OFF_MANUFACTURER = 0x1B
    private const val OFF_COLOR_NAME = 0x2B
    private const val OFF_RGBA = 0x4B
    private const val OFF_DIAMETER = 0x5C
    private const val OFF_WEIGHT = 0x5E
    private const val OFF_TEMP = 0x60
    private const val MAX_U16 = 65_535

    /** Encodes the data as an OpenTag3D payload of [PAYLOAD_SIZE] bytes. */
    fun encode(data: OpenTag3dData): ByteArray {
        val buf = ByteArray(PAYLOAD_SIZE)
        buf[0] = (TAG_VERSION shr 8).toByte()
        buf[1] = TAG_VERSION.toByte()
        putText(buf, OFF_MATERIAL, 5, data.material)
        putText(buf, OFF_MODIFIERS, 5, data.modifiers)
        putText(buf, OFF_MANUFACTURER, 16, data.manufacturer)
        putText(buf, OFF_COLOR_NAME, 32, data.colorName)
        val rgba = data.rgba.filter { it.isLetterOrDigit() }.padEnd(8, '0').take(8)
        for (i in 0 until 4) {
            buf[OFF_RGBA + i] = rgba.substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: 0
        }
        putU16(buf, OFF_DIAMETER, (data.diameterMm * 1000).toInt().coerceIn(0, MAX_U16))
        putU16(buf, OFF_WEIGHT, data.weightGrams.coerceIn(0, MAX_U16))
        buf[OFF_TEMP] = (data.printTempC / 5).coerceIn(0, 255).toByte()
        return buf
    }

    /** Returns null when [payload] is too short to be an OpenTag3D core payload, or larger than [MAX_PAYLOAD_SIZE]. */
    fun decode(payload: ByteArray): OpenTag3dData? {
        if (payload.size !in PAYLOAD_SIZE..MAX_PAYLOAD_SIZE) return null
        val rgba = (0 until 4).joinToString("") { "%02X".format(payload[OFF_RGBA + it].toInt() and 0xFF) }
        return OpenTag3dData(
            material = getText(payload, OFF_MATERIAL, 5),
            modifiers = getText(payload, OFF_MODIFIERS, 5),
            manufacturer = getText(payload, OFF_MANUFACTURER, 16),
            colorName = getText(payload, OFF_COLOR_NAME, 32),
            rgba = rgba,
            diameterMm = getU16(payload, OFF_DIAMETER) / 1000.0,
            weightGrams = getU16(payload, OFF_WEIGHT),
            printTempC = (payload[OFF_TEMP].toInt() and 0xFF) * 5,
        )
    }

    private fun putText(buf: ByteArray, offset: Int, length: Int, text: String) {
        val bytes = TagText.sanitize(text, length).toByteArray(Charsets.UTF_8)
        // Truncate on bytes, never past the field, so multi-byte characters cannot overflow into the next field.
        for (i in 0 until length) buf[offset + i] = if (i < bytes.size) bytes[i] else ' '.code.toByte()
    }

    private fun getText(buf: ByteArray, offset: Int, length: Int): String =
        TagText.sanitize(String(buf, offset, length, Charsets.UTF_8), length)

    private fun putU16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value shr 8).toByte()
        buf[offset + 1] = value.toByte()
    }

    private fun getU16(buf: ByteArray, offset: Int): Int = ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)
}
