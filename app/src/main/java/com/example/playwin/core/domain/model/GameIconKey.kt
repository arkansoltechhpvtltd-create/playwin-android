package com.example.playwin.core.domain.model

/**
 * Domain-level, UI-framework-agnostic reference to an icon.
 * core.designsystem maps each key to a concrete Material icon so that
 * domain models never depend on Compose.
 */
enum class GameIconKey {
    GRID,
    BILLIARDS,
    CARROM,
    CHINESE_CHECKERS,
    SUDOKU,
    SNAKES_AND_LADDERS,
    CHESS,
    LUDO,
    CARDS
}
