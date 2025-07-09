import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hag.al_quran.SurahJson

fun loadSurahsFromAssets(context: Context): List<SurahJson> {
    val jsonString = context.assets.open("quran.json").bufferedReader().use { it.readText() }
    val listType = object : TypeToken<List<SurahJson>>() {}.type
    return Gson().fromJson(jsonString, listType)
}
