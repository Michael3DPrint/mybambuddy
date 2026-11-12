package io.github.michael3dprint.mybambuddy.data.api

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import io.github.michael3dprint.mybambuddy.data.api.dto.HmsErrorDto
import io.github.michael3dprint.mybambuddy.util.PrintDeckLogger

/** The most action codes read from one HMS error. */
private const val MAX_HMS_ACTIONS = 16

/** Reads an HMS error from either a plain string or an object, skipping fields it does not know. */
class HmsErrorDtoAdapter {
    /** Reads an HMS error from a plain string or an object. */
    @FromJson
    fun fromJson(reader: JsonReader): HmsErrorDto? = try {
        when (reader.peek()) {
            JsonReader.Token.STRING -> {
                val str = reader.nextString()
                HmsErrorDto(code = str, description = "HMS Error $str")
            }
            JsonReader.Token.BEGIN_OBJECT -> {
                reader.beginObject()
                var code: String? = null
                var description: String? = null
                var severity: String? = null
                var attr: Long? = null
                var module: Int? = null
                var fullCode: String? = null
                val actions = mutableListOf<String>()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "code" -> code = when (reader.peek()) {
                            JsonReader.Token.STRING -> reader.nextString()
                            JsonReader.Token.NUMBER -> reader.nextLong().toString()
                            else -> {
                                reader.skipValue()
                                null
                            }
                        }
                        "description", "desc" ->
                            description =
                                if (reader.peek() == JsonReader.Token.STRING) {
                                    reader.nextString()
                                } else {
                                    reader.skipValue()
                                    null
                                }
                        "severity" -> severity = when (reader.peek()) {
                            JsonReader.Token.STRING -> reader.nextString()
                            JsonReader.Token.NUMBER -> reader.nextInt().toString()
                            else -> {
                                reader.skipValue()
                                null
                            }
                        }
                        "attr" ->
                            attr =
                                if (reader.peek() == JsonReader.Token.NUMBER) {
                                    reader.nextLong()
                                } else {
                                    reader.skipValue()
                                    null
                                }
                        "module" ->
                            module =
                                if (reader.peek() == JsonReader.Token.NUMBER) {
                                    reader.nextInt()
                                } else {
                                    reader.skipValue()
                                    null
                                }
                        "full_code" ->
                            fullCode =
                                if (reader.peek() == JsonReader.Token.STRING) {
                                    reader.nextString()
                                } else {
                                    reader.skipValue()
                                    null
                                }
                        "actions" ->
                            if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    // Bounded: a hostile server cannot make us collect an unlimited list.
                                    if (reader.peek() == JsonReader.Token.STRING && actions.size < MAX_HMS_ACTIONS) {
                                        actions += reader.nextString()
                                    } else {
                                        reader.skipValue()
                                    }
                                }
                                reader.endArray()
                            } else {
                                reader.skipValue()
                            }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                HmsErrorDto(
                    code = code,
                    description = description,
                    severity = severity,
                    attr = attr,
                    module = module,
                    fullCode = fullCode,
                    actions = actions,
                )
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    } catch (e: Exception) {
        PrintDeckLogger.e("PrintDeckTrace", "Failed to parse HmsErrorDto", e)
        null
    }
}
