package dev.yiqidi.mobile

import androidx.compose.runtime.Composable

@Composable
internal fun MusicApp(store: MusicStore, ui: MusicUiState, player: PlayerState, tree: org.json.JSONObject, dark: Boolean, sharedInvite: String, consumeSharedInvite: () -> Unit) {
    QiyidiScreen(store, ui, player, tree, dark, sharedInvite, consumeSharedInvite)
}
