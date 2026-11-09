package app.gamecollection

import java.text.Normalizer

/** Запись игры из офлайн-пакета платформы. */
data class Entry(
    val key: String,          // ключ платформы: ps1, n64, ...
    val label: String,        // название платформы для показа
    val id: Long,
    val title: String,
    val region: String,
    val languages: String?,
    val disc: Int?,
    val extra: String,
    val serial: String,
    val tokens: Set<String>,
    val sig: Set<String>
) {
    val displayTitle: String get() = if (disc != null) "$title (диск $disc)" else title
    val details: String
        get() = listOfNotNull(label, region.ifBlank { null }, languages, extra.ifBlank { null }, serial.ifBlank { null })
            .joinToString(" • ")
}

object Platforms {
    val ORDER = listOf(
        "ps1", "ps2", "ps3", "psp", "vita", "xbox", "xbox360", "n64", "gamecube", "wii", "wiiu",
        "gb", "gbc", "gba", "ds", "3ds", "sms", "gamegear", "megadrive", "megacd", "saturn", "dreamcast"
    )
    fun order(key: String): Int = ORDER.indexOf(key).let { if (it < 0) 999 else it }
}

object Text {
    private val diacritics = Regex("\\p{Mn}+")
    private val nonAlnum = Regex("[^a-z0-9]+")
    private val STOP = setOf("the", "of", "and", "a", "an", "in", "to")

    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(diacritics, "")
            .lowercase().replace(nonAlnum, " ").trim()

    fun tokens(s: String): List<String> = norm(s).split(' ').filter { it.isNotEmpty() }

    /** Значимые слова названия (без артиклей и предлогов). */
    fun sigTokens(s: String): Set<String> {
        val t = tokens(s)
        val sig = t.filter { it !in STOP }
        return (if (sig.isEmpty()) t else sig).toSet()
    }

    fun serialNorm(s: String): String = s.uppercase().filter { it.isLetterOrDigit() }
}

/** Достаёт из распознанного текста возможные коды издания. */
object CodeExtractor {
    // Картриджи и коробки: NUS-NO7P-EUR, AGB-AXVE-USA, NTR-ADME-EUR, CTR-P-AHXE ...
    private val cart = Regex(
        "(?:NUS|AGB|NTR|CTR|TWL|DMG|CGB|SHVC|SNS|NES|HVC)[-\\s]?(?:[A-Z][-\\s])?([A-Z0-9]{4})(?![A-Z0-9])"
    )
    private val splitter = Regex("[\\s,;:|]+")

    fun candidates(text: String): Set<String> {
        val up = text.uppercase()
        val out = LinkedHashSet<String>()
        cart.findAll(up).forEach { out += it.groupValues[1] }
        val tokens = up.split(splitter).filter { it.isNotEmpty() }
        fun ok(s: String, min: Int) = s.length in min..12 && s.any { it.isDigit() } && s.any { it.isLetter() }
        for (i in tokens.indices) {
            val a = Text.serialNorm(tokens[i])
            if (ok(a, 5)) out += a
            if (i + 1 < tokens.size) {
                val b = a + Text.serialNorm(tokens[i + 1])
                if (ok(b, 6)) out += b
            }
        }
        return out
    }
}

object Matcher {
    /** Игры, названия которых целиком присутствуют в распознанном тексте. */
    fun inText(entries: List<Entry>, text: String, limit: Int = 12): List<Entry> {
        val words = Text.tokens(text).toHashSet()
        if (words.isEmpty()) return emptyList()
        return entries.asSequence()
            .filter { it.sig.isNotEmpty() && it.sig.sumOf { t -> t.length } >= 5 && words.containsAll(it.sig) }
            .sortedWith(compareByDescending<Entry> { it.sig.size }.thenBy { it.title.length })
            .take(limit).toList()
    }

    /** Ручной поиск: все введённые слова должны начинаться как слова названия. */
    fun search(entries: List<Entry>, query: String, limit: Int = 30): List<Entry> {
        val qt = Text.tokens(query)
        if (qt.isEmpty()) return emptyList()
        return entries.asSequence()
            .filter { e -> qt.all { q -> e.tokens.any { it.startsWith(q) } } }
            .sortedBy { it.title.length }
            .take(limit).toList()
    }
}
