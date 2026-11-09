package app.gamecollection

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Онлайн-поиск названия по штрихкоду (бесплатный лимитированный сервис UPCitemdb).
 * Возвращает null, если нет сети, штрихкода нет в базе или исчерпан лимит.
 */
object Upc {
    fun lookupTitle(code: String): String? = try {
        val conn = URL("https://api.upcitemdb.com/prod/trial/lookup?upc=$code")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "GameCollection/0.1")
        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val items = JSONObject(body).optJSONArray("items")
            if (items != null && items.length() > 0) items.getJSONObject(0).optString("title").ifBlank { null } else null
        } else null
    } catch (e: Exception) {
        null
    }
}
