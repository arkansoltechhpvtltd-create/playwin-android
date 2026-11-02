# PlayWin

A mobile mini-games app where wins earn coupons that convert to wallet rewards.

Kotlin + Jetpack Compose, Clean Architecture (domain / data / feature layers, Hilt DI).
All data currently comes from in-memory mock repositories under `core.data.mock`; there
is no real backend yet.

## Going live: backend interfaces to implement

The app is already wired so that going live means writing network-backed
implementations of six domain interfaces — **no UI or ViewModel code needs to
change**. Every ViewModel injects the interface type (never the `Mock*` class
directly), and `core/data/di/DataModule.kt` is the single place that binds each
interface to its implementation via Hilt `@Binds`. To go live, replace the
`Mock*` class bound in each `@Binds` method with a real implementation of the
same interface (e.g. backed by Retrofit/gRPC) — that's the only change needed
outside `core.data`.

All interfaces live in `core/domain/repository/`; today's reference behavior
is in the matching `core/data/mock/Mock*.kt`.

### `AuthRepository`
Who's signed in.
- `observeCurrentUser(): Flow<AuthUser?>` — emit the signed-in user, or `null` when logged out. Drives top-level navigation, so it should reflect server session state (e.g. via a stored token) on app start.
- `login(identifier, password): Result<AuthUser>` — authenticate against the backend; return `Result.failure` with a user-facing message on bad credentials.
- `signUp(displayName, identifier, password, isAdult): Result<AuthUser>` — create the account server-side; enforce age gating (`isAdult`) server-side, not just client-side.
- `loginAsGuest(): Result<AuthUser>` — start an ephemeral session with no persisted credentials.
- `updateDisplayName(displayName): Result<AuthUser>` — persist the change server-side and return the updated user.
- `logout(): Unit` — invalidate the session (server-side token revocation, not just local clear).

### `GamesRepository`
Lobby catalogue.
- `observeGames(): Flow<List<Game>>` — the list of games to show, including which are fully playable (`Game.isFullyPlayable`) vs. "coming soon". Lets the backend control rollout without a client release.

### `GameEngineRepository`
The single authority on "who won" — the client never decides a game's outcome itself.
- `resolve(gameId: GameId, snapshot: GameSnapshot): GameOutcome` — take the client's reported end-of-round board state (`GameSnapshot`, one sealed subtype per game: `ChineseCheckers`, `TwentyFortyEight`, `EightBall`, `Carrom`, `Sudoku`, `SnakesAndLadders`, `Chess`, `Ludo`, `Rummy`) and authoritatively return `WIN` / `LOSE` / `DRAW` / `IN_PROGRESS`. A real implementation should independently re-derive or verify the result server-side (e.g. replay moves, validate against game rules) rather than trust the client's claim, so a modified client can't fabricate a win.
- `rollDie(gameId: GameId): Int` — throw one six-sided die (1..6) for a dice game played locally (today: Snakes & Ladders vs Computer). No screen or ViewModel rolls its own die. Online Snakes & Ladders doesn't use this: the `roll_and_move` Postgres function (`supabase/migrations/0009_snakes_and_ladders_server_dice.sql`) throws the die and applies the move server-side, and `rooms_guard` rejects any other write to that game's board.

### `RewardsRepository`
Issues rewards for a confirmed win — deliberately separate from `GameEngineRepository`: that decides *whether* a round was won, this decides *what the player gets*.
- `claimRoomReward(roomId: String): Result<String>` — claim the coupon for an online win through the `claim_room_coupon` Postgres function (`supabase/migrations/0010_server_issued_coupons.sql`). The server reads the winner from the room, issues one coupon per room, and only pays out games in `coupon_verified_games`, whose winner it decides itself (today: Snakes & Ladders). Returns the coupon id, or `NO_COUPON_ID` when that game doesn't pay out.
- `claimVsComputerReward(gameId: GameId): String` — vs-Computer games run entirely on the device, so nothing can prove a win. A signed-in player gets `NO_COUPON_ID`; a guest gets a demo coupon that lives only in their in-memory wallet.

### Server-side win resolution: Supabase Edge Function contract

`GameEngineRepository.resolve` is still mocked
(`core/data/mock/MockGameEngineRepository.kt`) — deliberately, so this is a
clean, already-isolated seam for a backend developer to fill in without
touching any ViewModel, screen, or the repositories already live on Supabase.

Clients can't write to the `coupons` table, so this function is how a game
would start paying out vs-Computer wins. It should ship as **one** Supabase
Edge Function, not two separate endpoints — the client must never be able to
call "claim reward" without the function having just verified a win itself.

**What the function must do:**
1. Authenticate the caller from the Supabase JWT (`auth.uid()`), never from a
   field in the request body.
2. Re-derive the outcome itself from the submitted `snapshot`, per `gameId` —
   reusing the exact rules in `MockGameEngineRepository` (Chinese Checkers
   move re-check + all-10-pegs-in-the-opposite-point, 2048 tile-reached/no-moves-left, 8-ball
   group-cleared-and-no-scratch, Carrom score comparison once cleared, Sudoku
   givens-kept + full grid with no repeats, Snakes & Ladders move replay from position + roll (reach or pass 100), Chess checkmate/draws
   via a full rules engine, Ludo all-tokens-home, Rummy valid-declaration
   check). Never trust a client-reported "I won" flag.
3. Only if the outcome is `WIN`, pick/generate a coupon and `insert` it into
   the `coupons` table using the **service role key**, bypassing RLS.
4. Make the claim idempotent per round (e.g. a dedupe key from `user_id` +
   `gameId` + a hash of the snapshot, or a server-issued round id) so retried
   or replayed requests can't mint duplicate coupons, and rate-limit per user.
5. Return the outcome and, if any, the new coupon — nothing else the client
   sent should be echoed back or trusted for a second write.

**Request** (`POST`, `Authorization: Bearer <user JWT>`):
```json
{
  "gameId": "CHINESE_CHECKERS",
  "snapshot": { "type": "ChineseCheckers", "before": { "cells": [0, 0, 2, 1], "sideToMove": 1 }, "from": 3, "to": 0 }
}
```
`gameId` is one of the nine `GameId` enum names; `snapshot.type` is one of
`GameSnapshot`'s nine sealed subtypes (`ChineseCheckers`, `TwentyFortyEight`,
`EightBall`, `Carrom`, `Sudoku`, `SnakesAndLadders`, `Chess`, `Ludo`, `Rummy`), and the
remaining fields match that subtype's constructor 1:1 — see
`core/domain/model/GameSnapshot.kt`.

**Response:**
```json
{
  "outcome": "WIN",
  "coupon": {
    "id": "...", "partnerName": "...", "discountText": "...",
    "source": "Won in Chinese Checkers", "expiry": "...", "code": "...", "status": "WON"
  }
}
```
`outcome` is one of `GameOutcome`'s four values (`WIN`/`LOSE`/`DRAW`/`IN_PROGRESS`);
`coupon` is `null` unless `outcome == "WIN"`.

### `CouponsRepository`
The player's coupon wallet.
- `observeCoupons(): Flow<List<Coupon>>` — the player's full coupon list (won, locked, redeemed).
- `observeCouponBalance(): Flow<Int>` — count of unredeemed, won coupons (lobby balance chip).
- `redeemCoupon(couponId: String): Unit` — mark a coupon redeemed through the `redeem_coupon` Postgres function, which checks the coupon belongs to the caller and hasn't been redeemed.

### `SettingsRepository`
App preferences and responsible-gaming controls for the current session.
- `observeSettings(): Flow<AppSettings>` — current preferences (dark mode, notifications, sound, daily limit minutes, self-exclusion).
- `setDarkModeEnabled(enabled: Boolean)`
- `setNotificationsEnabled(enabled: Boolean)`
- `setSoundEnabled(enabled: Boolean)`
- `setDailyLimitMinutes(minutes: Int)`
- `setSelfExclusionEnabled(enabled: Boolean)` — responsible-gaming settings in particular should be persisted server-side and enforced there too (e.g. reject game rounds once a self-excluded or over-limit player tries to play), not relied on client-side alone.

*(Profile screens are composed from `AuthRepository` + `SettingsRepository` — there's no separate profile repository.)*
