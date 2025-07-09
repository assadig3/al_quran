package com.hag.al_quran

data class SurahJson(
    val id: Int,
    val name: String,
    val page: Int,
    val verses: List<AyahJson>
)

data class AyahJson(
    val id: Int,
    val text: String,
    val page: Int
)
