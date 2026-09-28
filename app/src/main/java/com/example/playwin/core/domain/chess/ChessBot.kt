package com.example.playwin.core.domain.chess

import kotlin.random.Random

/**
 * The "Play vs Computer" chess opponent - deliberately simple: it only ever
 * plays legal moves ([ChessEngine.legalMoves]) and scores each one on what
 * it does right now, looking just one reply ahead:
 *  - checkmate wins outright; stalemate is avoided;
 *  - captures score by the value of what's taken, promotions by the new queen;
 *  - checks get a bonus;
 *  - a move that leaves a piece hanging (the reply wins material, net of our
 *    recapture) or allows mate in one is penalised.
 * A little randomness breaks ties so it doesn't play identically every game.
 */
object ChessBot {

    private const val MATE = 1000.0
    private const val CHECK_BONUS = 0.6
    private const val CENTRE_BONUS = 0.15
    private val CENTRE = setOf(Square(3, 3), Square(3, 4), Square(4, 3), Square(4, 4))

    fun value(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 1
        PieceType.KNIGHT, PieceType.BISHOP -> 3
        PieceType.ROOK -> 5
        PieceType.QUEEN -> 9
        PieceType.KING -> 0
    }

    /** The move to play for the side to move, or null if it has none (checkmate or stalemate). */
    fun chooseMove(position: ChessPosition, random: Random = Random): ChessMove? =
        ChessEngine.legalMoves(position).maxByOrNull { score(position, it) + random.nextDouble(0.0, 0.1) }

    internal fun score(position: ChessPosition, move: ChessMove): Double {
        val me = position.sideToMove
        val after = ChessEngine.applyMove(position, move)
        when (ChessEngine.gameStatus(after)) {
            GameStatus.CHECKMATE -> return MATE
            GameStatus.STALEMATE -> return -MATE / 2
            GameStatus.IN_PROGRESS -> Unit
        }

        var score = capturedValue(position, move).toDouble()
        if (move.promotion != null) score += value(move.promotion) - value(PieceType.PAWN)
        if (ChessEngine.isInCheck(after, me.opposite())) score += CHECK_BONUS
        if (move.to in CENTRE && position.pieceAt(move.from)?.type in setOf(PieceType.PAWN, PieceType.KNIGHT)) {
            score += CENTRE_BONUS
        }

        val replies = ChessEngine.legalMoves(after)
        if (replies.any { reply -> allowsMate(after, reply, me) }) return score - MATE
        score -= replies.maxOfOrNull { reply -> replyGain(after, reply) }?.coerceAtLeast(0) ?: 0
        return score
    }

    /** Material the opponent's [reply] wins, net of our best immediate recapture on that square. */
    private fun replyGain(after: ChessPosition, reply: ChessMove): Int {
        val taken = capturedValue(after, reply)
        if (taken == 0) return 0
        val attacker = after.pieceAt(reply.from) ?: return taken
        val afterReply = ChessEngine.applyMove(after, reply)
        val canRecapture = ChessEngine.legalMoves(afterReply).any { it.to == reply.to }
        return if (canRecapture) taken - value(attacker.type) else taken
    }

    /** Whether the opponent's [reply] checkmates us. Only checking replies need the (costlier) full test. */
    private fun allowsMate(after: ChessPosition, reply: ChessMove, me: PieceColor): Boolean {
        val afterReply = ChessEngine.applyMove(after, reply)
        return ChessEngine.isInCheck(afterReply, me) && ChessEngine.gameStatus(afterReply) == GameStatus.CHECKMATE
    }

    private fun capturedValue(position: ChessPosition, move: ChessMove): Int = when {
        move.isEnPassant -> value(PieceType.PAWN)
        else -> position.pieceAt(move.to)?.let { value(it.type) } ?: 0
    }
}
