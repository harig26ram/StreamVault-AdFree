package com.streamvault.player.sponsorblock

import com.streamvault.player.core.PlayerState
import com.streamvault.player.sponsorblock.data.SponsorSegment
import com.streamvault.player.sponsorblock.domain.SponsorBlockRepository

sealed interface SponsorBlockAction {
    data class Skip(val segment: SponsorSegment) : SponsorBlockAction
    data object None : SponsorBlockAction
}

class SponsorBlockManager(
    private val repository: SponsorBlockRepository = SponsorBlockRepository()
) {
    private val cache = mutableMapOf<String, List<SponsorSegment>>()

    suspend fun loadSegments(videoId: String): Result<List<SponsorSegment>> {
        return repository.getSegments(videoId).also { result ->
            result.onSuccess { segments ->
                cache[videoId] = segments
            }
        }
    }

    fun shouldAutoSkip(segment: SponsorSegment): Boolean {
        return segment.category == "sponsor" || segment.category == "selfpromo"
    }

    fun checkSegments(videoId: String, state: PlayerState, positionMs: Long): SponsorBlockAction? {
        if (state !is PlayerState.Playing) return null
        val cachedSegments = cache[videoId] ?: emptyList()
        if (cachedSegments.isEmpty()) return SponsorBlockAction.None
        for (segment in cachedSegments) {
            if (!shouldAutoSkip(segment)) continue
            val startMs = (segment.segment[0] * 1000).toLong()
            val endMs = (segment.segment[1] * 1000).toLong()
            if (positionMs in startMs until endMs) {
                return SponsorBlockAction.Skip(segment)
            }
        }
        return SponsorBlockAction.None
    }
}
