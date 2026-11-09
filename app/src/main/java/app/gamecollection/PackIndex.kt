package app.gamecollection

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/** Загружает офлайн-пакеты платформ из assets в память для быстрого поиска. */
class PackIndex(private val ctx: Context) {
    @Volatile
    var entries: List<Entry> = emptyList()
        private set
    private val serialMap = HashMap<String, MutableList<Entry>>()
    private val byId = HashMap<Pair<String, Long>, Entry>()

    fun load() {
        val dir = File(ctx.filesDir, "packs").apply { mkdirs() }
        val prefs = ctx.getSharedPreferences("packs", Context.MODE_PRIVATE)
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val version = PackageInfoCompat.getLongVersionCode(info)
        val names = ctx.assets.list("packs")?.filter { it.endsWith(".db") } ?: emptyList()

        if (prefs.getLong("copied_version", -1L) != version) {
            for (n in names) {
                ctx.assets.open("packs/$n").use { input ->
                    File(dir, n).outputStream().use { input.copyTo(it) }
                }
            }
            prefs.edit().putLong("copied_version", version).apply()
        }

        val all = ArrayList<Entry>(70000)
        for (n in names) {
            val f = File(dir, n)
            if (!f.exists()) continue
            val key = n.removeSuffix(".db")
            val db = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                var label = key
                db.rawQuery("SELECT value FROM meta WHERE key='platform'", null).use {
                    if (it.moveToFirst()) label = it.getString(0)
                }
                db.rawQuery("SELECT id,title,region,languages,disc,extra,serial FROM games", null).use { c ->
                    while (c.moveToNext()) {
                        val title = c.getString(1) ?: continue
                        val e = Entry(
                            key = key, label = label, id = c.getLong(0), title = title,
                            region = c.getString(2) ?: "",
                            languages = if (c.isNull(3)) null else c.getString(3),
                            disc = if (c.isNull(4)) null else c.getInt(4),
                            extra = c.getString(5) ?: "",
                            serial = c.getString(6) ?: "",
                            tokens = Text.tokens(title).toSet(),
                            sig = Text.sigTokens(title)
                        )
                        all += e
                        byId[key to e.id] = e
                        for (s in e.serial.split(", ")) {
                            val sn = Text.serialNorm(s)
                            if (sn.isNotEmpty()) serialMap.getOrPut(sn) { ArrayList(2) } += e
                        }
                    }
                }
            } finally {
                db.close()
            }
        }
        all.sortWith(compareBy({ Platforms.order(it.key) }, { it.title }))
        entries = all
    }

    fun bySerials(codes: Set<String>): List<Entry> =
        codes.flatMap { serialMap[it] ?: emptyList() }.distinct()

    fun byId(key: String, id: Long): Entry? = byId[key to id]
}
