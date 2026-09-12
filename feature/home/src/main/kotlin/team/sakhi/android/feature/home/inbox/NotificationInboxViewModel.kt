package team.sakhi.android.feature.home.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import team.sakhi.care.CareStore
import team.sakhi.notifications.InAppNotification
import team.sakhi.notifications.InAppNotificationStore
import team.sakhi.notifications.InboxState
import team.sakhi.notifications.NotificationRouting
import team.sakhi.notifications.SakhiNotification
import team.sakhi.session.SessionManager
import team.sakhi.sync.DataMigration

/** Where an inline "Allow / Not now" answer stands, per row. Absent means untouched. */
enum class RequestAnswerState { Sending, Failed }

/**
 * The inbox screen's state and actions. The list itself lives in the shared
 * [InAppNotificationStore] (the bell's badge reads the same one); this only adds what is
 * particular to the screen: pull-to-refresh, and answering a partner's logging request
 * from the row itself.
 */
class NotificationInboxViewModel(
    private val store: InAppNotificationStore,
    private val careStore: CareStore,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val inbox: StateFlow<InboxState> = store.state

    private val _answers = MutableStateFlow<Map<String, RequestAnswerState>>(emptyMap())
    val answers: StateFlow<Map<String, RequestAnswerState>> = _answers.asStateFlow()

    /**
     * False for a local-only (offline) account, which has no server and so no inbox. The
     * screen shows its empty state instead of a skeleton that would never resolve.
     */
    val canLoad: Boolean
        get() = sessionManager.current?.userId?.let { !DataMigration.isOfflineUserId(it) } ?: false

    init {
        // Opening the inbox is the moment she most expects it to be current. There is no
        // pull-to-refresh: in a sheet, pulling down at the top is how she dismisses it, and
        // the list is already re-read on open, on every push and on every return to the app.
        store.refresh()
    }

    fun retry() = store.refresh()

    fun markAllRead() = store.markAllRead()

    fun delete(id: String) = store.delete(id)

    /**
     * Marks the row read and returns where it leads, or null when it leads nowhere more
     * specific than the inbox itself (a feature notice). The link is built by the same
     * shared rule a tapped push uses, so the two can never disagree.
     */
    fun open(item: InAppNotification): String? {
        store.markRead(item.id)
        return NotificationRouting.deepLinkUri(item.kind)
    }

    /**
     * Her answer to a partner asking to log for her. The only place in the app she can
     * give it: before the inbox, the request arrived as a push and led nowhere she could
     * say yes or no.
     *
     * The partner is the row's actor (the server records who asked). Answering marks the
     * row read, which is also what retires its buttons, so a request is never answered
     * twice from here.
     */
    fun answer(item: InAppNotification, approved: Boolean) {
        val request = item.kind as? SakhiNotification.LogRequestReceived ?: return
        val partnerUserId = item.actorUserId ?: return
        val userId = sessionManager.current?.userId ?: return
        if (_answers.value[item.id] == RequestAnswerState.Sending) return

        _answers.update { it + (item.id to RequestAnswerState.Sending) }
        viewModelScope.launch {
            val result = runCatching {
                careStore.respondLogPermission(
                    requestId = request.requestId,
                    partnershipId = request.partnershipId,
                    partnerUserId = partnerUserId,
                    approved = approved,
                    userId = userId,
                )
            }
            if (result.isSuccess) {
                store.markRead(item.id)
                _answers.update { it - item.id }
            } else {
                _answers.update { it + (item.id to RequestAnswerState.Failed) }
            }
        }
    }
}
