package com.example.playwin.core.domain.chess

/**
 * Converts a [ChessPosition] to and from FEN (Forsyth-Edwards Notation), the
 * standard one-line text form of a chess position - used to store online
 * games in the room row. Move counters aren't tracked by [ChessPosition], so
 * they're written as "0 1" and ignored on read.
 */
object ChessFen {

    fun toFen(position: ChessPosition): String {
        val placement = (7 downTo 0).joinToString("/") { row ->
            buildString {
                var empty = 0
                for (col in 0..7) {
                    val piece = position.board[row][col]
                    if (piece == null) {
                        empty++
                    } else {
                        if (empty > 0) append(empty).also { empty = 0 }
                        append(letterFor(piece))
                    }
                }
                if (empty > 0) append(empty)
            }
        }
        val side = if (position.sideToMove == PieceColor.WHITE) "w" else "b"
        val castling = buildString {
            if (position.whiteCanCastleKingSide) append('K')
            if (position.whiteCanCastleQueenSide) append('Q')
            if (position.blackCanCastleKingSide) append('k')
            if (position.blackCanCastleQueenSide) append('q')
        }.ifEmpty { "-" }
        val enPassant = position.enPassantTarget?.let { "${'a' + it.col}${it.row + 1}" } ?: "-"
        return "$placement $side $castling $enPassant 0 1"
    }

    /** Parses [fen], or returns null if it isn't a well-formed position. */
    fun fromFen(fen: String): ChessPosition? = runCatching {
        val fields = fen.trim().split(Regex("\\s+"))
        val ranks = fields[0].split("/")
        require(ranks.size == 8)
        val board = ranks.mapIndexed { index, rank ->
            val cells = mutableListOf<ChessPiece?>()
            for (ch in rank) {
                if (ch.isDigit()) repeat(ch.digitToInt()) { cells += null } else cells += pieceFor(ch)
            }
            require(cells.size == 8)
            7 - index to cells.toList()
        }.sortedBy { it.first }.map { it.second }

        val castling = fields.getOrElse(2) { "-" }
        val enPassant = fields.getOrElse(3) { "-" }
        ChessPosition(
            board = board,
            sideToMove = if (fields[1] == "b") PieceColor.BLACK else PieceColor.WHITE,
            whiteCanCastleKingSide = 'K' in castling,
            whiteCanCastleQueenSide = 'Q' in castling,
            blackCanCastleKingSide = 'k' in castling,
            blackCanCastleQueenSide = 'q' in castling,
            enPassantTarget = if (enPassant == "-") null else Square(enPassant[1].digitToInt() - 1, enPassant[0] - 'a')
        )
    }.getOrNull()

    private fun letterFor(piece: ChessPiece): Char {
        val letter = when (piece.type) {
            PieceType.PAWN -> 'p'
            PieceType.KNIGHT -> 'n'
            PieceType.BISHOP -> 'b'
            PieceType.ROOK -> 'r'
            PieceType.QUEEN -> 'q'
            PieceType.KING -> 'k'
        }
        return if (piece.color == PieceColor.WHITE) letter.uppercaseChar() else letter
    }

    private fun pieceFor(ch: Char): ChessPiece {
        val type = when (ch.lowercaseChar()) {
            'p' -> PieceType.PAWN
            'n' -> PieceType.KNIGHT
            'b' -> PieceType.BISHOP
            'r' -> PieceType.ROOK
            'q' -> PieceType.QUEEN
            'k' -> PieceType.KING
            else -> error("Bad piece '$ch'")
        }
        return ChessPiece(type, if (ch.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK)
    }
}
