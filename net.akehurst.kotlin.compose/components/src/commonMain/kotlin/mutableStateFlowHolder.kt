package net.akehurst.kotlin.compose.components.flowHolder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlin.collections.component1
import kotlin.collections.component2

fun <T : Any?> mutableStateFlowHolderOf(initialValue: T) = MutableStateFlowHolder(initialValue)

class MutableStateFlowHolder<T : Any?>(initialValue: T) {
    private val _mutableStateFlow = MutableStateFlow(initialValue)
    val stateFlow = _mutableStateFlow.asStateFlow()

    val value get() = _mutableStateFlow.value

    private var _isModified = stateFlow.combine(MutableStateFlow(initialValue)) { v, i -> v != i }
    val isModified get() = _isModified

    fun update(provider: (T) -> T) {
        _mutableStateFlow.update { provider.invoke(it) }
        _isModified = stateFlow.combine(MutableStateFlow(value)) { v, i -> v != i }
    }

    @Composable
    fun collectAsState() = stateFlow.collectAsState()
}

fun <V> mutableStateFlowHolderListOf(initialValue: List<V>) = MutableStateFlowHolderList<V>(initialValue)
class MutableStateFlowHolderList<V>(initialValue: List<V>) {
    private val _listFlow = mutableStateFlowHolderOf(initialValue.map {
        mutableStateFlowHolderOf(it)
    })
    val listStateFlow = _listFlow.stateFlow

    val value get() = _listFlow.value.map { v -> v.value }

    private var _isModified = listStateFlow.combine(MutableStateFlow(initialValue)) { v, i -> v != i }
    val isModified get() = _isModified

    fun update(index: Int, provider: (V?) -> V) {
        var mutableFlow = _listFlow.value.getOrNull(index)
        if (null == mutableFlow) {
            mutableFlow = mutableStateFlowHolderOf(provider.invoke(null))
            _listFlow.update { v -> v + mutableFlow } //FIXME: this assumes idx is the next item !
        } else {
            mutableFlow.update { v -> provider.invoke(v) }
        }
        _isModified = listStateFlow.combine(MutableStateFlow(value)) { v, i -> v != i }
    }

    fun updateAll(provider: (List<V>) -> List<V>) {
        provider.invoke(value).forEachIndexed { idx, v ->
            update(idx) { v }
        }
    }

    @Composable
    fun collectAsState() = listStateFlow.collectAsState()
}

fun <K, V> mutableStateFlowHolderMapOf(initialValue: Map<K, V>) = MutableStateFlowHolderMap<K, V>(initialValue)
class MutableStateFlowHolderMap<K, V>(initialValue: Map<K, V>) {

    private val _mapStateFlow = mutableStateFlowHolderOf(initialValue.entries.associate { (k, v) ->
        Pair(k, mutableStateFlowHolderOf(v))
    })

    val mapStateFlow = _mapStateFlow.stateFlow

    val value get() = _mapStateFlow.value.entries.associate { (k, v) -> Pair(k, v.value) }

    private var _isModified = mapStateFlow.combine(MutableStateFlow(initialValue)) { v, i -> v != i }
    val isModified get() = _isModified

    operator fun get(key: K) = _mapStateFlow.value[key]

    fun update(k: K, provider: (V?) -> V) {
        var mutableFlow = _mapStateFlow.value[k]
        if (null == mutableFlow) {
            mutableFlow = mutableStateFlowHolderOf(provider.invoke(null))
            _mapStateFlow.update { v -> v + Pair(k, mutableFlow) }
        } else {
            mutableFlow.update { v -> provider.invoke(v) }
        }
        _isModified = mapStateFlow.combine(MutableStateFlow(value)) { v, i -> v != i }
    }

    fun updateAll(provider: (Map<K, V>) -> Map<K, V>) {
        provider.invoke(value).entries.forEach { (k, v) ->
            update(k) { v }
        }
    }

    @Composable
    fun collectAsState() = mapStateFlow.collectAsState()
}
