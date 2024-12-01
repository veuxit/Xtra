package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class EmoteCardResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val emote: Emote,
    )

    @Serializable
    data class Emote(
        val type: String? = null,
        val subscriptionTier: String? = null,
        val bitsBadgeTierSummary: Tier? = null,
        val owner: User? = null,
    )

    @Serializable
    data class Tier(
        val threshold: Int? = null,
    )

    @Serializable
    data class User(
        val login: String? = null,
        val displayName: String? = null,
    )
}