package com.mapconductor.react.here

import com.facebook.react.bridge.ReadableMap

fun ReadableMap.getDoubleOrNull(name: String): Double? =
    if (hasKey(name) && !isNull(name)) getDouble(name) else null

fun ReadableMap.getBooleanOrNull(name: String): Boolean? =
    if (hasKey(name) && !isNull(name)) getBoolean(name) else null

fun ReadableMap.getIntOrNull(name: String): Int? =
    if (hasKey(name) && !isNull(name)) getInt(name) else null
