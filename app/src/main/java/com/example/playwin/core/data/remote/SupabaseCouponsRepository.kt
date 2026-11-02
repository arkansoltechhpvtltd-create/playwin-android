package com.example.playwin.core.data.remote

import android.util.Log
import com.example.playwin.core.data.mock.MockCouponsRepository
import com.example.playwin.core.domain.model.Coupon
import com.example.playwin.core.domain.model.CouponStatus
import com.example.playwin.core.domain.repository.AuthRepository
import com.example.playwin.core.domain.repository.CouponsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SupabaseCoupons"
private const val TABLE = "coupons"

@Serializable
private data class RedeemCouponParams(@SerialName("p_coupon_id") val couponId: String)

@Serializable
internal data class CouponRow(
    val id: String,
    val partner: String,
    val discount: String,
    val code: String,
    @SerialName("source_game") val sourceGame: String,
    val status: String,
    @SerialName("expires_at") val expiresAt: String? = null
) {
    fun toDomain(): Coupon = Coupon(
        id = id,
        partnerName = partner,
        discountText = discount,
        source = sourceGame,
        expiry = expiresAt.orEmpty(),
        code = code,
        status = runCatching { CouponStatus.valueOf(status.uppercase()) }.getOrDefault(CouponStatus.WON)
    )
}

/**
 * Real, network-backed [CouponsRepository] for signed-in Supabase users - every
 * coupon lives in the `coupons` table (RLS-scoped to the caller via `user_id`),
 * not in memory. Guest sessions never touch Supabase: they delegate entirely to
 * [MockCouponsRepository] so guest mode keeps working without an account.
 *
 * The app can only read the table. A coupon is created by the server
 * ([SupabaseRewardsRepository]) and redeemed through the `redeem_coupon` RPC
 * (see `supabase/migrations/0010_server_issued_coupons.sql`).
 *
 * Reads and redeems fail soft (logged, not thrown) so a flaky connection never
 * crashes the wallet screen; [observeCoupons] falls back to whatever is cached,
 * and a redeem the server refused leaves the coupon as it was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SupabaseCouponsRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val mockCouponsRepository: MockCouponsRepository
) : CouponsRepository {

    private val remoteCoupons = MutableStateFlow<List<Coupon>>(emptyList())
    private var cachedUserId: String? = null

    override fun observeCoupons(): Flow<List<Coupon>> =
        authRepository.observeCurrentUser()
            .distinctUntilChanged { old, new -> old?.id == new?.id && old?.isGuest == new?.isGuest }
            .flatMapLatest { user ->
                when {
                    user == null -> flowOf(emptyList())
                    user.isGuest -> mockCouponsRepository.observeCoupons()
                    else -> remoteCouponsFlow(user.id)
                }
            }

    override fun observeCouponBalance(): Flow<Int> =
        observeCoupons().map { coupons -> coupons.count { it.status == CouponStatus.WON } }

    /** Puts a coupon the server just issued into the wallet. A retried claim returns the same one. */
    fun onCouponIssued(coupon: Coupon) {
        remoteCoupons.update { list -> listOf(coupon) + list.filterNot { it.id == coupon.id } }
    }

    override suspend fun redeemCoupon(couponId: String) {
        val user = authRepository.observeCurrentUser().first()
        if (user == null || user.isGuest) {
            mockCouponsRepository.redeemCoupon(couponId)
            return
        }
        try {
            supabaseClient.postgrest.rpc(
                "redeem_coupon",
                Json.encodeToJsonElement(RedeemCouponParams(couponId = couponId)).jsonObject
            )
            remoteCoupons.update { list ->
                list.map { if (it.id == couponId) it.copy(status = CouponStatus.REDEEMED) else it }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to redeem coupon $couponId", e)
        }
    }

    private fun remoteCouponsFlow(userId: String): Flow<List<Coupon>> = flow {
        refreshFromDb(userId)
        emitAll(remoteCoupons)
    }

    private suspend fun refreshFromDb(userId: String) {
        if (cachedUserId == userId) return
        try {
            val rows = supabaseClient.from(TABLE)
                .select {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<CouponRow>()
            remoteCoupons.value = rows.map { it.toDomain() }
            cachedUserId = userId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline or a query/RLS error: keep whatever's cached (possibly
            // empty) instead of crashing the wallet screen.
            Log.w(TAG, "Failed to load coupons for $userId", e)
        }
    }
}
