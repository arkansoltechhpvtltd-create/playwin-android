package com.example.playwin.core.domain.chess

/**
 * Standard algebraic notation (SAN) for showing moves, e.g. `e4`, `Nf3`,
 * `exd5`, `O-O`, `e8=Q+`, `Qxf7#`. Display only: it reads positions through
 * [ChessEngine] and never changes how a game is played or decided.
 */
object ChessNotation {

    /** [move], played from [position], written in SAN. */
    fun san(position: ChessPosition, move: ChessMove): String {
        val piece = position.pieceAt(move.from) ?: return ""
        val after = ChessEngine.applyMove(position, move)
        val suffix = when {
            ChessEngine.legalMoves(after).isEmpty() && ChessEngine.isInCheck(after, after.sideToMove) -> "#"
            ChessEngine.isInCheck(after, after.sideToMove) -> "+"
            else -> ""
        }
        if (move.isCastle) return (if (move.to.col == 6) "O-O" else "O-O-O") + suffix

        val capture = move.isEnPassant || position.pieceAt(move.to) != null
        val target = squareName(move.to)
        val body = if (piece.type == PieceType.PAWN) {
            val from = if (capture) "${fileOf(move.from)}x" else ""
            val promotion = move.promotion?.let { "=${letterOf(it)}" } ?: ""
            "$from$target$promotion"
        } else {
            "${letterOf(piece.type)}${disambiguation(position, move, piece)}${if (capture) "x" else ""}$target"
        }
        return body + suffix
    }

    /** The piece [move] takes from [position], if any - the pawn beside it, for en passant. */
    fun captured(position: ChessPosition, move: ChessMove): ChessPiece? =
        if (move.isEnPassant) position.pieceAt(Square(move.from.row, move.to.col)) else position.pieceAt(move.to)

    fun squareName(square: Square): String = "${fileOf(square)}${square.row + 1}"

    /**
     * When another piece of the same kind could also reach the square: the
     * file if that tells them apart, else the rank, else both.
     */
    private fun disambiguation(position: ChessPosition, move: ChessMove, piece: ChessPiece): String {
        val rivals = ChessEngine.legalMoves(position)
            .filter { it.to == move.to && it.from != move.from && position.pieceAt(it.from) == piece }
            .map { it.from }
        if (rivals.isEmpty()) return ""
        return when {
            rivals.none { it.col == move.from.col } -> fileOf(move.from).toString()
            rivals.none { it.row == move.from.row } -> (move.from.row + 1).toString()
            else -> squareName(move.from)
        }
    }

    private fun fileOf(square: Square): Char = 'a' + square.col

    private fun letterOf(type: PieceType): String = when (type) {
        PieceType.KING -> "K"
        PieceType.QUEEN -> "Q"
        PieceType.ROOK -> "R"
        PieceType.BISHOP -> "B"
        PieceType.KNIGHT -> "N"
        PieceType.PAWN -> ""
    }
}
