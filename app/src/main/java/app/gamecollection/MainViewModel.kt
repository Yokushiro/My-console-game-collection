package app.gamecollection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val index = PackIndex(app)
    private val catalog = CatalogDb(app)

    val loading = MutableStateFlow(true)
    val items = MutableStateFlow<List<CatalogItem>>(emptyList())
    val candidates = MutableStateFlow<List<Entry>>(emptyList())
    val status = MutableStateFlow("Загрузка базы игр…")
    val wantText = AtomicBoolean(false)

    private var lastBarcode: String? = null
    private var pendingBarcode: String? = null

    companion object {
        const val READY = "Наведите камеру на штрихкод или код издания и нажмите «Прочитать текст»"
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            index.load()
            items.value = catalog.all()
            loading.value = false
            status.value = READY
        }
    }

    fun onBarcode(code: String) {
        if (loading.value || code == lastBarcode) return
        lastBarcode = code
        pendingBarcode = code
        viewModelScope.launch(Dispatchers.IO) {
            status.value = "Штрихкод $code: ищу…"
            catalog.mappedGame(code)?.let { (key, id) ->
                index.byId(key, id)?.let {
                    candidates.value = listOf(it)
                    status.value = "Найдено по сохранённому штрихкоду $code"
                    return@launch
                }
            }
            val title = Upc.lookupTitle(code)
            if (title == null) {
                candidates.value = emptyList()
                status.value = "Штрихкод $code не найден (нет интернета или его нет в бесплатной базе). " +
                    "Нажмите «Прочитать текст» или найдите игру вручную"
                return@launch
            }
            val found = Matcher.inText(index.entries, title)
            candidates.value = found
            status.value = if (found.isEmpty()) "По штрихкоду: «$title», в базе не найдено. Найдите вручную"
            else "По штрихкоду: «$title». Выберите нужное издание"
        }
    }

    fun onOcrText(text: String) {
        if (loading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val bySerial = index.bySerials(CodeExtractor.candidates(text))
            if (bySerial.isNotEmpty()) {
                candidates.value = bySerial
                status.value = "Найдено по коду издания. Проверьте и добавьте"
                return@launch
            }
            val byTitle = Matcher.inText(index.entries, text)
            candidates.value = byTitle
            status.value = if (byTitle.isEmpty()) "Ничего не найдено. Наведите ближе на код/название или найдите вручную"
            else "Найдено по названию. Выберите регион и издание"
        }
    }

    fun search(query: String) {
        if (loading.value) return
        candidates.value = Matcher.search(index.entries, query)
        status.value = if (query.isBlank()) READY else "Результаты поиска"
    }

    fun add(e: Entry) {
        viewModelScope.launch(Dispatchers.IO) {
            catalog.add(e)
            pendingBarcode?.let { catalog.saveBarcode(it, e.key, e.id) }
            pendingBarcode = null
            items.value = catalog.all()
            candidates.value = emptyList()
            status.value = "Добавлено: ${e.displayTitle}. Наведите камеру на следующую игру"
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            catalog.delete(id)
            items.value = catalog.all()
        }
    }
}
