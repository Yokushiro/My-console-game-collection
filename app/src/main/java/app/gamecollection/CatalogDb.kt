package app.gamecollection

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class CatalogItem(
    val id: Long,
    val platformKey: String,
    val platformLabel: String,
    val title: String,
    val region: String,
    val serial: String,
    val extra: String
)

/** Личный каталог пользователя (хранится на телефоне). */
class CatalogDb(ctx: Context) : SQLiteOpenHelper(ctx, "catalog.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT, platform_key TEXT, platform_label TEXT," +
                " title TEXT, region TEXT, serial TEXT, extra TEXT, added_at INTEGER)"
        )
        db.execSQL("CREATE TABLE barcodes(barcode TEXT PRIMARY KEY, platform_key TEXT, game_id INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun all(): List<CatalogItem> {
        val out = ArrayList<CatalogItem>()
        readableDatabase.rawQuery(
            "SELECT id,platform_key,platform_label,title,region,serial,extra FROM items", null
        ).use { c ->
            while (c.moveToNext()) {
                out += CatalogItem(
                    c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "",
                    c.getString(4) ?: "", c.getString(5) ?: "", c.getString(6) ?: ""
                )
            }
        }
        return out
    }

    fun add(e: Entry) {
        val v = ContentValues().apply {
            put("platform_key", e.key)
            put("platform_label", e.label)
            put("title", e.displayTitle)
            put("region", e.region)
            put("serial", e.serial)
            put("extra", e.extra)
            put("added_at", System.currentTimeMillis())
        }
        writableDatabase.insert("items", null, v)
    }

    fun delete(id: Long) {
        writableDatabase.delete("items", "id=?", arrayOf(id.toString()))
    }

    fun saveBarcode(barcode: String, key: String, gameId: Long) {
        val v = ContentValues().apply {
            put("barcode", barcode)
            put("platform_key", key)
            put("game_id", gameId)
        }
        writableDatabase.insertWithOnConflict("barcodes", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun mappedGame(barcode: String): Pair<String, Long>? =
        readableDatabase.rawQuery(
            "SELECT platform_key,game_id FROM barcodes WHERE barcode=?", arrayOf(barcode)
        ).use { if (it.moveToFirst()) it.getString(0) to it.getLong(1) else null }
}
