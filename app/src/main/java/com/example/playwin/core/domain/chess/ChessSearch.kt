package com.example.playwin.core.domain.chess

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * The computer's move search: iterative-deepening negamax with alpha-beta
 * pruning, a transposition table, null-move pruning, check extensions and a
 * capture-only quiescence search, over a tapered evaluation (material,
 * piece-square tables, mobility, king safety, pawn structure).
 *
 * [ChessEngine]'s immutable [ChessPosition] is far too slow to search
 * millions of positions, so this keeps its own mutable 64-square board with
 * make/unmake. It only *chooses* a move: [ChessBot] maps the choice back onto
 * [ChessEngine.legalMoves], so the rules engine stays the single authority on
 * what's legal. [ChessSearchPerftTest] checks this board's move generation
 * against the same reference counts as the rules engine.
 *
 * One instance per search - it isn't thread-safe and isn't reused.
 */
internal class ChessSearch(
    position: ChessPosition,
    earlierPositions: List<String>,
    private val difficulty: ChessDifficulty,
    private val random: Random
) {
    // ---- Board state ------------------------------------------------------------------------
    // Square index = row * 8 + col (a1 = 0, h8 = 63). A piece is its type (PAWN..KING) times
    // its colour: positive for White, negative for Black, 0 for empty.

    private val board = IntArray(64)
    private var side = WHITE
    private var castling = 0
    private var ep = -1
    private var epInHash = false
    private var halfmove = 0
    private var hash = 0L
    private val kingSq = IntArray(2)

    // Undo stack, one entry per move made from the root (null moves included).
    private var sp = 0
    private val undoCaptured = IntArray(STACK)
    private val undoCastling = IntArray(STACK)
    private val undoEp = IntArray(STACK)
    private val undoEpInHash = BooleanArray(STACK)
    private val undoHalfmove = IntArray(STACK)
    private val undoHash = LongArray(STACK)
    private var nullMovesOnPath = 0

    /** Hashes of the game's earlier positions, for repetition draws. */
    private val history = HashSet<Long>()

    // ---- Search state -----------------------------------------------------------------------

    private val moveBuf = Array(MAX_PLY) { IntArray(MAX_MOVES) }
    private val scoreBuf = Array(MAX_PLY) { IntArray(MAX_MOVES) }
    private val killers = IntArray(MAX_PLY * 2)
    private val historyHeuristic = IntArray(64 * 64)

    private val ttKey = LongArray(TT_SIZE)
    private val ttMove = IntArray(TT_SIZE)
    private val ttScore = IntArray(TT_SIZE)
    private val ttDepth = ByteArray(TT_SIZE)
    private val ttFlag = ByteArray(TT_SIZE)

    private var nodes = 0L
    private var deadline = Long.MAX_VALUE
    private var aborted = false
    private var rootBest = 0

    // Evaluation scratch, reused to avoid allocating per node.
    private val pawnFiles = Array(2) { IntArray(8) }

    init {
        for (row in 0..7) for (col in 0..7) {
            val piece = position.board[row][col] ?: continue
            val code = typeCode(piece.type) * if (piece.color == PieceColor.WHITE) WHITE else BLACK
            board[row * 8 + col] = code
            if (piece.type == PieceType.KING) kingSq[colorIndex(code)] = row * 8 + col
        }
        side = if (position.sideToMove == PieceColor.WHITE) WHITE else BLACK
        castling = (if (position.whiteCanCastleKingSide) WK else 0) or (if (position.whiteCanCastleQueenSide) WQ else 0) or
            (if (position.blackCanCastleKingSide) BK else 0) or (if (position.blackCanCastleQueenSide) BQ else 0)
        ep = position.enPassantTarget?.let { it.row * 8 + it.col } ?: -1
        halfmove = position.halfmoveClock
        epInHash = ep >= 0 && epCapturable(board, ep, side)
        hash = hashOf(board, side, castling, ep, epInHash)
        earlierPositions.forEach { key -> ChessFen.fromFen(key)?.let { history += hashOf(it) } }
    }

    // ---- Root -------------------------------------------------------------------------------

    /** The chosen move as from/to/promotion, or null with no legal move. Searches for at most about [budgetMillis]. */
    fun bestMove(budgetMillis: Long): ChessMove? {
        val start = System.nanoTime()
        deadline = start + budgetMillis * 1_000_000
        val rootCount = generateLegal(0)
        if (rootCount == 0) return null
        var best = moveBuf[0][0]
        var completedDepth = 0

        for (depth in 1..difficulty.maxDepth) {
            rootBest = 0
            val score = negamax(depth, -INF, INF, 0, false)
            if (aborted) {
                // A move that beat the previous best before time ran out is still the better move.
                if (rootBest != 0) best = rootBest
                break
            }
            best = rootBest
            completedDepth = depth
            if (abs(score) >= MATE_BOUND) break
            // Another iteration takes several times as long as this one: don't start what can't finish.
            if (System.nanoTime() - start > (deadline - start) / 2) break
        }

        if (difficulty.randomMarginCp > 0 && completedDepth > 0) {
            pickNearBest(completedDepth)?.let { best = it }
        }
        return toChessMove(best)
    }

    /**
     * For an easier opponent: scores every root move exactly and picks at
     * random among those within [ChessDifficulty.randomMarginCp] of the best,
     * so it varies its play and makes small inaccuracies - but never one
     * that costs a piece. It never passes up a mate, or a capture or
     * promotion that's as good as its best move.
     */
    private fun pickNearBest(depth: Int): Int? {
        val count = generateLegal(0)
        val moves = moveBuf[0].copyOf(count)
        val scores = IntArray(count)
        for (i in 0 until count) {
            make(moves[i])
            scores[i] = -negamax(depth - 1, -INF, INF, 1, true)
            unmake(moves[i])
            if (aborted) return null
        }
        val top = scores.max()
        if (abs(top) >= MATE_BOUND) return moves[scores.indexOfFirst { it == top }]
        val candidates = moves.indices.filter { scores[it] >= top - difficulty.randomMarginCp }
        // A safe capture or promotion among them is always taken: it shouldn't dither over free material.
        candidates.filter { !isQuiet(moves[it]) }.maxByOrNull { scores[it] }?.let { return moves[it] }
        return moves[candidates[random.nextInt(candidates.size)]]
    }

    private fun toChessMove(move: Int): ChessMove {
        val from = move and 63
        val to = (move shr 6) and 63
        val promo = (move shr 12) and 7
        return ChessMove(
            from = Square(from shr 3, from and 7),
            to = Square(to shr 3, to and 7),
            promotion = if (promo == 0) null else pieceType(promo),
            isCastle = move and FLAG_CASTLE != 0,
            isEnPassant = move and FLAG_EP != 0
        )
    }

    // ---- Search -----------------------------------------------------------------------------

    private fun negamax(depthIn: Int, alphaIn: Int, beta: Int, ply: Int, allowNull: Boolean): Int {
        if ((++nodes and 1023L) == 0L && System.nanoTime() > deadline) aborted = true
        if (aborted) return 0

        if (ply > 0) {
            if (halfmove >= 100 || isRepetition()) return DRAW
            if (ply >= MAX_PLY - 1) return evaluate()
        }

        val inCheck = isAttacked(kingSq[colorIndex(side)], -side)
        // Look one ply further out of check, so a forcing line isn't cut off just before it resolves.
        val depth = if (inCheck && ply < MAX_PLY / 2) depthIn + 1 else depthIn
        if (depth <= 0) return quiesce(alphaIn, beta, ply)

        var alpha = alphaIn
        val slot = (hash and TT_MASK).toInt()
        var hashMove = 0
        if (ttKey[slot] == hash) {
            hashMove = ttMove[slot]
            if (ply > 0 && ttDepth[slot] >= depth) {
                val stored = fromTt(ttScore[slot], ply)
                when (ttFlag[slot]) {
                    EXACT -> return stored
                    LOWER -> if (stored >= beta) return stored
                    UPPER -> if (stored <= alpha) return stored
                }
            }
        }

        // Null move: if passing still leaves us at or above beta, a real move surely will.
        if (allowNull && !inCheck && depth >= 3 && ply > 0 && abs(beta) < MATE_BOUND && hasPieces(side)) {
            makeNull()
            val score = -negamax(depth - 3, -beta, -beta + 1, ply + 1, false)
            unmakeNull()
            if (aborted) return 0
            if (score >= beta) return beta
        }

        val count = generateLegal(ply)
        if (count == 0) return if (inCheck) -MATE + ply else DRAW
        scoreMoves(ply, count, hashMove)

        var best = -INF
        var bestMove = 0
        for (i in 0 until count) {
            val move = pickNext(ply, i, count)
            make(move)
            var score: Int
            if (i == 0) {
                score = -negamax(depth - 1, -beta, -alpha, ply + 1, true)
            } else {
                // Principal variation search: prove the move is no better with a null window first.
                score = -negamax(depth - 1, -alpha - 1, -alpha, ply + 1, true)
                if (score > alpha && score < beta) score = -negamax(depth - 1, -beta, -alpha, ply + 1, true)
            }
            unmake(move)
            if (aborted) return 0

            if (score > best) {
                best = score
                bestMove = move
                if (score > alpha) {
                    alpha = score
                    if (ply == 0) rootBest = move
                    if (score >= beta) {
                        if (isQuiet(move)) {
                            val key = move and MOVE_MASK
                            if (killers[ply * 2] != key) {
                                killers[ply * 2 + 1] = killers[ply * 2]
                                killers[ply * 2] = key
                            }
                            val h = (move and 63) * 64 + ((move shr 6) and 63)
                            historyHeuristic[h] = min(historyHeuristic[h] + depth * depth, HISTORY_CAP)
                        }
                        break
                    }
                }
            }
        }

        val flag = when {
            best >= beta -> LOWER
            best > alphaIn -> EXACT
            else -> UPPER
        }
        ttKey[slot] = hash
        ttMove[slot] = bestMove and MOVE_MASK
        ttScore[slot] = toTt(best, ply)
        ttDepth[slot] = depth.toByte()
        ttFlag[slot] = flag
        return best
    }

    /**
     * Quiescence: past the nominal depth, keep resolving captures (and queen
     * promotions) until the position is quiet, so the evaluation never lands
     * in the middle of an exchange. In check, every evasion is searched, so a
     * mate at the horizon is still seen.
     */
    private fun quiesce(alphaIn: Int, beta: Int, ply: Int): Int {
        if ((++nodes and 1023L) == 0L && System.nanoTime() > deadline) aborted = true
        if (aborted) return 0
        if (ply >= MAX_PLY - 1) return evaluate()

        var alpha = alphaIn
        val inCheck = isAttacked(kingSq[colorIndex(side)], -side)
        val count: Int
        var standPat = -INF
        if (inCheck) {
            count = generateLegal(ply)
            if (count == 0) return -MATE + ply
            scoreMoves(ply, count, 0)
        } else {
            standPat = evaluate()
            if (standPat >= beta) return standPat
            if (standPat > alpha) alpha = standPat
            count = generatePseudo(ply, capturesOnly = true)
            scoreMoves(ply, count, 0)
        }

        var best = standPat
        val us = side
        for (i in 0 until count) {
            val move = pickNext(ply, i, count)
            if (!inCheck) {
                // Delta pruning: even winning this piece outright can't lift us to alpha.
                val gain = VALUE[capturedType(move)] + if ((move shr 12) and 7 != 0) VALUE[QUEEN] - VALUE[PAWN] else 0
                if (standPat + gain + DELTA_MARGIN < alpha) continue
            }
            make(move)
            if (!inCheck && isAttacked(kingSq[colorIndex(us)], -us)) {
                unmake(move)
                continue
            }
            val score = -quiesce(-beta, -alpha, ply + 1)
            unmake(move)
            if (aborted) return 0
            if (score > best) {
                best = score
                if (score > alpha) {
                    alpha = score
                    if (score >= beta) break
                }
            }
        }
        return best
    }

    private fun isRepetition(): Boolean {
        // Positions can only repeat back to the last capture or pawn move.
        var i = sp - 2
        val limit = sp - halfmove
        while (i >= 0 && i >= limit) {
            if (undoHash[i] == hash) return true
            i -= 2
        }
        return nullMovesOnPath == 0 && hash in history
    }

    private fun hasPieces(color: Int): Boolean {
        for (sq in 0..63) {
            val p = board[sq] * color
            if (p in KNIGHT..QUEEN) return true
        }
        return false
    }

    private fun isQuiet(move: Int) = capturedType(move) == 0 && (move shr 12) and 7 == 0

    /** The type of piece [move] captures, for the side to move in the current position; 0 for none. */
    private fun capturedType(move: Int): Int =
        if (move and FLAG_EP != 0) PAWN else abs(board[(move shr 6) and 63])

    private fun scoreMoves(ply: Int, count: Int, hashMove: Int) {
        val moves = moveBuf[ply]
        val scores = scoreBuf[ply]
        for (i in 0 until count) {
            val move = moves[i]
            val key = move and MOVE_MASK
            val victim = capturedType(move)
            val promo = (move shr 12) and 7
            scores[i] = when {
                key == hashMove && hashMove != 0 -> 10_000_000
                victim != 0 -> 1_000_000 + VALUE[victim] * 16 - abs(board[move and 63]) + if (promo == QUEEN) 900 else 0
                promo == QUEEN -> 950_000
                key == killers[ply * 2] -> 900_000
                key == killers[ply * 2 + 1] -> 890_000
                move and FLAG_CHECK != 0 -> 800_000
                promo != 0 -> -100_000 // under-promotions last
                else -> historyHeuristic[(move and 63) * 64 + ((move shr 6) and 63)] +
                    if (ply == 0) random.nextInt(ROOT_JITTER) else 0
            }
        }
    }

    /** Selection sort, one step at a time: most moves are never reached after a cutoff. */
    private fun pickNext(ply: Int, index: Int, count: Int): Int {
        val moves = moveBuf[ply]
        val scores = scoreBuf[ply]
        var bestIndex = index
        for (j in index + 1 until count) if (scores[j] > scores[bestIndex]) bestIndex = j
        if (bestIndex != index) {
            val m = moves[index]; moves[index] = moves[bestIndex]; moves[bestIndex] = m
            val s = scores[index]; scores[index] = scores[bestIndex]; scores[bestIndex] = s
        }
        return moves[index]
    }

    private fun toTt(score: Int, ply: Int) = when {
        score >= MATE_BOUND -> score + ply
        score <= -MATE_BOUND -> score - ply
        else -> score
    }

    private fun fromTt(score: Int, ply: Int) = when {
        score >= MATE_BOUND -> score - ply
        score <= -MATE_BOUND -> score + ply
        else -> score
    }

    // ---- Move generation --------------------------------------------------------------------

    /** Fully legal moves into moveBuf[ply], each flagged [FLAG_CHECK] if it gives check. */
    internal fun generateLegal(ply: Int): Int {
        val count = generatePseudo(ply, capturesOnly = false)
        val moves = moveBuf[ply]
        val us = side
        var legal = 0
        for (i in 0 until count) {
            val move = moves[i]
            make(move)
            if (!isAttacked(kingSq[colorIndex(us)], -us)) {
                val checks = isAttacked(kingSq[colorIndex(-us)], us)
                moves[legal++] = if (checks) move or FLAG_CHECK else move
            }
            unmake(move)
        }
        return legal
    }

    /** Pseudo-legal moves (may leave the king in check). [capturesOnly] keeps captures and queen promotions. */
    private fun generatePseudo(ply: Int, capturesOnly: Boolean): Int {
        val moves = moveBuf[ply]
        var n = 0
        val us = side
        for (from in 0..63) {
            val piece = board[from] * us
            if (piece <= 0) continue
            val row = from shr 3
            val col = from and 7
            when (piece) {
                PAWN -> {
                    val dir = if (us == WHITE) 8 else -8
                    val promoRow = if (us == WHITE) 7 else 0
                    val startRow = if (us == WHITE) 1 else 6
                    val one = from + dir
                    val promotes = (one shr 3) == promoRow
                    if (board[one] == 0) {
                        if (promotes) {
                            moves[n++] = from or (one shl 6) or (QUEEN shl 12)
                            if (!capturesOnly) for (p in UNDER_PROMOTIONS) moves[n++] = from or (one shl 6) or (p shl 12)
                        } else if (!capturesOnly) {
                            moves[n++] = from or (one shl 6)
                            if (row == startRow && board[one + dir] == 0) moves[n++] = from or ((one + dir) shl 6) or FLAG_DOUBLE
                        }
                    }
                    for (dc in PAWN_SIDES) {
                        val c = col + dc
                        if (c < 0 || c > 7) continue
                        val to = one + dc
                        val target = board[to]
                        if (target * us < 0) {
                            if (promotes) {
                                moves[n++] = from or (to shl 6) or (QUEEN shl 12)
                                if (!capturesOnly) for (p in UNDER_PROMOTIONS) moves[n++] = from or (to shl 6) or (p shl 12)
                            } else {
                                moves[n++] = from or (to shl 6)
                            }
                        } else if (target == 0 && to == ep) {
                            moves[n++] = from or (to shl 6) or FLAG_EP
                        }
                    }
                }
                KNIGHT, KING -> {
                    val targets = if (piece == KNIGHT) KNIGHT_TARGETS[from] else KING_TARGETS[from]
                    for (to in targets) {
                        val target = board[to] * us
                        if (target > 0 || (capturesOnly && target == 0)) continue
                        moves[n++] = from or (to shl 6)
                    }
                    if (piece == KING && !capturesOnly) n = addCastling(moves, n, from)
                }
                else -> {
                    val firstDir = if (piece == BISHOP) 4 else 0
                    val lastDir = if (piece == ROOK) 3 else 7
                    for (d in firstDir..lastDir) {
                        for (to in RAYS[from][d]) {
                            val target = board[to] * us
                            if (target > 0) break
                            if (target < 0) {
                                moves[n++] = from or (to shl 6)
                                break
                            }
                            if (!capturesOnly) moves[n++] = from or (to shl 6)
                        }
                    }
                }
            }
        }
        return n
    }

    private fun addCastling(moves: IntArray, start: Int, from: Int): Int {
        var n = start
        val us = side
        val home = if (us == WHITE) 4 else 60
        if (from != home) return n
        val kingSide = if (us == WHITE) WK else BK
        val queenSide = if (us == WHITE) WQ else BQ
        if (castling and (kingSide or queenSide) == 0 || isAttacked(home, -us)) return n
        if (castling and kingSide != 0 && board[home + 1] == 0 && board[home + 2] == 0 && board[home + 3] == ROOK * us &&
            !isAttacked(home + 1, -us) && !isAttacked(home + 2, -us)
        ) {
            moves[n++] = home or ((home + 2) shl 6) or FLAG_CASTLE
        }
        if (castling and queenSide != 0 && board[home - 1] == 0 && board[home - 2] == 0 && board[home - 3] == 0 &&
            board[home - 4] == ROOK * us && !isAttacked(home - 1, -us) && !isAttacked(home - 2, -us)
        ) {
            moves[n++] = home or ((home - 2) shl 6) or FLAG_CASTLE
        }
        return n
    }

    /** Whether [sq] is attacked by any piece of colour [by]. */
    private fun isAttacked(sq: Int, by: Int): Boolean {
        val row = sq shr 3
        val col = sq and 7
        val pawnRow = row - by // a White pawn attacks from the rank below, a Black one from above
        if (pawnRow in 0..7) {
            if (col > 0 && board[pawnRow * 8 + col - 1] == PAWN * by) return true
            if (col < 7 && board[pawnRow * 8 + col + 1] == PAWN * by) return true
        }
        for (s in KNIGHT_TARGETS[sq]) if (board[s] == KNIGHT * by) return true
        for (s in KING_TARGETS[sq]) if (board[s] == KING * by) return true
        val rays = RAYS[sq]
        for (d in 0..7) {
            for (s in rays[d]) {
                val p = board[s]
                if (p == 0) continue
                if (p * by > 0) {
                    val type = p * by
                    if (type == QUEEN || (d < 4 && type == ROOK) || (d >= 4 && type == BISHOP)) return true
                }
                break
            }
        }
        return false
    }

    // ---- Make / unmake ----------------------------------------------------------------------

    private fun make(move: Int) {
        val from = move and 63
        val to = (move shr 6) and 63
        val promo = (move shr 12) and 7
        val piece = board[from]
        val us = side

        undoCastling[sp] = castling
        undoEp[sp] = ep
        undoEpInHash[sp] = epInHash
        undoHalfmove[sp] = halfmove
        undoHash[sp] = hash

        var captured: Int
        if (move and FLAG_EP != 0) {
            val capSq = (from and 56) or (to and 7)
            captured = board[capSq]
            board[capSq] = 0
            hash = hash xor pieceKey(captured, capSq)
        } else {
            captured = board[to]
            if (captured != 0) hash = hash xor pieceKey(captured, to)
        }
        undoCaptured[sp] = captured

        board[from] = 0
        val placed = if (promo != 0) promo * us else piece
        board[to] = placed
        hash = hash xor pieceKey(piece, from) xor pieceKey(placed, to)

        if (move and FLAG_CASTLE != 0) {
            val rookFrom = if (to > from) from + 3 else from - 4
            val rookTo = if (to > from) from + 1 else from - 1
            val rook = board[rookFrom]
            board[rookFrom] = 0
            board[rookTo] = rook
            hash = hash xor pieceKey(rook, rookFrom) xor pieceKey(rook, rookTo)
        }
        if (piece * us == KING) kingSq[colorIndex(us)] = to

        hash = hash xor CASTLE_KEYS[castling]
        castling = castling and CASTLE_MASK[from] and CASTLE_MASK[to]
        hash = hash xor CASTLE_KEYS[castling]

        if (epInHash) hash = hash xor EP_KEYS[ep and 7]
        ep = -1
        epInHash = false
        if (move and FLAG_DOUBLE != 0) {
            ep = (from + to) / 2
            epInHash = epCapturable(board, ep, -us)
            if (epInHash) hash = hash xor EP_KEYS[ep and 7]
        }

        halfmove = if (piece * us == PAWN || captured != 0) 0 else halfmove + 1
        side = -us
        hash = hash xor SIDE_KEY
        sp++
    }

    private fun unmake(move: Int) {
        sp--
        val from = move and 63
        val to = (move shr 6) and 63
        val promo = (move shr 12) and 7
        side = -side
        val us = side
        val placed = board[to]
        val piece = if (promo != 0) PAWN * us else placed
        board[from] = piece
        board[to] = 0
        val captured = undoCaptured[sp]
        if (move and FLAG_EP != 0) board[(from and 56) or (to and 7)] = captured else board[to] = captured

        if (move and FLAG_CASTLE != 0) {
            val rookFrom = if (to > from) from + 3 else from - 4
            val rookTo = if (to > from) from + 1 else from - 1
            board[rookFrom] = board[rookTo]
            board[rookTo] = 0
        }
        if (piece * us == KING) kingSq[colorIndex(us)] = from

        castling = undoCastling[sp]
        ep = undoEp[sp]
        epInHash = undoEpInHash[sp]
        halfmove = undoHalfmove[sp]
        hash = undoHash[sp]
    }

    private fun makeNull() {
        undoEp[sp] = ep
        undoEpInHash[sp] = epInHash
        undoHalfmove[sp] = halfmove
        undoHash[sp] = hash
        if (epInHash) hash = hash xor EP_KEYS[ep and 7]
        ep = -1
        epInHash = false
        halfmove = 0
        side = -side
        hash = hash xor SIDE_KEY
        nullMovesOnPath++
        sp++
    }

    private fun unmakeNull() {
        sp--
        nullMovesOnPath--
        side = -side
        ep = undoEp[sp]
        epInHash = undoEpInHash[sp]
        halfmove = undoHalfmove[sp]
        hash = undoHash[sp]
    }

    /** Leaf count to [depth] using this board's own generator - see [ChessSearchPerftTest]. */
    internal fun perft(depth: Int, ply: Int = 0): Long {
        val count = generateLegal(ply)
        if (depth == 1) return count.toLong()
        var total = 0L
        for (i in 0 until count) {
            val move = moveBuf[ply][i]
            make(move)
            total += perft(depth - 1, ply + 1)
            unmake(move)
        }
        return total
    }

    // ---- Evaluation -------------------------------------------------------------------------

    /** The position's value in centipawns for the side to move. */
    private fun evaluate(): Int {
        val files = pawnFiles
        files[0].fill(0)
        files[1].fill(0)
        var phase = 0
        var minors = 0
        var majorsOrPawns = 0
        var bishopsWhite = 0
        var bishopsBlack = 0
        for (sq in 0..63) {
            val p = board[sq]
            when (abs(p)) {
                0, KING -> {}
                PAWN -> { files[colorIndex(p)][sq and 7]++; majorsOrPawns++ }
                KNIGHT -> { phase += 1; minors++ }
                BISHOP -> { phase += 1; minors++; if (p > 0) bishopsWhite++ else bishopsBlack++ }
                ROOK -> { phase += 2; majorsOrPawns++ }
                QUEEN -> { phase += 4; majorsOrPawns++ }
            }
        }
        // King and at most one minor piece against a bare king: nobody can win.
        if (majorsOrPawns == 0 && minors <= 1) return DRAW
        phase = min(phase, 24)

        var mg = 0
        var eg = 0
        var material = 0
        val full = difficulty.fullEvaluation
        var kingAttackWhite = 0 // pressure White's pieces put on Black's king zone
        var kingAttackBlack = 0
        val whiteKing = kingSq[0]
        val blackKing = kingSq[1]

        for (sq in 0..63) {
            val p = board[sq]
            if (p == 0) continue
            val color = if (p > 0) WHITE else BLACK
            val type = p * color
            val idx = if (color == WHITE) (7 - (sq shr 3)) * 8 + (sq and 7) else sq
            var pieceMg = VALUE[type]
            var pieceEg = VALUE[type]
            if (type != KING) material += VALUE[type] * color
            when (type) {
                PAWN -> {
                    pieceMg += PST_PAWN[idx]
                    pieceEg += PST_PAWN[idx]
                    val ci = colorIndex(color)
                    val col = sq and 7
                    if (full) {
                        if (files[ci][col] > 1) { pieceMg -= 12; pieceEg -= 18 }
                        val left = col > 0 && files[ci][col - 1] > 0
                        val right = col < 7 && files[ci][col + 1] > 0
                        if (!left && !right) { pieceMg -= 12; pieceEg -= 12 }
                    }
                    if (isPassed(sq, color)) {
                        val rank = if (color == WHITE) sq shr 3 else 7 - (sq shr 3)
                        pieceMg += PASSED_MG[rank]
                        pieceEg += PASSED_EG[rank]
                    }
                }
                KNIGHT -> { pieceMg += PST_KNIGHT[idx]; pieceEg += PST_KNIGHT[idx] }
                BISHOP -> { pieceMg += PST_BISHOP[idx]; pieceEg += PST_BISHOP[idx] }
                ROOK -> {
                    pieceMg += PST_ROOK[idx]; pieceEg += PST_ROOK[idx]
                    if (full) {
                        val col = sq and 7
                        val own = files[colorIndex(color)][col]
                        val theirs = files[colorIndex(-color)][col]
                        if (own == 0) {
                            val bonus = if (theirs == 0) 20 else 10
                            pieceMg += bonus; pieceEg += bonus / 2
                        }
                    }
                }
                QUEEN -> { pieceMg += PST_QUEEN[idx]; pieceEg += PST_QUEEN[idx] }
                KING -> { pieceMg += PST_KING_MG[idx]; pieceEg += PST_KING_EG[idx] }
            }
            if (full && type in KNIGHT..QUEEN) {
                // Mobility, and how many squares round the enemy king this piece hits.
                val enemyKing = if (color == WHITE) blackKing else whiteKing
                var mobility = 0
                var zoneHits = 0
                if (type == KNIGHT) {
                    for (to in KNIGHT_TARGETS[sq]) {
                        if (board[to] * color <= 0) mobility++
                        if (KING_DISTANCE[to * 64 + enemyKing] <= 1) zoneHits++
                    }
                } else {
                    val firstDir = if (type == BISHOP) 4 else 0
                    val lastDir = if (type == ROOK) 3 else 7
                    for (d in firstDir..lastDir) {
                        for (to in RAYS[sq][d]) {
                            val target = board[to]
                            if (target * color <= 0) mobility++
                            if (KING_DISTANCE[to * 64 + enemyKing] <= 1) zoneHits++
                            if (target != 0) break
                        }
                    }
                }
                val m = (mobility - MOBILITY_BASE[type]) * MOBILITY_WEIGHT[type]
                pieceMg += m
                pieceEg += m
                if (zoneHits > 0) {
                    val units = zoneHits * ATTACK_WEIGHT[type]
                    if (color == WHITE) kingAttackWhite += units else kingAttackBlack += units
                }
            }
            mg += pieceMg * color
            eg += pieceEg * color
        }

        if (bishopsWhite >= 2) { mg += 30; eg += 40 }
        if (bishopsBlack >= 2) { mg -= 30; eg -= 40 }

        if (full) {
            // King safety only matters with the queens and some pieces still on.
            mg -= min(kingAttackBlack * kingAttackBlack / 4, 400) - pawnShield(whiteKing, WHITE)
            mg += min(kingAttackWhite * kingAttackWhite / 4, 400) - pawnShield(blackKing, BLACK)
        }

        // Ahead on material in an endgame: herd the lone king to the edge, bring ours close.
        if (abs(material) >= 300 && phase <= 8) {
            val winnerKing = if (material > 0) whiteKing else blackKing
            val loserKing = if (material > 0) blackKing else whiteKing
            val mopUp = CENTER_DISTANCE[loserKing] * 10 + (14 - manhattan(winnerKing, loserKing)) * 4
            eg += if (material > 0) mopUp else -mopUp
        }

        val score = (mg * phase + eg * (24 - phase)) / 24
        return score * side + TEMPO
    }

    /** Bonus (or, with pawns missing, penalty) for the pawns sheltering a king on its back two ranks. */
    private fun pawnShield(king: Int, color: Int): Int {
        val row = king shr 3
        val col = king and 7
        val relRow = if (color == WHITE) row else 7 - row
        if (relRow > 1) return -20 // a king wandering up the board in the middlegame
        var score = 0
        for (c in max(0, col - 1)..min(7, col + 1)) {
            val r1 = row + color
            val r2 = row + 2 * color
            score += when {
                board[r1 * 8 + c] == PAWN * color -> 12
                r2 in 0..7 && board[r2 * 8 + c] == PAWN * color -> 6
                else -> -12
            }
            if (pawnFiles[colorIndex(color)][c] == 0) score -= 10
        }
        return score
    }

    private fun isPassed(sq: Int, color: Int): Boolean {
        val row = sq shr 3
        val col = sq and 7
        for (c in max(0, col - 1)..min(7, col + 1)) {
            var r = row + color
            while (r in 0..7) {
                if (board[r * 8 + c] == -PAWN * color) return false
                r += color
            }
        }
        return true
    }

    companion object {
        const val WHITE = 1
        const val BLACK = -1

        const val PAWN = 1
        const val KNIGHT = 2
        const val BISHOP = 3
        const val ROOK = 4
        const val QUEEN = 5
        const val KING = 6

        private const val WK = 1
        private const val WQ = 2
        private const val BK = 4
        private const val BQ = 8

        private const val FLAG_EP = 1 shl 15
        private const val FLAG_CASTLE = 1 shl 16
        private const val FLAG_DOUBLE = 1 shl 17
        private const val FLAG_CHECK = 1 shl 18
        /** A move's identity, without [FLAG_CHECK] (which depends on the position it's played in). */
        private const val MOVE_MASK = FLAG_CHECK - 1

        private const val MAX_PLY = 96
        private const val STACK = MAX_PLY + 8
        private const val MAX_MOVES = 256
        const val INF = 1_000_000
        const val MATE = 100_000
        const val MATE_BOUND = MATE - 1_000
        private const val DRAW = 0
        private const val TEMPO = 10
        private const val DELTA_MARGIN = 200
        private const val HISTORY_CAP = 700_000
        private const val ROOT_JITTER = 8

        private const val TT_BITS = 17
        private const val TT_SIZE = 1 shl TT_BITS
        private const val TT_MASK = (TT_SIZE - 1).toLong()
        private const val EXACT: Byte = 0
        private const val LOWER: Byte = 1
        private const val UPPER: Byte = 2

        /** Material in centipawns: P=1, N=3, B=3, R=5, Q=9. Indexed by type; the king's is never counted. */
        private val VALUE = intArrayOf(0, 100, 300, 300, 500, 900, 0)
        private val MOBILITY_BASE = intArrayOf(0, 0, 4, 6, 6, 12, 0)
        private val MOBILITY_WEIGHT = intArrayOf(0, 0, 4, 4, 2, 1, 0)
        private val ATTACK_WEIGHT = intArrayOf(0, 0, 2, 2, 3, 5, 0)
        private val PASSED_MG = intArrayOf(0, 5, 10, 15, 25, 40, 60, 0)
        private val PASSED_EG = intArrayOf(0, 10, 20, 35, 60, 100, 150, 0)
        private val UNDER_PROMOTIONS = intArrayOf(KNIGHT, ROOK, BISHOP)
        private val PAWN_SIDES = intArrayOf(-1, 1)

        private fun colorIndex(piece: Int) = if (piece > 0) 0 else 1

        private fun typeCode(type: PieceType) = when (type) {
            PieceType.PAWN -> PAWN
            PieceType.KNIGHT -> KNIGHT
            PieceType.BISHOP -> BISHOP
            PieceType.ROOK -> ROOK
            PieceType.QUEEN -> QUEEN
            PieceType.KING -> KING
        }

        private fun pieceType(code: Int) = when (code) {
            PAWN -> PieceType.PAWN
            KNIGHT -> PieceType.KNIGHT
            BISHOP -> PieceType.BISHOP
            ROOK -> PieceType.ROOK
            QUEEN -> PieceType.QUEEN
            else -> PieceType.KING
        }

        private fun manhattan(a: Int, b: Int) = abs((a shr 3) - (b shr 3)) + abs((a and 7) - (b and 7))

        // ---- Geometry, precomputed once ----

        private val KNIGHT_TARGETS: Array<IntArray> = Array(64) { sq -> stepTargets(sq, listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)) }
        private val KING_TARGETS: Array<IntArray> = Array(64) { sq -> stepTargets(sq, listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)) }

        /** RAYS[sq][d]: squares outward from sq; directions 0-3 orthogonal, 4-7 diagonal. */
        private val RAYS: Array<Array<IntArray>> = Array(64) { sq ->
            val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
            Array(8) { d ->
                val (dr, dc) = dirs[d]
                val squares = mutableListOf<Int>()
                var r = (sq shr 3) + dr
                var c = (sq and 7) + dc
                while (r in 0..7 && c in 0..7) {
                    squares += r * 8 + c
                    r += dr
                    c += dc
                }
                squares.toIntArray()
            }
        }

        private val KING_DISTANCE = IntArray(64 * 64) { i ->
            val a = i / 64
            val b = i % 64
            max(abs((a shr 3) - (b shr 3)), abs((a and 7) - (b and 7)))
        }

        private val CENTER_DISTANCE = IntArray(64) { sq ->
            val r = sq shr 3
            val c = sq and 7
            max(3 - r, r - 4) + max(3 - c, c - 4)
        }

        /** Castling rights that survive a move touching each square. */
        private val CASTLE_MASK = IntArray(64) { sq ->
            when (sq) {
                0 -> 15 and WQ.inv()
                7 -> 15 and WK.inv()
                4 -> 15 and (WK or WQ).inv()
                56 -> 15 and BQ.inv()
                63 -> 15 and BK.inv()
                60 -> 15 and (BK or BQ).inv()
                else -> 15
            }
        }

        private fun stepTargets(sq: Int, offsets: List<Pair<Int, Int>>): IntArray =
            offsets.mapNotNull { (dr, dc) ->
                val r = (sq shr 3) + dr
                val c = (sq and 7) + dc
                if (r in 0..7 && c in 0..7) r * 8 + c else null
            }.toIntArray()

        // ---- Zobrist hashing ----

        private val zobrist = Random(0x5EED_C4E55L)
        private val PIECE_KEYS = LongArray(12 * 64) { zobrist.nextLong() }
        private val SIDE_KEY = zobrist.nextLong()
        private val CASTLE_KEYS = LongArray(16) { zobrist.nextLong() }
        private val EP_KEYS = LongArray(8) { zobrist.nextLong() }

        private fun pieceKey(piece: Int, sq: Int): Long =
            PIECE_KEYS[((abs(piece) - 1) + if (piece < 0) 6 else 0) * 64 + sq]

        /**
         * Whether a pawn of [mover] could take en passant on [epSquare] - only
         * then does the square make the position different, as for
         * [ChessEngine.repetitionKey].
         */
        private fun epCapturable(board: IntArray, epSquare: Int, mover: Int): Boolean {
            val pawnRow = (epSquare shr 3) - mover
            val col = epSquare and 7
            if (pawnRow !in 0..7) return false
            return (col > 0 && board[pawnRow * 8 + col - 1] == PAWN * mover) ||
                (col < 7 && board[pawnRow * 8 + col + 1] == PAWN * mover)
        }

        private fun hashOf(board: IntArray, side: Int, castling: Int, ep: Int, epInHash: Boolean): Long {
            var h = 0L
            for (sq in 0..63) if (board[sq] != 0) h = h xor pieceKey(board[sq], sq)
            if (side == BLACK) h = h xor SIDE_KEY
            h = h xor CASTLE_KEYS[castling]
            if (epInHash) h = h xor EP_KEYS[ep and 7]
            return h
        }

        private fun hashOf(position: ChessPosition): Long {
            val board = IntArray(64)
            for (row in 0..7) for (col in 0..7) {
                val piece = position.board[row][col] ?: continue
                board[row * 8 + col] = typeCode(piece.type) * if (piece.color == PieceColor.WHITE) WHITE else BLACK
            }
            val side = if (position.sideToMove == PieceColor.WHITE) WHITE else BLACK
            val castling = (if (position.whiteCanCastleKingSide) WK else 0) or (if (position.whiteCanCastleQueenSide) WQ else 0) or
                (if (position.blackCanCastleKingSide) BK else 0) or (if (position.blackCanCastleQueenSide) BQ else 0)
            val ep = position.enPassantTarget?.let { it.row * 8 + it.col } ?: -1
            return hashOf(board, side, castling, ep, ep >= 0 && epCapturable(board, ep, side))
        }

        // ---- Piece-square tables, from White's side, a8 first (as a board is printed) ----

        private val PST_PAWN = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0
        )
        private val PST_KNIGHT = intArrayOf(
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50
        )
        private val PST_BISHOP = intArrayOf(
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20
        )
        private val PST_ROOK = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 10, 10, 10, 10, 10, 5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            0, 0, 0, 5, 5, 0, 0, 0
        )
        private val PST_QUEEN = intArrayOf(
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5,
            -10, 5, 5, 5, 5, 5, 0, -10,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20
        )
        private val PST_KING_MG = intArrayOf(
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20
        )
        private val PST_KING_EG = intArrayOf(
            -50, -40, -30, -20, -20, -30, -40, -50,
            -30, -20, -10, 0, 0, -10, -20, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -30, 0, 0, 0, 0, -30, -30,
            -50, -30, -30, -30, -30, -30, -30, -50
        )
    }
}
