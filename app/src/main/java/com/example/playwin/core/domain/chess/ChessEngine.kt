package com.example.playwin.core.domain.chess

import kotlin.math.abs

/**
 * A complete, pure-Kotlin chess rules engine: legal move generation for every
 * piece (including castling, en passant, and promotion to any of
 * [PROMOTION_PIECES]), check, checkmate, stalemate, and the draws (insufficient
 * material, fifty moves, threefold repetition). A pseudo-legal move is only included in
 * [legalMoves] if playing it would not leave the mover's own king in check.
 *
 * This has no Android dependency and is exercised directly by unit tests -
 * see [com.example.playwin.core.domain.chess.ChessEngineTest] in the test source set.
 */
object ChessEngine {

    private val KNIGHT_OFFSETS = listOf(
        -2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1
    )
    private val DIAGONAL_DIRECTIONS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
    private val ORTHOGONAL_DIRECTIONS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    private val ALL_DIRECTIONS = DIAGONAL_DIRECTIONS + ORTHOGONAL_DIRECTIONS

    /** Fifty moves each, in half-moves. */
    private const val FIFTY_MOVES = 100

    /** What a pawn may become on the last rank, strongest first. */
    val PROMOTION_PIECES = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

    fun initialPosition(): ChessPosition {
        val backRank = listOf(
            PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
            PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
        )
        val board: List<List<ChessPiece?>> = (0..7).map { row ->
            (0..7).map { col ->
                when (row) {
                    0 -> ChessPiece(backRank[col], PieceColor.WHITE)
                    1 -> ChessPiece(PieceType.PAWN, PieceColor.WHITE)
                    6 -> ChessPiece(PieceType.PAWN, PieceColor.BLACK)
                    7 -> ChessPiece(backRank[col], PieceColor.BLACK)
                    else -> null
                }
            }
        }
        return ChessPosition(board = board, sideToMove = PieceColor.WHITE)
    }

    fun isInCheck(position: ChessPosition, color: PieceColor): Boolean {
        val kingSquare = findKing(position, color) ?: return false
        return isSquareAttacked(position, kingSquare, color.opposite())
    }

    /** Every fully legal move for the side to move: pseudo-legal moves filtered to those that don't self-check. */
    fun legalMoves(position: ChessPosition): List<ChessMove> {
        val pseudoMoves = mutableListOf<ChessMove>()
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = position.board[row][col] ?: continue
                if (piece.color != position.sideToMove) continue
                pseudoMoves += pseudoLegalMovesFrom(position, Square(row, col), piece)
            }
        }
        pseudoMoves += castlingMoves(position)

        return pseudoMoves.filter { move ->
            !isInCheck(applyMove(position, move), position.sideToMove)
        }
    }

    fun legalMovesFrom(position: ChessPosition, square: Square): List<ChessMove> =
        legalMoves(position).filter { it.from == square }

    /**
     * The legal move from [from] to [to] for the side to move, promoting to
     * [promotion] - which must be given for a pawn reaching the last rank and
     * must be null otherwise. Null if there's no such legal move: this is how a
     * player's chosen promotion is checked before it's played.
     */
    fun legalMove(position: ChessPosition, from: Square, to: Square, promotion: PieceType? = null): ChessMove? =
        legalMovesFrom(position, from).firstOrNull { it.to == to && it.promotion == promotion }

    /**
     * Where the game stands at [position]. [earlierPositions] are the
     * [repetitionKey]s of every position before it in this game, oldest
     * first, for the threefold-repetition draw (applied automatically, as
     * online play does, rather than waiting for a claim).
     */
    fun gameStatus(position: ChessPosition, earlierPositions: List<String> = emptyList()): GameStatus {
        // Mate (or stalemate) on the move that would otherwise draw still stands.
        if (legalMoves(position).isEmpty()) {
            return if (isInCheck(position, position.sideToMove)) GameStatus.CHECKMATE else GameStatus.STALEMATE
        }
        if (hasInsufficientMaterial(position)) return GameStatus.INSUFFICIENT_MATERIAL
        if (earlierPositions.isNotEmpty()) {
            val key = repetitionKey(position)
            if (earlierPositions.count { it == key } + 1 >= 3) return GameStatus.THREEFOLD_REPETITION
        }
        if (position.halfmoveClock >= FIFTY_MOVES) return GameStatus.FIFTY_MOVE_RULE
        return GameStatus.IN_PROGRESS
    }

    /**
     * What makes two positions "the same" for repetition (FIDE 9.2): the
     * pieces, the side to move, castling rights, and an en passant capture -
     * which only counts if one is actually playable, not merely because a
     * pawn just moved two squares.
     */
    fun repetitionKey(position: ChessPosition): String {
        val enPassantMatters = position.enPassantTarget != null && legalMoves(position).any { it.isEnPassant }
        val keyed = position.copy(enPassantTarget = position.enPassantTarget.takeIf { enPassantMatters }, halfmoveClock = 0)
        return ChessFen.toFen(keyed).split(" ").take(4).joinToString(" ")
    }

    /**
     * True when no sequence of moves can checkmate: king against king, king and
     * one knight or bishop against king, or kings with bishops all on one colour.
     */
    fun hasInsufficientMaterial(position: ChessPosition): Boolean {
        val others = position.board.flatMapIndexed { row, rank ->
            rank.mapIndexedNotNull { col, piece -> piece?.takeIf { it.type != PieceType.KING }?.let { it to (row + col) % 2 } }
        }
        if (others.any { (piece, _) -> piece.type != PieceType.KNIGHT && piece.type != PieceType.BISHOP }) return false
        if (others.size <= 1) return true
        return others.all { (piece, _) -> piece.type == PieceType.BISHOP } && others.map { it.second }.toSet().size == 1
    }

    /** Applies a move (assumed legal) and returns the resulting position, side to move flipped. */
    fun applyMove(position: ChessPosition, move: ChessMove): ChessPosition {
        val board = position.board.map { it.toMutableList() }.toMutableList()
        val moving = board[move.from.row][move.from.col] ?: error("No piece at ${move.from}")
        val resetsClock = moving.type == PieceType.PAWN || board[move.to.row][move.to.col] != null

        if (move.isEnPassant) {
            // The captured pawn sits beside the mover, not on the destination square.
            board[move.from.row][move.to.col] = null
        }

        board[move.from.row][move.from.col] = null
        board[move.to.row][move.to.col] = if (move.promotion != null) moving.copy(type = move.promotion) else moving

        if (move.isCastle) {
            val row = move.from.row
            if (move.to.col == 6) {
                board[row][5] = board[row][7]
                board[row][7] = null
            } else {
                board[row][3] = board[row][0]
                board[row][0] = null
            }
        }

        val newEnPassantTarget = if (moving.type == PieceType.PAWN && abs(move.to.row - move.from.row) == 2) {
            Square((move.from.row + move.to.row) / 2, move.from.col)
        } else {
            null
        }

        var whiteKingSide = position.whiteCanCastleKingSide
        var whiteQueenSide = position.whiteCanCastleQueenSide
        var blackKingSide = position.blackCanCastleKingSide
        var blackQueenSide = position.blackCanCastleQueenSide

        if (moving.type == PieceType.KING) {
            if (moving.color == PieceColor.WHITE) {
                whiteKingSide = false
                whiteQueenSide = false
            } else {
                blackKingSide = false
                blackQueenSide = false
            }
        }
        if (move.from == Square(0, 0) || move.to == Square(0, 0)) whiteQueenSide = false
        if (move.from == Square(0, 7) || move.to == Square(0, 7)) whiteKingSide = false
        if (move.from == Square(7, 0) || move.to == Square(7, 0)) blackQueenSide = false
        if (move.from == Square(7, 7) || move.to == Square(7, 7)) blackKingSide = false

        return position.copy(
            board = board,
            sideToMove = position.sideToMove.opposite(),
            whiteCanCastleKingSide = whiteKingSide,
            whiteCanCastleQueenSide = whiteQueenSide,
            blackCanCastleKingSide = blackKingSide,
            blackCanCastleQueenSide = blackQueenSide,
            enPassantTarget = newEnPassantTarget,
            halfmoveClock = if (resetsClock) 0 else position.halfmoveClock + 1
        )
    }

    private fun findKing(position: ChessPosition, color: PieceColor): Square? {
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = position.board[row][col]
                if (piece != null && piece.type == PieceType.KING && piece.color == color) return Square(row, col)
            }
        }
        return null
    }

    private fun isSquareAttacked(position: ChessPosition, square: Square, byColor: PieceColor): Boolean {
        val pawnRow = if (byColor == PieceColor.WHITE) square.row - 1 else square.row + 1
        for (dc in intArrayOf(-1, 1)) {
            val s = Square(pawnRow, square.col + dc)
            if (s.isOnBoard) {
                val p = position.pieceAt(s)
                if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
            }
        }

        for ((dr, dc) in KNIGHT_OFFSETS) {
            val s = Square(square.row + dr, square.col + dc)
            if (s.isOnBoard) {
                val p = position.pieceAt(s)
                if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) return true
            }
        }

        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val s = Square(square.row + dr, square.col + dc)
                if (s.isOnBoard) {
                    val p = position.pieceAt(s)
                    if (p != null && p.color == byColor && p.type == PieceType.KING) return true
                }
            }
        }

        for ((dr, dc) in DIAGONAL_DIRECTIONS) {
            var r = square.row + dr
            var c = square.col + dc
            while (Square(r, c).isOnBoard) {
                val p = position.pieceAt(Square(r, c))
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) return true
                    break
                }
                r += dr
                c += dc
            }
        }
        for ((dr, dc) in ORTHOGONAL_DIRECTIONS) {
            var r = square.row + dr
            var c = square.col + dc
            while (Square(r, c).isOnBoard) {
                val p = position.pieceAt(Square(r, c))
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) return true
                    break
                }
                r += dr
                c += dc
            }
        }

        return false
    }

    private fun pseudoLegalMovesFrom(position: ChessPosition, from: Square, piece: ChessPiece): List<ChessMove> =
        when (piece.type) {
            PieceType.PAWN -> pawnMoves(position, from, piece.color)
            PieceType.KNIGHT -> knightMoves(position, from, piece.color)
            PieceType.BISHOP -> slidingMoves(position, from, piece.color, DIAGONAL_DIRECTIONS)
            PieceType.ROOK -> slidingMoves(position, from, piece.color, ORTHOGONAL_DIRECTIONS)
            PieceType.QUEEN -> slidingMoves(position, from, piece.color, ALL_DIRECTIONS)
            PieceType.KING -> kingStepMoves(position, from, piece.color)
        }

    private fun pawnMoves(position: ChessPosition, from: Square, color: PieceColor): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        val direction = if (color == PieceColor.WHITE) 1 else -1
        val startRow = if (color == PieceColor.WHITE) 1 else 6
        val promotionRow = if (color == PieceColor.WHITE) 7 else 0

        val oneStep = Square(from.row + direction, from.col)
        if (oneStep.isOnBoard && position.pieceAt(oneStep) == null) {
            addPawnMove(moves, from, oneStep, promotionRow)
            val twoStep = Square(from.row + direction * 2, from.col)
            if (from.row == startRow && position.pieceAt(twoStep) == null) {
                moves += ChessMove(from, twoStep)
            }
        }

        for (dc in intArrayOf(-1, 1)) {
            val target = Square(from.row + direction, from.col + dc)
            if (!target.isOnBoard) continue
            val targetPiece = position.pieceAt(target)
            if (targetPiece != null && targetPiece.color != color) {
                addPawnMove(moves, from, target, promotionRow)
            } else if (targetPiece == null && position.enPassantTarget == target) {
                moves += ChessMove(from, target, isEnPassant = true)
            }
        }
        return moves
    }

    /** A pawn move; onto the last rank it's one move per piece the pawn could become. */
    private fun addPawnMove(moves: MutableList<ChessMove>, from: Square, to: Square, promotionRow: Int) {
        if (to.row == promotionRow) {
            PROMOTION_PIECES.forEach { moves += ChessMove(from, to, promotion = it) }
        } else {
            moves += ChessMove(from, to)
        }
    }

    private fun knightMoves(position: ChessPosition, from: Square, color: PieceColor): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        for ((dr, dc) in KNIGHT_OFFSETS) {
            val target = Square(from.row + dr, from.col + dc)
            if (!target.isOnBoard) continue
            val targetPiece = position.pieceAt(target)
            if (targetPiece == null || targetPiece.color != color) moves += ChessMove(from, target)
        }
        return moves
    }

    private fun slidingMoves(
        position: ChessPosition,
        from: Square,
        color: PieceColor,
        directions: List<Pair<Int, Int>>
    ): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        for ((dr, dc) in directions) {
            var r = from.row + dr
            var c = from.col + dc
            while (Square(r, c).isOnBoard) {
                val target = Square(r, c)
                val targetPiece = position.pieceAt(target)
                if (targetPiece == null) {
                    moves += ChessMove(from, target)
                } else {
                    if (targetPiece.color != color) moves += ChessMove(from, target)
                    break
                }
                r += dr
                c += dc
            }
        }
        return moves
    }

    private fun kingStepMoves(position: ChessPosition, from: Square, color: PieceColor): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val target = Square(from.row + dr, from.col + dc)
                if (!target.isOnBoard) continue
                val targetPiece = position.pieceAt(target)
                if (targetPiece == null || targetPiece.color != color) moves += ChessMove(from, target)
            }
        }
        return moves
    }

    private fun castlingMoves(position: ChessPosition): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        val color = position.sideToMove
        val row = if (color == PieceColor.WHITE) 0 else 7
        val kingSquare = Square(row, 4)
        val king = position.pieceAt(kingSquare)
        if (king == null || king.type != PieceType.KING || king.color != color) return moves

        val opponent = color.opposite()
        if (isSquareAttacked(position, kingSquare, opponent)) return moves

        val canKingSide = if (color == PieceColor.WHITE) position.whiteCanCastleKingSide else position.blackCanCastleKingSide
        val canQueenSide = if (color == PieceColor.WHITE) position.whiteCanCastleQueenSide else position.blackCanCastleQueenSide

        val kingSideRook = position.pieceAt(Square(row, 7))
        if (canKingSide &&
            position.pieceAt(Square(row, 5)) == null &&
            position.pieceAt(Square(row, 6)) == null &&
            kingSideRook != null && kingSideRook.type == PieceType.ROOK && kingSideRook.color == color &&
            !isSquareAttacked(position, Square(row, 5), opponent) &&
            !isSquareAttacked(position, Square(row, 6), opponent)
        ) {
            moves += ChessMove(kingSquare, Square(row, 6), isCastle = true)
        }

        val queenSideRook = position.pieceAt(Square(row, 0))
        if (canQueenSide &&
            position.pieceAt(Square(row, 1)) == null &&
            position.pieceAt(Square(row, 2)) == null &&
            position.pieceAt(Square(row, 3)) == null &&
            queenSideRook != null && queenSideRook.type == PieceType.ROOK && queenSideRook.color == color &&
            !isSquareAttacked(position, Square(row, 3), opponent) &&
            !isSquareAttacked(position, Square(row, 2), opponent)
        ) {
            moves += ChessMove(kingSquare, Square(row, 2), isCastle = true)
        }

        return moves
    }
}
