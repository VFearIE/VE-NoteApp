package com.noteVE.data

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromStringList(list: List<String>?): String? =
        list?.let { JSONArray(it).toString() }

    @TypeConverter
    fun toStringList(json: String?): List<String>? = json?.let { j ->
        val arr = JSONArray(j)
        (0 until arr.length()).map { arr.getString(it) }
    }
}
