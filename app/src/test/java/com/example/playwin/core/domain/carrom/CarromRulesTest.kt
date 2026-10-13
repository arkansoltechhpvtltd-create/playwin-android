package com.example.playwin.core.domain.carrom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarromRulesTest {

    private fun pocket(match: CarromMatch, vararg ids: Int): List<Coin> =
        match.coins.map { if (it.id in ids) it.copy(pocketed = true) else it }

    @Test
    fun `the first coin potted decides colors, scores, and keeps the turn`() {
        val match = CarromMatch()
        val outcome = CarromRules.resolveShot(match, pocket(match, 10), potted = listOf(10))
        assertEquals(mapOf(1 to CoinColor.BLACK, 2 to CoinColor.WHITE), outcome.after.playerColors)
        assertEquals(1, outcome.after.scores[1])
        assertEquals(1, outcome.after.currentPlayer)
    }

    // Queen cover rule. Player 1 (the human in vs-Computer) owns white, player 2 (the computer) black.
    private val colors = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK)
    private val ownCoin = mapOf(1 to 1, 2 to 10)

    /** [player] pots the queen alone; returns the match with the cover now owed. */
    private fun queenPotted(player: Int): CarromMatch {
        val match = CarromMatch(currentPlayer = player, playerColors = colors)
        val outcome = CarromRules.resolveShot(match, pocket(match, QUEEN_ID), potted = listOf(QUEEN_ID))
        assertEquals(QueenEvent.COVER_OWED, outcome.queenEvent)
        assertEquals(0, outcome.pointsGained)
        assertEquals(player, outcome.after.queenCoverOwedBy)
        assertEquals("potting the queen earns the cover shot", player, outcome.after.currentPlayer)
        return outcome.after
    }

    private fun queenPottedThenOwnCoinNextShotIsClaimed(player: Int) {
        val owed = queenPotted(player)
        val coin = ownCoin.getValue(player)
        val outcome = CarromRules.resolveShot(owed, pocket(owed, coin), potted = listOf(coin))
        assertEquals(QueenEvent.CLAIMED, outcome.queenEvent)
        assertEquals(1 + QUEEN_POINTS, outcome.pointsGained)
        assertEquals(1 + QUEEN_POINTS, outcome.after.scores[player])
        assertNull(outcome.after.queenCoverOwedBy)
        assertTrue(outcome.after.coins.first { it.id == QUEEN_ID }.pocketed)
        assertEquals(player, outcome.after.currentPlayer)
    }

    private fun queenPottedThenNoOwnCoinNextShotIsReturned(player: Int) {
        val owed = queenPotted(player)
        val outcome = CarromRules.resolveShot(owed, owed.coins, potted = emptyList())
        assertEquals(QueenEvent.RETURNED, outcome.queenEvent)
        assertEquals(0, outcome.pointsGained)
        assertEquals(0, outcome.after.scores[player])
        assertNull(outcome.after.queenCoverOwedBy)
        assertEquals(3 - player, outcome.after.currentPlayer)
        val queen = outcome.after.coins.first { it.id == QUEEN_ID }
        assertFalse(queen.pocketed)
        assertEquals(Vec2(CarromBoard.SIZE / 2f, CarromBoard.SIZE / 2f), queen.position)
        assertFalse(outcome.after.boardCleared)
    }

    private fun queenAndOwnCoinSameShotIsClaimed(player: Int) {
        val match = CarromMatch(currentPlayer = player, playerColors = colors)
        val coin = ownCoin.getValue(player)
        val outcome = CarromRules.resolveShot(match, pocket(match, QUEEN_ID, coin), potted = listOf(QUEEN_ID, coin))
        assertEquals(QueenEvent.CLAIMED, outcome.queenEvent)
        assertEquals(1 + QUEEN_POINTS, outcome.after.scores[player])
        assertNull(outcome.after.queenCoverOwedBy)
    }

    @Test fun `player - queen then own coin next shot claims the queen`() = queenPottedThenOwnCoinNextShotIsClaimed(1)
    @Test fun `computer - queen then own coin next shot claims the queen`() = queenPottedThenOwnCoinNextShotIsClaimed(2)
    @Test fun `player - queen then no own coin next shot returns it`() = queenPottedThenNoOwnCoinNextShotIsReturned(1)
    @Test fun `computer - queen then no own coin next shot returns it`() = queenPottedThenNoOwnCoinNextShotIsReturned(2)
    @Test fun `player - queen and own coin on the same shot claims the queen`() = queenAndOwnCoinSameShotIsClaimed(1)
    @Test fun `computer - queen and own coin on the same shot claims the queen`() = queenAndOwnCoinSameShotIsClaimed(2)

    @Test
    fun `potting only the opponent's coin on the cover shot returns the queen`() {
        val owed = queenPotted(1)
        val outcome = CarromRules.resolveShot(owed, pocket(owed, 10), potted = listOf(10))
        assertEquals(QueenEvent.RETURNED, outcome.queenEvent)
        assertEquals(0, outcome.after.scores[1])
    }

    @Test
    fun `fouling on the cover shot returns the queen`() {
        val owed = queenPotted(1)
        val outcome = CarromRules.resolveShot(owed, pocket(owed, STRIKER_ID, 1), potted = listOf(STRIKER_ID, 1))
        assertEquals(QueenEvent.RETURNED, outcome.queenEvent)
        assertEquals(0, outcome.after.scores[1])
    }

    @Test
    fun `timing out with a cover owed returns the queen`() {
        val passed = CarromRules.passTurn(queenPotted(1))
        assertNull(passed.queenCoverOwedBy)
        assertFalse(passed.coins.first { it.id == QUEEN_ID }.pocketed)
        assertEquals(2, passed.currentPlayer)
    }

    @Test
    fun `the returned queen goes to the centre when it is free`() {
        val owed = queenPotted(1)
        // Clear the middle so the centre spot is open.
        val cleared = owed.coins.map { if (it.id != STRIKER_ID && it.id != QUEEN_ID && it.id != 1) it.copy(pocketed = true) else it }
            .map { if (it.id == 1) it.copy(position = Vec2(100f, 100f)) else it }
        val outcome = CarromRules.resolveShot(owed.copy(coins = cleared), cleared, potted = emptyList())
        assertEquals(Vec2(CarromBoard.SIZE / 2f, CarromBoard.SIZE / 2f), outcome.after.coins.first { it.id == QUEEN_ID }.position)
    }

    @Test
    fun `the returned queen takes the nearest free spot when the centre is taken`() {
        val center = Vec2(CarromBoard.SIZE / 2f, CarromBoard.SIZE / 2f)
        val coins = listOf(
            Coin(STRIKER_ID, CarromBoard.baselineFor(1)),
            Coin(QUEEN_ID, center, pocketed = true),
            Coin(1, center)
        )
        val queen = CarromRules.respotQueen(coins).first { it.id == QUEEN_ID }
        assertFalse(queen.pocketed)
        val gap = (queen.position - center).length
        assertTrue("queen overlaps the coin at the centre", gap >= 2 * CarromBoard.COIN_RADIUS)
        assertTrue("queen was put further out than needed: $gap", gap <= 2 * CarromBoard.COIN_RADIUS + CarromBoard.COIN_RADIUS / 2f)
    }

    @Test
    fun `the queen is not an opponent coin for either side`() {
        val match = CarromMatch(currentPlayer = 2, playerColors = colors)
        val outcome = CarromRules.resolveShot(match, pocket(match, QUEEN_ID), potted = listOf(QUEEN_ID))
        assertFalse(outcome.fouled)
        assertEquals(0, outcome.after.scores[1])
        assertEquals(0, outcome.after.scores[2])
    }

    @Test
    fun `potting the striker is a foul - no points, turn passes, striker re-spotted`() {
        val match = CarromMatch(playerColors = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK))
        val outcome = CarromRules.resolveShot(match, pocket(match, STRIKER_ID, 1), potted = listOf(STRIKER_ID, 1))
        assertTrue(outcome.fouled)
        assertEquals(0, outcome.pointsGained)
        assertEquals(2, outcome.after.currentPlayer)
        val striker = outcome.after.coins.first { it.id == STRIKER_ID }
        assertFalse(striker.pocketed)
        assertEquals(CarromBoard.baselineFor(2), striker.position)
    }

    @Test
    fun `player - potting only the opponent's coin scores it for them and passes the turn`() {
        val match = CarromMatch(playerColors = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK))
        val outcome = CarromRules.resolveShot(match, pocket(match, 10), potted = listOf(10))
        assertEquals(0, outcome.pointsGained)
        assertEquals(1, outcome.opponentPointsGained)
        assertEquals(mapOf(1 to 0, 2 to 1), outcome.after.scores)
        assertEquals(2, outcome.after.currentPlayer)
    }

    @Test
    fun `computer - potting only the opponent's coin scores it for them and passes the turn`() {
        val match = CarromMatch(currentPlayer = 2, playerColors = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK))
        val outcome = CarromRules.resolveShot(match, pocket(match, 1), potted = listOf(1))
        assertEquals(mapOf(1 to 1, 2 to 0), outcome.after.scores)
        assertEquals(1, outcome.after.currentPlayer)
    }

    @Test
    fun `potting your own and their coins scores each for its owner, and keeps your turn`() {
        val match = CarromMatch(playerColors = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK))
        val outcome = CarromRules.resolveShot(match, pocket(match, 1, 2, 10), potted = listOf(1, 2, 10))
        assertEquals(mapOf(1 to 2, 2 to 1), outcome.after.scores)
        assertEquals(1, outcome.after.currentPlayer)
    }

    @Test
    fun `the first pot's other-colour coins count for the opponent`() {
        val open = CarromMatch()
        // 10 decides colours (player 1 black), so white 1 potted alongside it is player 2's.
        val outcome = CarromRules.resolveShot(open, pocket(open, 10, 1), potted = listOf(10, 1))
        assertEquals(mapOf(1 to 1, 2 to 1), outcome.after.scores)
    }

    @Test
    fun `a coin the opponent potted for you can pay your due, taking back its point`() {
        val match = CarromMatch(currentPlayer = 2, playerColors = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK))
        val gift = CarromRules.resolveShot(match, pocket(match, 1), potted = listOf(1)).after
        assertEquals(1, gift.scores[1])
        // Player 1 fouls: the coin the computer potted for them pays the due.
        val foul = CarromRules.resolveShot(gift, pocket(gift, STRIKER_ID), potted = listOf(STRIKER_ID))
        assertEquals(DueEvent.PAID_ON_FOUL, foul.dueEvent)
        assertEquals(0, foul.after.scores[1])
    }

    @Test
    fun `passing the turn moves the striker to the other baseline`() {
        val passed = CarromRules.passTurn(CarromMatch())
        assertEquals(2, passed.currentPlayer)
        assertEquals(CarromBoard.baselineFor(2), passed.coins.first { it.id == STRIKER_ID }.position)
    }

    // --- Fouls and dues. Player 1 (the human) owns white 1..9, player 2 (the computer) black 10..18. ---

    private val owned = mapOf(1 to CoinColor.WHITE, 2 to CoinColor.BLACK)
    private val firstCoin = mapOf(1 to 1, 2 to 10)
    private val secondCoin = mapOf(1 to 2, 2 to 11)

    private fun onBoard(match: CarromMatch, id: Int) = !match.coins.first { it.id == id }.pocketed

    /** [player] to shoot, having already pocketed (and scored) their [secondCoin]. */
    private fun withOnePocketed(player: Int): CarromMatch {
        val base = CarromMatch(currentPlayer = player, playerColors = owned)
        return base.copy(coins = pocket(base, secondCoin.getValue(player)), scores = mapOf(1 to 0, 2 to 0) + (player to 1))
    }

    private fun foulsPayingDueFromAnEarlierCoin(player: Int) {
        val match = withOnePocketed(player)
        val outcome = CarromRules.resolveShot(match, pocket(match, STRIKER_ID), potted = listOf(STRIKER_ID))
        assertTrue(outcome.fouled)
        assertEquals(DueEvent.PAID_ON_FOUL, outcome.dueEvent)
        assertEquals(-1, outcome.pointsGained)
        assertEquals(0, outcome.after.scores[player])
        assertTrue("the due coin is back on the board", onBoard(outcome.after, secondCoin.getValue(player)))
        assertTrue(outcome.after.duesOwed.isEmpty())
        assertEquals(3 - player, outcome.after.currentPlayer)
    }

    private fun foulsOwingDueThenPayingItWithTheNextCoin(player: Int) {
        val match = CarromMatch(currentPlayer = player, playerColors = owned)
        val foul = CarromRules.resolveShot(match, pocket(match, STRIKER_ID), potted = listOf(STRIKER_ID))
        assertEquals(DueEvent.OWED, foul.dueEvent)
        assertEquals(0, foul.pointsGained)
        assertEquals(mapOf(player to 1), foul.after.duesOwed)

        // Their next turn: the first own coin they pot pays the due instead of scoring.
        val next = foul.after.copy(currentPlayer = player)
        val coin = firstCoin.getValue(player)
        val pays = CarromRules.resolveShot(next, pocket(next, coin), potted = listOf(coin))
        assertEquals(DueEvent.PAID_FROM_POT, pays.dueEvent)
        assertEquals(0, pays.pointsGained)
        assertTrue("the coin went back to the centre", onBoard(pays.after, coin))
        assertTrue(pays.after.duesOwed.isEmpty())
        assertEquals("potting your own coin still keeps the turn", player, pays.after.currentPlayer)

        // Debt cleared: the coin after that scores again.
        val then = CarromRules.resolveShot(pays.after, pocket(pays.after, secondCoin.getValue(player)), potted = listOf(secondCoin.getValue(player)))
        assertEquals(1, then.pointsGained)
        assertNull(then.dueEvent)
    }

    private fun foulReturnsEveryCoinPottedThatShot(player: Int) {
        val match = withOnePocketed(player)
        val own = firstCoin.getValue(player)
        val theirs = firstCoin.getValue(3 - player)
        val potted = listOf(STRIKER_ID, own, theirs, QUEEN_ID)
        val outcome = CarromRules.resolveShot(match, pocket(match, *potted.toIntArray()), potted)
        for (id in listOf(own, theirs, QUEEN_ID)) assertTrue("coin $id back on the board", onBoard(outcome.after, id))
        assertEquals(QueenEvent.RETURNED, outcome.queenEvent)
        assertEquals("only the due is lost, the shot's own coin never scored", -1, outcome.pointsGained)
        val back = outcome.after.coins.filter { it.id in outcome.returnedToCentre }
        for (a in back) for (b in back) if (a.id < b.id) {
            assertTrue("coins ${a.id} and ${b.id} overlap", (a.position - b.position).length >= 2 * CarromBoard.COIN_RADIUS)
        }
    }

    @Test fun `player - a foul pays a due with an earlier coin`() = foulsPayingDueFromAnEarlierCoin(1)
    @Test fun `computer - a foul pays a due with an earlier coin`() = foulsPayingDueFromAnEarlierCoin(2)
    @Test fun `player - a due owed is paid by the next own coin`() = foulsOwingDueThenPayingItWithTheNextCoin(1)
    @Test fun `computer - a due owed is paid by the next own coin`() = foulsOwingDueThenPayingItWithTheNextCoin(2)
    @Test fun `player - a foul returns every coin potted that shot`() = foulReturnsEveryCoinPottedThatShot(1)
    @Test fun `computer - a foul returns every coin potted that shot`() = foulReturnsEveryCoinPottedThatShot(2)

    @Test
    fun `a foul decides no colors, and a due owed before colors waits for your first coin`() {
        val open = CarromMatch()
        val foul = CarromRules.resolveShot(open, pocket(open, STRIKER_ID, 10), potted = listOf(STRIKER_ID, 10))
        assertTrue("coins potted on a foul decide nothing", foul.after.playerColors.isEmpty())
        assertEquals(DueEvent.OWED, foul.dueEvent)
        assertTrue(onBoard(foul.after, 10))

        val next = foul.after.copy(currentPlayer = 1)
        val pays = CarromRules.resolveShot(next, pocket(next, 3), potted = listOf(3))
        assertEquals(CoinColor.WHITE, pays.after.playerColors[1])
        assertEquals(DueEvent.PAID_FROM_POT, pays.dueEvent)
        assertEquals(0, pays.after.scores[1])
    }

    @Test
    fun `dues stack, one coin each`() {
        var match = CarromMatch(currentPlayer = 1, playerColors = owned)
        repeat(2) {
            match = CarromRules.resolveShot(match, pocket(match, STRIKER_ID), listOf(STRIKER_ID)).after.copy(currentPlayer = 1)
        }
        assertEquals(mapOf(1 to 2), match.duesOwed)
        val outcome = CarromRules.resolveShot(match, pocket(match, 1, 2, 3), listOf(1, 2, 3))
        assertEquals(1, outcome.pointsGained)
        assertTrue(outcome.after.duesOwed.isEmpty())
    }

    // --- Winning: first to clear their coins, once the queen is covered ---

    private val allWhite = (1..9).toList()
    private val allBlack = (10..18).toList()

    /**
     * Late in a game: [player] to shoot, with only [onBoard] coins still out
     * (striker aside). The queen is down and covered unless it's in [onBoard]
     * or [queenOwedBy] is set.
     */
    private fun endgame(player: Int, onBoard: List<Int>, queenOwedBy: Int? = null): CarromMatch {
        val base = CarromMatch(currentPlayer = player, playerColors = owned, queenCoverOwedBy = queenOwedBy)
        val down = (allWhite + allBlack + QUEEN_ID).filter { it !in onBoard }
        return base.copy(coins = pocket(base, *down.toIntArray()))
    }

    private fun own(player: Int) = if (player == 1) allWhite else allBlack
    private fun theirs(player: Int) = own(3 - player)

    private fun clearingYourLastCoinWins(player: Int) {
        val last = own(player).first()
        val match = endgame(player, onBoard = listOf(last, theirs(player).first()))
        val outcome = CarromRules.resolveShot(match, pocket(match, last), listOf(last))
        assertFalse(outcome.fouled)
        assertEquals(player, CarromRules.winner(outcome.after, player))
    }

    private fun clearingYourLastCoinBeforeTheQueenIsAFoul(player: Int) {
        val last = own(player).first()
        val match = endgame(player, onBoard = listOf(last, theirs(player).first(), QUEEN_ID))
        val outcome = CarromRules.resolveShot(match, pocket(match, last), listOf(last))
        assertTrue(outcome.fouled)
        assertEquals(CarromFoul.LAST_COIN_BEFORE_QUEEN, outcome.foul)
        assertTrue("the last coin is back on the board", onBoard(outcome.after, last))
        assertEquals(3 - player, outcome.after.currentPlayer)
        assertNull(CarromRules.winner(outcome.after, player))
    }

    private fun coveringTheQueenWithYourLastCoinWins(player: Int) {
        val last = own(player).first()
        // The queen went down last shot; this shot must cover it - with the last coin.
        val match = endgame(player, onBoard = listOf(last, theirs(player).first()), queenOwedBy = player)
        val outcome = CarromRules.resolveShot(match, pocket(match, last), listOf(last))
        assertEquals(QueenEvent.CLAIMED, outcome.queenEvent)
        assertFalse(outcome.fouled)
        assertEquals(player, CarromRules.winner(outcome.after, player))
    }

    private fun pocketingTheirLastCoinHandsThemTheBoard(player: Int) {
        val theirLast = theirs(player).first()
        val match = endgame(player, onBoard = listOf(own(player).first(), theirLast))
        val outcome = CarromRules.resolveShot(match, pocket(match, theirLast), listOf(theirLast))
        assertEquals(3 - player, CarromRules.winner(outcome.after, player))
    }

    @Test fun `player - clearing your last coin wins`() = clearingYourLastCoinWins(1)
    @Test fun `computer - clearing its last coin wins`() = clearingYourLastCoinWins(2)
    @Test fun `player - your last coin before the queen is a foul`() = clearingYourLastCoinBeforeTheQueenIsAFoul(1)
    @Test fun `computer - its last coin before the queen is a foul`() = clearingYourLastCoinBeforeTheQueenIsAFoul(2)
    @Test fun `player - covering the queen with your last coin wins`() = coveringTheQueenWithYourLastCoinWins(1)
    @Test fun `computer - covering the queen with its last coin wins`() = coveringTheQueenWithYourLastCoinWins(2)
    @Test fun `player - pocketing the computer's last coin hands it the board`() = pocketingTheirLastCoinHandsThemTheBoard(1)
    @Test fun `computer - pocketing your last coin hands you the board`() = pocketingTheirLastCoinHandsThemTheBoard(2)

    @Test
    fun `pocketing the opponent's last coin before the queen is a foul too`() {
        val match = endgame(1, onBoard = listOf(1, 10, QUEEN_ID))
        val outcome = CarromRules.resolveShot(match, pocket(match, 10), listOf(10))
        assertEquals(CarromFoul.LAST_COIN_BEFORE_QUEEN, outcome.foul)
        assertTrue(onBoard(outcome.after, 10))
    }

    @Test
    fun `no winner while both sides have coins, or before colors are decided`() {
        assertNull(CarromRules.winner(endgame(1, onBoard = listOf(1, 10)), 1))
        assertNull(CarromRules.winner(CarromMatch(), 1))
    }

    @Test
    fun `a simulated shot comes to rest`() {
        val match = CarromMatch()
        val result = CarromPhysics.simulate(CarromRules.launch(match.coins, Vec2(0f, -1f), 1f))
        assertFalse(result.anyMoving)
        assertTrue(result.coins.all { it.velocity == Vec2.Zero })
    }
}
