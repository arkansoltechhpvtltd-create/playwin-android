package com.example.playwin.core.domain.model

/**
 * A game offered in the lobby.
 *
 * @param isFullyPlayable false for titles that only show a "coming soon" scaffold.
 */
data class Game(
    val id: GameId,
    val title: String,
    val tagline: String,
    val icon: GameIconKey,
    val isFullyPlayable: Boolean
)
