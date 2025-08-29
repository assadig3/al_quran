package com.hag.al_quran.Surah

data class Surah(
    val number: Int,
    val name: String,
    val englishName: String,
    val type: String,
    val ayahCount: Int,
    val pageNumber: Int
)