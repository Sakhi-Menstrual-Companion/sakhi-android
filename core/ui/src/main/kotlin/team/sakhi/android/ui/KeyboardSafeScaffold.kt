package team.sakhi.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared screen shell for input-heavy surfaces: a top lane, a fill body, and a
 * footer that automatically stays above the IME and navigation bar. This is
 * the Android equivalent of the repeated iOS pattern where the message/action
 * lane lifts with the keyboard instead of being hand-managed per screen.
 */
@Composable
fun KeyboardSafeScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable ColumnScope.() -> Unit = {},
    body: @Composable BoxScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        topBar()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            content = body,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
            content = footer,
        )
    }
}
