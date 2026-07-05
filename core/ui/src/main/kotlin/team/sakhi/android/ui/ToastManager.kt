package team.sakhi.android.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ToastType { SUCCESS, ERROR, WARNING, INFO }

data class ToastMessage(
    val title: String,
    val message: String,
    val type: ToastType = ToastType.INFO,
    val durationMs: Long = 4000L,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * Port of iOS `ToastManager.shared`: a single top-level queue, deduplicated by
 * `(type, title)`, one toast on screen at a time, next one drains automatically
 * on dismiss. A plain singleton object (not Koin-injected) to mirror iOS's own
 * `static let shared` shape exactly -- every call site does `ToastManager.show(...)`
 * the same way iOS does `ToastManager.shared.show(...)`.
 */
object ToastManager {
    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    private val pending = ArrayDeque<ToastMessage>()
    private var lastShownKey: String? = null

    fun show(
        title: String,
        message: String,
        type: ToastType = ToastType.INFO,
        durationMs: Long = 4000L,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        val toast = ToastMessage(title, message, type, durationMs, actionLabel, onAction)
        val key = "$type-$title"

        if (_current.value != null) {
            if (lastShownKey == key || pending.any { "${it.type}-${it.title}" == key }) return
            pending.addLast(toast)
            return
        }
        present(toast, key)
    }

    private fun present(toast: ToastMessage, key: String) {
        lastShownKey = key
        _current.value = toast
    }

    /** Called by `ToastHost` once its dismiss animation + duration elapse. */
    fun dismiss() {
        _current.value = null
        lastShownKey = null
        if (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            present(next, "${next.type}-${next.title}")
        }
    }
}
