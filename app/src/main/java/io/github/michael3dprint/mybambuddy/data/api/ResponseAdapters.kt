package io.github.michael3dprint.mybambuddy.data.api

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import io.github.michael3dprint.mybambuddy.data.api.dto.ArchiveItemDto
import io.github.michael3dprint.mybambuddy.data.api.dto.ArchivesResponseDto
import io.github.michael3dprint.mybambuddy.data.api.dto.HealthResponse
import io.github.michael3dprint.mybambuddy.data.api.dto.SystemInfoResponse
import io.github.michael3dprint.mybambuddy.util.PrintDeckLogger

/** Accepts the health reply as an object, plain text or a boolean. */
class HealthResponseAdapter {
    /** Accepts the health reply as an object, plain text or a boolean. */
    @FromJson
    fun fromJson(reader: JsonReader, delegate: JsonAdapter<HealthResponse>): HealthResponse? = when (reader.peek()) {
        JsonReader.Token.STRING -> {
            val str = reader.nextString().trim()
            val isOk = str.equals("ok", ignoreCase = true) ||
                str.equals("healthy", ignoreCase = true) ||
                str.equals("up", ignoreCase = true) ||
                str.equals("true", ignoreCase = true) ||
                str.startsWith("ok", ignoreCase = true) ||
                str.startsWith("healthy", ignoreCase = true)
            HealthResponse(status = str, message = str, ok = isOk)
        }
        JsonReader.Token.BOOLEAN -> {
            val boolVal = reader.nextBoolean()
            HealthResponse(
                status = if (boolVal) "ok" else "error",
                message = if (boolVal) "ok" else "error",
                ok = boolVal,
            )
        }
        JsonReader.Token.BEGIN_OBJECT -> {
            delegate.fromJson(reader)
        }
        else -> {
            reader.skipValue()
            HealthResponse(status = "ok", message = "ok", ok = true)
        }
    }
}

/** Accepts the system-info reply as an object or plain text. */
class SystemInfoResponseAdapter {
    /** Accepts the system-info reply as an object or plain text. */
    @FromJson
    fun fromJson(reader: JsonReader, delegate: JsonAdapter<SystemInfoResponse>): SystemInfoResponse? = when (reader.peek()) {
        JsonReader.Token.STRING -> {
            val str = reader.nextString().trim()
            SystemInfoResponse(appName = str, system = str, version = "1.0", status = "ok")
        }
        JsonReader.Token.BOOLEAN -> {
            val boolVal = reader.nextBoolean()
            SystemInfoResponse(
                appName = "Bambuddy",
                system = "Bambuddy System",
                version = "1.0",
                status = if (boolVal) "ok" else "error",
            )
        }
        JsonReader.Token.BEGIN_OBJECT -> {
            delegate.fromJson(reader)
        }
        else -> {
            reader.skipValue()
            SystemInfoResponse(appName = "Bambuddy", system = "Bambuddy System", version = "1.0", status = "ok")
        }
    }
}

/** Accepts the archive list as a bare array or as a wrapper object. */
class ArchivesResponseDtoAdapter {
    /** Accepts the archive list as a bare array or as a wrapper object. */
    @FromJson
    fun fromJson(
        reader: JsonReader,
        delegate: JsonAdapter<ArchivesResponseDto>,
        itemAdapter: JsonAdapter<ArchiveItemDto>,
    ): ArchivesResponseDto? = try {
        when (reader.peek()) {
            JsonReader.Token.BEGIN_ARRAY -> {
                val list = mutableListOf<ArchiveItemDto>()
                reader.beginArray()
                while (reader.hasNext()) {
                    if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                        val pathBefore = reader.path
                        val item = try {
                            itemAdapter.fromJson(reader)
                        } catch (e: Exception) {
                            PrintDeckLogger.e("PrintDeckTrace", "Failed to parse ArchiveItemDto array item", e)
                            if (reader.path == pathBefore && reader.hasNext()) {
                                try {
                                    reader.skipValue()
                                } catch (_: Exception) {}
                            }
                            null
                        }
                        if (item != null) list.add(item)
                    } else {
                        PrintDeckLogger.w("PrintDeckTrace", "Skipping non-object item in archives array at ${reader.path}")
                        reader.skipValue()
                    }
                }
                reader.endArray()
                ArchivesResponseDto(archives = list, count = list.size)
            }
            JsonReader.Token.BEGIN_OBJECT -> {
                try {
                    delegate.fromJson(reader)
                } catch (e: Exception) {
                    PrintDeckLogger.e("PrintDeckTrace", "Failed to parse ArchivesResponseDto object", e)
                    ArchivesResponseDto(archives = emptyList(), count = 0)
                }
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    } catch (e: Exception) {
        PrintDeckLogger.e("PrintDeckTrace", "Unexpected error in ArchivesResponseDtoAdapter", e)
        ArchivesResponseDto(archives = emptyList(), count = 0)
    }
}
