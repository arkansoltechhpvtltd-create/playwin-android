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
- `resolve(gameId: GameId, snapshot: GameSnapshot): GameOutcome` — take the client's reported end-of-round board state (`GameSnapshot`, one sealed subtype per game: `ConnectFour`, `TwentyFortyEight`, `EightBall`, `Carrom`, `Bingo`, `WordHunt`, `Chess`, `Ludo`, `Rummy`) and authoritatively return `WIN` / `LOSE` / `DRAW` / `IN_PROGRESS`. A real implementation should independently re-derive or verify the result server-side (e.g. replay moves, validate against game rules) rather than trust the client's claim, so a modified client can't fabricate a win.

### `RewardsRepository`
Issues rewards for a confirmed win — deliberately separate from `GameEngineRepository`: that decides *whether* a round was won, this decides *what the player gets*.
- `claimReward(gameId: GameId): Coupon` — called only after `GameEngineRepository.resolve` has returned `WIN`. Should be server-authoritative (idempotent per round, rate-limited) so a client can't claim rewards for rounds it didn't win or claim the same win twice.

### `CouponsRepository`
The player's coupon wallet.
- `observeCoupons(): Flow<List<Coupon>>` — the player's full coupon list (won, locked, redeemed).
- `observeCouponBalance(): Flow<Int>` — count of unredeemed, won coupons (lobby balance chip).
- `addCoupon(coupon: Coupon): Unit` — persist a newly won coupon (typically called internally by the backend when `RewardsRepository.claimReward` succeeds, not by the client).
- `redeemCoupon(couponId: String): Unit` — mark a coupon redeemed server-side; should validate the coupon belongs to the caller and isn't already redeemed/expired.

### `SettingsRepository`
App preferences and responsible-gaming controls for the current session.
- `observeSettings(): Flow<AppSettings>` — current preferences (dark mode, notifications, sound, daily limit minutes, self-exclusion).
- `setDarkModeEnabled(enabled: Boolean)`
- `setNotificationsEnabled(enabled: Boolean)`
- `setSoundEnabled(enabled: Boolean)`
- `setDailyLimitMinutes(minutes: Int)`
- `setSelfExclusionEnabled(enabled: Boolean)` — responsible-gaming settings in particular should be persisted server-side and enforced there too (e.g. reject game rounds once a self-excluded or over-limit player tries to play), not relied on client-side alone.

*(Profile screens are composed from `AuthRepository` + `SettingsRepository` — there's no separate profile repository.)*
