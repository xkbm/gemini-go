package com.gemini.go.data.api

import com.gemini.go.data.model.Part
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

class PartAdapter : JsonSerializer<Part>, JsonDeserializer<Part> {
    override fun serialize(src: Part, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        when (src) {
            is Part.TextPart -> obj.addProperty("text", src.text)
            is Part.InlineDataPart -> {
                val inline = JsonObject()
                inline.addProperty("mimeType", src.mimeType)
                inline.addProperty("data", src.data)
                obj.add("inline_data", inline)
            }
            is Part.FunctionCallPart -> {
                val fc = JsonObject()
                fc.addProperty("name", src.name)
                fc.add("args", context.serialize(src.args))
                obj.add("functionCall", fc)
            }
        }
        return obj
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Part {
        val obj = json.asJsonObject
        return when {
            obj.has("text") -> Part.TextPart(obj.get("text").asString)
            obj.has("inline_data") -> {
                val i = obj.getAsJsonObject("inline_data")
                Part.InlineDataPart(i.get("mimeType").asString, i.get("data").asString)
            }
            obj.has("inlineData") -> {
                val i = obj.getAsJsonObject("inlineData")
                Part.InlineDataPart(i.get("mimeType").asString, i.get("data").asString)
            }
            obj.has("functionCall") -> {
                val fc = obj.getAsJsonObject("functionCall")
                val name = fc.get("name").asString
                @Suppress("UNCHECKED_CAST")
                val args = context.deserialize<Map<String, Any?>>(fc.get("args"), Map::class.java) ?: emptyMap()
                Part.FunctionCallPart(name, args)
            }
            obj.has("function_call") -> {
                val fc = obj.getAsJsonObject("function_call")
                val name = fc.get("name").asString
                @Suppress("UNCHECKED_CAST")
                val args = context.deserialize<Map<String, Any?>>(fc.get("args"), Map::class.java) ?: emptyMap()
                Part.FunctionCallPart(name, args)
            }
            else -> Part.TextPart(obj.toString())
        }
    }
}
