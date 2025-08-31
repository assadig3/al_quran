package com.hag.al_quran.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SearchRepository(app)

    data class UiState(
        val query: String = "",
        val options: SearchOptions = SearchOptions(),
        val isLoading: Boolean = false,
        val totalCount: Int = 0,
        val results: List<SearchResultItem> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // مصادر الأحداث مفصولة عن حالة الواجهة
    private val queryFlow   = MutableStateFlow("")
    private val optionsFlow = MutableStateFlow(SearchOptions())

    init {
        // اجمع (query, options) كمصدر واحد، ثم ديباونس بناءً على options.debounceMs
        viewModelScope.launch {
            combine(queryFlow, optionsFlow) { q, opts -> q to opts }
                .distinctUntilChanged()
                .collect { (q, opts) ->
                    // ديباونس يدوي بسيط بناءً على القيمة في الخيارات
                    val delayMs = opts.debounceMs
                    if (delayMs > 0) kotlinx.coroutines.delay(delayMs)

                    if (q.isBlank()) {
                        _state.update {
                            it.copy(query = q, options = opts, isLoading = false, results = emptyList(), totalCount = 0)
                        }
                        return@collect
                    }

                    // ابدأ تحميل
                    _state.update { it.copy(query = q, options = opts, isLoading = true) }

                    // نفّذ البحث والعدّ بخيط Default
                    val res = withContext(Dispatchers.Default) { repo.search(q, opts) }
                    val cnt = withContext(Dispatchers.Default) { repo.count(q, opts) }
                    _state.update {
                        it.copy(
                            query = q,
                            options = opts,
                            results = res,
                            totalCount = cnt,
                            isLoading = false
                        )
                    }
                }
        }
    }

    // setters: لا تلمس _state إلا للعرض؛ المصدر الحقيقي flows أعلاه
    fun setQuery(q: String) {
        queryFlow.value = q
        _state.update { it.copy(query = q) } // تحدّث الواجهة فورًا (اختياري)
    }

    fun setMode(mode: SearchOptions.Mode) {
        val newOpts = _state.value.options.copy(mode = mode)
        optionsFlow.value = newOpts
        _state.update { it.copy(options = newOpts) }
    }

    fun setExactTashkeel(enabled: Boolean) {
        val newOpts = _state.value.options.copy(exactTashkeel = enabled)
        optionsFlow.value = newOpts
        _state.update { it.copy(options = newOpts) }
    }
}
