package com.safeincloud.core.json

import kotlinx.serialization.json.Json

object AppJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        isLenient = false
    }

    inline fun <reified T> decode(text: String): T = instance.decodeFromString(text)
}
