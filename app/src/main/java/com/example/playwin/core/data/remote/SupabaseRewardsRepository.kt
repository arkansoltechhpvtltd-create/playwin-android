package com.example.playwin.core.data.remote

import android.util.Log
import com.example.playwin.core.data.mock.MockRewardsRepository
import com.example.playwin.core.domain.model.GameId
import com.example.playwin.core.domain.repository.AuthRepository
import com.example.playwin.core.domain.repository.NO_COUPON_ID
import com.example.playwin.core.domain.repository.RewardsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SupabaseRewards"

@Serializable
private data class ClaimRoomCouponParams(@SerialName("p_room_id") val roomId: String)

/**
 * Real [RewardsRepository]. The `claim_room_coupon` RPC (a `security definer`
 * Postgres function - see `supabase/migrations/0010_server_issued_coupons.sql`)
 * is the only thing that creates a coupon; the client sends a room id and
 * nothing else, and `claim_pending_coupons` (0011) runs it for every unpaid win.
 * Guests never touch Supabase and keep the demo rewards.
 */
@Singleton
class SupabaseRewardsRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val couponsRepository: SupabaseCouponsRepository,
    private val mockRewardsRepository: MockRewardsRepository
) : RewardsRepository {

    override suspend fun claimRoomReward(roomId: String): Result<String> = try {
        val coupon = supabaseClient.postgrest
            .rpc("claim_room_coupon", Json.encodeToJsonElement(ClaimRoomCouponParams(roomId)).jsonObject)
            .decodeList<CouponRow>()
            .firstOrNull()
            ?.toDomain()
        if (coupon != null) couponsRepository.onCouponIssued(coupon)
        Result.success(coupon?.id ?: NO_COUPON_ID)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Failed to claim the coupon for room $roomId", e)
        Result.failure(e)
    }

    override suspend fun claimPendingRewards() {
        val user = authRepository.observeCurrentUser().first()
        if (user == null || user.isGuest) return
        try {
            val coupons = supabaseClient.postgrest
                .rpc("claim_pending_coupons")
                .decodeList<CouponRow>()
                .map { it.toDomain() }
            couponsRepository.onCouponsLoaded(user.id, coupons)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to claim pending coupons", e)
        }
    }

    override suspend fun claimVsComputerReward(gameId: GameId): String {
        val user = authRepository.observeCurrentUser().first()
        return if (user == null || user.isGuest) {
            mockRewardsRepository.claimVsComputerReward(gameId)
        } else {
            NO_COUPON_ID
        }
    }
}
