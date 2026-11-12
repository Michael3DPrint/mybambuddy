package io.github.michael3dprint.mybambuddy.data.api

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterStatusDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrintersResponse
import io.github.michael3dprint.mybambuddy.util.PrintDeckLogger

/**
 * Reads the printers reply whether it is a list, a wrapper object or a map. Every branch must consume or skip each value, or
 * parsing never ends (see docs/TASK_LEDGER.md).
 */
class PrintersResponseAdapter {
    /** Reads the printers reply whether it is a list, a wrapper object or a map. */
    @FromJson
    fun fromJson(reader: JsonReader, delegate: JsonAdapter<PrintersResponse>, itemAdapter: JsonAdapter<PrinterDto>): PrintersResponse? {
        val list = mutableListOf<PrinterDto>()
        return try {
            when (reader.peek()) {
                JsonReader.Token.BEGIN_ARRAY -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                            val pathBefore = reader.path
                            val item = try {
                                itemAdapter.fromJson(reader)
                            } catch (e: Exception) {
                                PrintDeckLogger.e("PrintDeckTrace", "Failed to parse PrinterDto item in array", e)
                                if (reader.path == pathBefore && reader.hasNext()) {
                                    try {
                                        reader.skipValue()
                                    } catch (_: Exception) {}
                                }
                                null
                            }
                            if (item != null) list.add(item)
                        } else {
                            PrintDeckLogger.w("PrintDeckTrace", "Skipping non-object item in printers array at ${reader.path}")
                            reader.skipValue()
                        }
                    }
                    reader.endArray()
                    PrintersResponse(printers = list, count = list.size)
                }
                JsonReader.Token.BEGIN_OBJECT -> {
                    val peek = reader.peekJson()
                    var wrapperListKey: String? = null
                    var isWrapperMap = false
                    var hasPrinterObjectFields = false
                    var hasMapChildObjects = false
                    val printerFields = setOf(
                        "id", "name", "model", "state", "status", "progress",
                        "remaining_time", "remaining_time_seconds", "snapshot_url",
                        "camera_url", "temperatures", "hms_errors", "hms_status", "print_name",
                    )
                    val printerSubObjects = setOf("status", "temperatures", "hms_errors", "hms_status")

                    try {
                        peek.beginObject()
                        while (peek.hasNext()) {
                            val name = peek.nextName()
                            val token = peek.peek()
                            if (name == "printers" || name == "data" || name == "items") {
                                wrapperListKey = name
                                if (token == JsonReader.Token.BEGIN_OBJECT) {
                                    isWrapperMap = true
                                }
                            }
                            if (name in printerFields) {
                                hasPrinterObjectFields = true
                            }
                            if (token == JsonReader.Token.BEGIN_OBJECT && name !in printerSubObjects) {
                                hasMapChildObjects = true
                            }
                            peek.skipValue()
                        }
                        peek.endObject()
                    } catch (e: Exception) {
                        PrintDeckLogger.e("PrintDeckTrace", "Error inspecting PrintersResponse JSON structure", e)
                    }

                    if (wrapperListKey != null) {
                        if (isWrapperMap) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val name = reader.nextName()
                                if (name == wrapperListKey && reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val key = reader.nextName()
                                        if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                                            val item = try {
                                                itemAdapter.fromJson(reader)
                                            } catch (e: Exception) {
                                                PrintDeckLogger.e(
                                                    "PrintDeckTrace",
                                                    "Failed to parse PrinterDto inside wrapper map key=$key",
                                                    e,
                                                )
                                                null
                                            }
                                            if (item != null) {
                                                val finalItem = if (item.id.isNullOrBlank()) item.copy(id = key) else item
                                                list.add(finalItem)
                                            }
                                        } else {
                                            reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                            PrintersResponse(printers = list, count = list.size)
                        } else {
                            val parsed = try {
                                delegate.fromJson(reader)
                            } catch (e: Exception) {
                                PrintDeckLogger.e("PrintDeckTrace", "Failed to parse PrintersResponse object wrapper", e)
                                null
                            }
                            if (parsed != null) {
                                val effectiveList = parsed.effectivePrinters
                                PrintersResponse(
                                    printers = effectiveList,
                                    data = parsed.data,
                                    items = parsed.items,
                                    count = parsed.count ?: effectiveList.size,
                                )
                            } else {
                                PrintersResponse(printers = emptyList(), count = 0)
                            }
                        }
                    } else if (hasPrinterObjectFields && !hasMapChildObjects) {
                        val singleItem = try {
                            itemAdapter.fromJson(reader)
                        } catch (e: Exception) {
                            PrintDeckLogger.e("PrintDeckTrace", "Failed to parse single PrinterDto object", e)
                            null
                        }
                        if (singleItem != null) {
                            PrintersResponse(printers = listOf(singleItem), count = 1)
                        } else {
                            PrintersResponse(printers = emptyList(), count = 0)
                        }
                    } else {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                                val item = try {
                                    itemAdapter.fromJson(reader)
                                } catch (e: Exception) {
                                    PrintDeckLogger.e("PrintDeckTrace", "Failed to parse PrinterDto map item for key=$key", e)
                                    null
                                }
                                if (item != null) {
                                    val finalItem = if (item.id.isNullOrBlank()) item.copy(id = key) else item
                                    list.add(finalItem)
                                }
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endObject()
                        PrintersResponse(printers = list, count = list.size)
                    }
                }
                else -> {
                    reader.skipValue()
                    PrintersResponse(printers = emptyList(), count = 0)
                }
            }
        } catch (e: Exception) {
            PrintDeckLogger.e("PrintDeckTrace", "Unexpected error in PrintersResponseAdapter.fromJson", e)
            PrintersResponse(printers = list, count = list.size)
        }
    }
}

/** Reads a printer status whether it arrives as an object, a wrapped object or an array. */
class PrinterStatusDtoAdapter {
    /** Reads a printer status whether it is an object, a wrapped object or an array. */
    @FromJson
    fun fromJson(reader: JsonReader, delegate: JsonAdapter<PrinterStatusDto>): PrinterStatusDto? = try {
        when (reader.peek()) {
            JsonReader.Token.STRING -> {
                val str = reader.nextString()
                PrinterStatusDto(state = str)
            }
            JsonReader.Token.BEGIN_ARRAY -> {
                reader.beginArray()
                var result: PrinterStatusDto? = null
                if (reader.hasNext()) {
                    result = try {
                        when (reader.peek()) {
                            JsonReader.Token.STRING -> PrinterStatusDto(state = reader.nextString())
                            JsonReader.Token.BEGIN_OBJECT -> delegate.fromJson(reader)
                            else -> {
                                reader.skipValue()
                                null
                            }
                        }
                    } catch (e: Exception) {
                        PrintDeckLogger.e("PrintDeckTrace", "Failed to parse PrinterStatusDto from status array item", e)
                        null
                    }
                }
                while (reader.hasNext()) {
                    reader.skipValue()
                }
                reader.endArray()
                result ?: PrinterStatusDto()
            }
            JsonReader.Token.BEGIN_OBJECT -> {
                val peek = reader.peekJson()
                var isWrapped = false
                var wrapperKey: String? = null
                try {
                    peek.beginObject()
                    var hasDirectStatusFields = false
                    while (peek.hasNext()) {
                        val name = peek.nextName()
                        if (name == "state" ||
                            name == "progress" ||
                            name == "remaining_time" ||
                            name == "current_layer" ||
                            name == "temperatures" ||
                            name == "hms_status" ||
                            name == "hms_errors"
                        ) {
                            hasDirectStatusFields = true
                        } else if ((name == "status" || name == "data") && peek.peek() == JsonReader.Token.BEGIN_OBJECT) {
                            wrapperKey = name
                        }
                        peek.skipValue()
                    }
                    peek.endObject()
                    if (!hasDirectStatusFields && wrapperKey != null) {
                        isWrapped = true
                    }
                } catch (e: Exception) {
                    PrintDeckLogger.e("PrintDeckTrace", "Error checking PrinterStatusDto wrapper", e)
                }

                if (isWrapped && wrapperKey != null) {
                    reader.beginObject()
                    var result: PrinterStatusDto? = null
                    while (reader.hasNext()) {
                        val name = reader.nextName()
                        if (name == wrapperKey && reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                            result = try {
                                delegate.fromJson(reader)
                            } catch (e: Exception) {
                                PrintDeckLogger.e("PrintDeckTrace", "Failed to parse wrapped status object", e)
                                null
                            }
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                    result ?: PrinterStatusDto()
                } else {
                    try {
                        delegate.fromJson(reader)
                    } catch (e: Exception) {
                        PrintDeckLogger.e("PrintDeckTrace", "Failed to parse status object", e)
                        PrinterStatusDto()
                    }
                }
            }
            else -> {
                reader.skipValue()
                PrinterStatusDto()
            }
        }
    } catch (e: Exception) {
        PrintDeckLogger.e("PrintDeckTrace", "Unexpected error parsing PrinterStatusDto", e)
        PrinterStatusDto()
    }
}
