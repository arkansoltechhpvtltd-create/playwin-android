package com.example.playwin.feature.game.wordhunt.online

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.playwin.feature.game.BoardSlot
import com.example.playwin.core.domain.wordhunt.WordHuntBoard
import com.example.playwin.core.domain.wordhunt.WordHuntScoring
import com.example.playwin.feature.game.online.OnlineMatchEffects
import com.example.playwin.feature.game.online.OnlineMatchScreen
import com.example.playwin.feature.game.wordhunt.LetterGrid
import com.example.playwin.feature.game.wordhunt.ScoreLine
import com.example.playwin.feature.game.wordhunt.TurnBanner
import com.example.playwin.feature.game.wordhunt.WordList

/** Online head-to-head Word Hunt via random matchmaking - starts searching as soon as it opens. */
@Composable
fun WordHuntOnlineRoute(
    onBack: () -> Unit,
    onWin: (couponId: String) -> Unit,
    onPlayComputer: () -> Unit,
    viewModel: WordHuntOnlineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val secondsLeft by viewModel.secondsLeft.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    OnlineMatchEffects(state, viewModel.controller, onBack, onWin)

    OnlineMatchScreen(
        title = "Word Hunt · Online",
        state = state,
        controller = viewModel.controller,
        onBack = onBack,
        onPlayComputer = onPlayComputer,
        activeContent = { room ->
            val me = state.myPlayerNumber ?: return@OnlineMatchScreen
            val puzzle = remember(room.id) { WordHuntBoard.forRoom(room.id) }
            val stored = room.wordHuntState()
            val myWords = stored.claimsOf(me).map { it.word }.toSet()
            val theirWords = stored.claimsOf(3 - me).map { it.word }.toSet()

            TurnBanner(
                text = if (state.isMyTurn) "Find a word!" else "Opponent is hunting…",
                secondsLeft = secondsLeft,
                note = null,
                isResolving = false,
                modifier = Modifier.padding(top = 8.dp)
            )
            ScoreLine(
                myScore = WordHuntScoring.total(stored.claimsOf(me)),
                theirScore = WordHuntScoring.total(stored.claimsOf(3 - me)),
                theirLabel = "Opponent"
            )
            WordList(puzzle.words, myWords, theirWords, modifier = Modifier.padding(top = 12.dp))
            BoardSlot(modifier = Modifier.padding(top = 16.dp)) {
                LetterGrid(
                    grid = puzzle.grid,    
                    words = puzzle.words,
                    myWords = myWords,
                    theirWords = theirWords,
                    selection = selection,
                    enabled = state.isMyTurn && !state.isResolving,
                    onCellTap = viewModel::onCellTap
                )
            }
        },
        finishedBoard = { room ->
            val me = state.myPlayerNumber ?: 1
            val puzzle = remember(room.id) { WordHuntBoard.forRoom(room.id) }
            val stored = room.wordHuntState()
            ScoreLine(
                myScore = WordHuntScoring.total(stored.claimsOf(me)),
                theirScore = WordHuntScoring.total(stored.claimsOf(3 - me)),
                theirLabel = "Opponent"
            )
            BoardSlot(modifier = Modifier.padding(top = 12.dp)) {
                LetterGrid(
                    grid = puzzle.grid,    
                    words = puzzle.words,
                    myWords = stored.claimsOf(me).map { it.word }.toSet(),
                    theirWords = stored.claimsOf(3 - me).map { it.word }.toSet(),
                    selection = null,
                    enabled = false,
                    onCellTap = { _, _ -> }
                )
            }
        }
    )
}
