package com.example.playwin.core.domain.bingo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BingoBotTest {

    /** Column c, row r holds its range's first number + [offset] + r; free centre is 0. */
    private fun card(offset: Int = 0): List<List<Int>> = (0 until BINGO_SIZE).map { r ->
        (0 until BINGO_SIZE).map { c ->
            if (r == BINGO_FREE_ROW && c == BINGO_FREE_COL) 0 else BINGO_COLUMN_RANGES[c].first + offset + r
        }
    }

    private fun List<List<Int>>.with(row: Int, col: Int, value: Int) =
        mapIndexed { r, cells -> cells.mapIndexed { c, v -> if (r == row && c == col) value else v } }

    private val mine = card()          // row 0: 1, 16, 31, 46, 61
    private val theirs = card(offset = 5) // row 0: 6, 21, 36, 51, 66 - no overlap with mine

    @Test
    fun `calls the number that completes its own line`() {
        val called = listOf(1, 16, 31, 46)
        repeat(20) {
            assertEquals(61, BingoBot.chooseCall(mine, theirs, called, Random(it)))
        }
    }

    @Test
    fun `never hands the opponent their winning number`() {
        // 66 would finish the opponent's top row; put it on the bot's card too so it's tempting.
        val tempting = mine.with(1, 4, 66)
        val called = listOf(6, 21, 36, 51)
        repeat(20) {
            assertNotEquals(66, BingoBot.chooseCall(tempting, theirs, called, Random(it)))
        }
    }

    @Test
    fun `pushes its most advanced line`() {
        val called = listOf(1, 16, 31) // three of the bot's top row
        repeat(20) {
            val choice = BingoBot.chooseCall(mine, theirs, called, Random(it))
            assertTrue("chose $choice", choice == 46 || choice == 61)
        }
    }

    @Test
    fun `a number that also helps the opponent scores lower`() {
        val myCard = mine.with(1, 4, 66)
        val helpsThem = BingoBot.score(66, myCard, theirs, called = listOf(6, 21))
        val helpsOnlyMe = BingoBot.score(66, myCard, card(offset = 10), called = listOf(6, 21))
        assertTrue(helpsThem < helpsOnlyMe)
    }

    @Test
    fun `returns null once every number has been called`() {
        assertNull(BingoBot.chooseCall(mine, theirs, (1..BINGO_MAX_NUMBER).toList()))
    }

    @Test
    fun `room cards are deterministic, distinct, and valid`() {
        val (host, guest) = BingoRules.cardsForRoom("3f9c2a7e-0000-4000-8000-000000000001")
        assertEquals(host to guest, BingoRules.cardsForRoom("3f9c2a7e-0000-4000-8000-000000000001"))
        assertNotEquals(host, guest)
        for (card in listOf(host, guest)) {
            assertEquals(0, card[BINGO_FREE_ROW][BINGO_FREE_COL])
            for (c in 0 until BINGO_SIZE) {
                val column = (0 until BINGO_SIZE).map { card[it][c] }.filter { it != 0 }
                assertTrue(column.all { it in BINGO_COLUMN_RANGES[c] })
                assertEquals(column.size, column.toSet().size)
            }
        }
    }

    @Test
    fun `marks and lines follow the called numbers`() {
        assertFalse(BingoRules.hasLine(BingoRules.marks(mine, listOf(1, 16, 31, 46))))
        assertTrue(BingoRules.hasLine(BingoRules.marks(mine, listOf(1, 16, 31, 46, 61))))
        // Centre column needs only 4 calls thanks to the free square.
        assertTrue(BingoRules.hasLine(BingoRules.marks(mine, listOf(31, 32, 34, 35))))
    }
}
