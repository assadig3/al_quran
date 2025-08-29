package com.hag.al_quran.Surah

import android.content.Context
import org.json.JSONArray

object SurahUtils {
    fun getAllSurahs(context: Context): List<Surah> {
        val surahs = mutableListOf<Surah>()
        try {
            val inputStream = context.assets.open("surahs.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val surahObj = jsonArray.getJSONObject(i)
                val number = surahObj.getInt("number")
                val name = surahObj.getString("name")
                val englishName = surahObj.getString("englishName")
                val type = surahObj.getString("type")
                val ayahCount = surahObj.getInt("ayahCount")
                val pageNumber = surahObj.getInt("pageNumber")

                val surah = Surah(
                    number = number,
                    name = name,
                    englishName = englishName,
                    type = type,
                    ayahCount = ayahCount,
                    pageNumber = pageNumber
                )
                surahs.add(surah)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return surahs
    }
}
