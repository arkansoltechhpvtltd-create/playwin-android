package com.example.playwin.feature.game.carrom

import com.example.playwin.core.domain.carrom.CarromBoard
import com.example.playwin.core.domain.carrom.CarromMatch
import com.example.playwin.core.domain.carrom.Coin
import com.example.playwin.core.domain.carrom.STRIKER_ID
import com.example.playwin.core.domain.carrom.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlideStrikerTest {

    private val center = CarromBoard.SIZE / 2f

    private fun striker(coins: List<Coin>?) = coins!!.first { it.id == STRIKER_ID }

    @Test
    fun `slides the striker sideways and keeps it on its baseline`() {
        val coins = CarromMatch().coins
        val before = striker(coins)
        val after = striker(slideStriker(coins, center - 120f))
        assertEquals(center - 120f, after.position.x, 0.001f)
        assertEquals(before.position.y, after.position.y, 0.001f)
    }

    @Test
    fun `stops a striker radius inside the end circles on both sides`() {
        val coins = CarromMatch().coins
        assertEquals(center - STRIKER_SLIDE_HALF_SPAN, striker(slideStriker(coins, -500f)).position.x, 0.001f)
        assertEquals(center + STRIKER_SLIDE_HALF_SPAN, striker(slideStriker(coins, 5000f)).position.x, 0.001f)
    }

    @Test
    fun `stays clear of the frame on either side`() {
        val coins = CarromMatch().coins
        val left = striker(slideStriker(coins, 0f)).position.x
        val right = striker(slideStriker(coins, CarromBoard.SIZE)).position.x
        assert(left - CarromBoard.STRIKER_RADIUS > 0f)
        assert(right + CarromBoard.STRIKER_RADIUS < CarromBoard.SIZE)
    }

    @Test
    fun `leaves the other pieces where they are`() {
        val coins = CarromMatch().coins
        val after = slideStriker(coins, center + 200f)!!
        assertEquals(coins.filter { it.id != STRIKER_ID }, after.filter { it.id != STRIKER_ID })
    }

    @Test
    fun `refuses a spot where the striker would overlap a coin`() {
        val baselineY = CarromBoard.baselineFor(1).y
        val coins = CarromMatch().coins.map { if (it.id == 1) it.copy(position = Vec2(center + 150f, baselineY)) else it }
        assertNull(slideStriker(coins, center + 150f + CarromBoard.STRIKER_RADIUS))
    }

    @Test
    fun `does nothing without a striker on the board`() {
        val coins = CarromMatch().coins.map { if (it.id == STRIKER_ID) it.copy(pocketed = true) else it }
        assertNull(slideStriker(coins, center))
    }
}
