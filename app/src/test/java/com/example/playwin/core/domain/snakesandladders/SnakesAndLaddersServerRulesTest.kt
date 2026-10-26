package com.example.playwin.core.domain.snakesandladders

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Online, the server's `roll_and_move` applies the rules, while the app replays
 * them with [SnakesAndLaddersRules]. Both have to agree on the board and the
 * sixes limit, so this reads the SQL and fails the build if they drift apart.
 */
class SnakesAndLaddersServerRulesTest {

    private val sql: String by lazy {
        val path = "supabase/migrations/0009_snakes_and_ladders_server_dice.sql"
        // Unit tests run from the app module; look upwards for the repo root.
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, path) }
            .first { it.exists() }
            .readText()
    }

    private fun constant(name: String): String =
        Regex("""$name constant \w+ := (.+?);""").find(sql)?.groupValues?.get(1)
            ?: error("$name not found in the migration")

    private fun jumps(name: String): Map<Int, Int> =
        Json.parseToJsonElement(constant(name).trim('\'')).jsonObject
            .map { (from, to) -> from.toInt() to to.jsonPrimitive.int }
            .toMap()

    @Test
    fun `the server's ladders match the app's`() {
        assertEquals(SnakesAndLaddersBoard.LADDERS, jumps("c_ladders"))
    }

    @Test
    fun `the server's snakes match the app's`() {
        assertEquals(SnakesAndLaddersBoard.SNAKES, jumps("c_snakes"))
    }

    @Test
    fun `the server forfeits on the same run of sixes and finishes on the same square`() {
        assertEquals(SnakesAndLaddersRules.MAX_SIXES_IN_A_ROW, constant("c_max_sixes").toInt())
        assertEquals(SnakesAndLaddersRules.FINISH, constant("c_finish").toInt())
    }
}
