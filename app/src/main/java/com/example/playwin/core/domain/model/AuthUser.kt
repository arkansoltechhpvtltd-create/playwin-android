package com.example.playwin.core.domain.model

/**
 * The signed-in player. `identifier` is whatever they registered with - an
 * email or a phone number - and is blank for a guest session.
 */
data class AuthUser(
    val id: String,
    val displayName: String,
    val initials: String,
    val identifier: String,
    val isAdult: Boolean,
    val isGuest: Boolean = false
)
