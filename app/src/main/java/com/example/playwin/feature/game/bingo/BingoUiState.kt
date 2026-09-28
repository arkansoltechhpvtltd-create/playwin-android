package com.example.playwin.feature.game.bingo

import com.example.playwin.core.domain.bingo.BingoRules
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.model.GameOutcome
import com.example.playwin.core.domain.model.GameSnapshot
import com.example.playwin.core.domain.repository.GameEngineRepository

enum class BingoSide { HUMAN, COMPUTER }

data class BingoUiState(
    val myCard: List<List<Int>> = BingoRules.generateCard(),
    val computerCard: List<List<Int>> = BingoRules.generateCard(),
    val called: List<Int> = emptyList(),
    /** The human calls first. */
    val turn: BingoSide = BingoSide.HUMAN,
    val lastCall: Int? = null,
    val lastCaller: BingoSide? = null,
    val winner: BingoSide? = null,
    val isResolving: Boolean = false,
    val wonCouponId: String? = null
) {
    val canCall: Boolean get() = winner == null && !isResolving && turn == BingoSide.HUMAN
}

enum class BingoCallResult { CALLER_WINS, OTHER_WINS, NO_WINNER }

/**
 * Asks the game engine - never the UI - whether calling the latest number in
 * [called] completed a line on either card. If both cards complete on the
 * same call, the caller takes it.
 */
suspend fun GameEngineRepository.resolveBingoCall(
    callerCard: List<List<Int>>,
    otherCard: List<List<Int>>,
    called: List<Int>
): BingoCallResult {
    suspend fun wins(card: List<List<Int>>) =
        resolve(GameId.BINGO, GameSnapshot.Bingo(daubed = BingoRules.marks(card, called))) == GameOutcome.WIN
    return when {
        wins(callerCard) -> BingoCallResult.CALLER_WINS
        wins(otherCard) -> BingoCallResult.OTHER_WINS
        else -> BingoCallResult.NO_WINNER
    }
}
