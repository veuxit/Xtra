package com.github.andreyasadchy.xtra.model.gql

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueryResponse(
    val errors: List<Error>? = null,
    val data: Query? = null,
) {
    @Serializable
    data class Query(
        val badges: List<Badge?>? = null,
        val cheerConfig: GlobalCheerConfig? = null,
        val games: GameConnection? = null,
        val game: Game? = null,
        val searchCategories: SearchCategoriesConnection? = null,
        val searchFor: SearchFor? = null,
        val searchStreams: SearchStreamConnection? = null,
        val searchUsers: SearchUserConnection? = null,
        val streams: StreamConnection? = null,
        val user: User? = null,
        val userResultByID: UserResult? = null,
        val userResultByLogin: UserResult? = null,
        val users: List<User?>? = null,
        val video: Video? = null,
    )

    @Serializable
    data class Badge(
        val imageURL: String? = null,
        val setID: String? = null,
        val title: String? = null,
        val version: String? = null,
    )

    @Serializable
    data class Broadcast(
        val startedAt: String? = null,
    )

    @Serializable
    data class BroadcastSettings(
        val title: String? = null,
    )

    @Serializable
    data class ChannelNotificationSettings(
        val isEnabled: Boolean? = null,
    )

    @Serializable
    data class CheerInfo(
        val cheerGroups: List<CheermoteGroup>? = null,
    )

    @Serializable
    data class Cheermote(
        val prefix: String? = null,
        val tiers: List<CheermoteTier>? = null,
    )

    @Serializable
    data class CheermoteColorConfig(
        val bits: Int? = null,
        val color: String? = null,
    )

    @Serializable
    data class CheermoteDisplayConfig(
        val backgrounds: List<String>? = null,
        val colors: List<CheermoteColorConfig>? = null,
        val scales: List<String>? = null,
        val types: List<CheermoteDisplayType>? = null,
    )

    @Serializable
    data class CheermoteDisplayType(
        val animation: String? = null,
        val extension: String? = null,
    )

    @Serializable
    data class CheermoteGroup(
        val nodes: List<Cheermote>? = null,
        val templateURL: String? = null,
    )

    @Serializable
    data class CheermoteTier(
        val bits: Int? = null,
    )

    @Serializable
    data class Clip(
        val broadcaster: User? = null,
        val createdAt: String? = null,
        val durationSeconds: Int? = null,
        val game: Game? = null,
        val id: String? = null,
        val slug: String? = null,
        val thumbnailURL: String? = null,
        val title: String? = null,
        val video: Video? = null,
        val videoOffsetSeconds: Int? = null,
        val viewCount: Int? = null,
    )

    @Serializable
    data class ClipConnection(
        val edges: List<ClipEdge>? = null,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class ClipEdge(
        val cursor: String? = null,
        val node: Clip? = null,
    )

    @Serializable
    data class Emote(
        val id: String? = null,
        val owner: User? = null,
        val setID: String? = null,
        val token: String? = null,
        val type: String? = null,
    )

    @Serializable
    data class EmoteSet(
        val emotes: List<Emote>? = null,
    )

    @Serializable
    data class Follow(
        val followedAt: String? = null,
    )

    @Serializable
    data class FollowConnection(
        val edges: List<FollowEdge>? = null,
        val pageInfo: PageInfo? = null,
        val totalCount: Int? = null,
    )

    @Serializable
    data class FollowedGameConnection(
        val nodes: List<Game>? = null,
    )

    @Serializable
    data class FollowEdge(
        val cursor: String? = null,
        val followedAt: String? = null,
        val node: User? = null,
    )

    @Serializable
    data class FollowedLiveUserConnection(
        val edges: List<FollowedLiveUserEdge>? = null,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class FollowedLiveUserEdge(
        val cursor: String? = null,
        val node: User? = null,
    )

    @Serializable
    data class FollowerConnection(
        val totalCount: Int? = null,
    )

    @Serializable
    data class FollowerEdge(
        val notificationSettings: ChannelNotificationSettings? = null,
    )

    @Serializable
    data class FreeformTag(
        val name: String? = null,
    )

    @Serializable
    data class Game(
        val boxArtURL: String? = null,
        val broadcastersCount: Int? = null,
        val clips: ClipConnection? = null,
        val displayName: String? = null,
        val id: String? = null,
        val slug: String? = null,
        val streams: StreamConnection? = null,
        val tags: List<Tag>? = null,
        val videos: VideoConnection? = null,
        val viewersCount: Int? = null,
    )

    @Serializable
    data class GameConnection(
        val edges: List<GameEdge>? = null,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class GameEdge(
        val cursor: String? = null,
        val node: Game? = null,
    )

    @Serializable
    data class GlobalCheerConfig(
        val displayConfig: CheermoteDisplayConfig? = null,
        val groups: List<CheermoteGroup>? = null,
    )

    @Serializable
    data class PageInfo(
        val hasNextPage: Boolean? = null,
        val hasPreviousPage: Boolean? = null,
    )

    @Serializable
    data class SearchCategoriesConnection(
        val edges: List<SearchCategoriesEdge>? = null,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class SearchCategoriesEdge(
        val cursor: String? = null,
        val node: Game? = null,
    )

    @Serializable
    data class SearchFor(
        val channels: SearchForResultUsers? = null,
        val games: SearchForResultGames? = null,
        val videos: SearchForResultVideos? = null,
    )

    @Serializable
    data class SearchForResultGames(
        val cursor: String? = null,
        val items: List<Game>? = null,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class SearchForResultUsers(
        val cursor: String? = null,
        val items: List<User>? = null,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class SearchForResultVideos(
        val cursor: String? = null,
        val items: List<Video>? = null,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class SearchStreamConnection(
        val edges: List<SearchStreamEdge>? = null,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class SearchStreamEdge(
        val cursor: String? = null,
        val node: Stream? = null,
    )

    @Serializable
    data class SearchUserConnection(
        val edges: List<SearchUserEdge>? = null,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class SearchUserEdge(
        val cursor: String? = null,
        val node: User? = null,
    )

    @Serializable
    data class Stream(
        val broadcaster: User? = null,
        val createdAt: String? = null,
        val freeformTags: List<FreeformTag>? = null,
        val game: Game? = null,
        val id: String? = null,
        val previewImageURL: String? = null,
        val title: String? = null,
        val type: String? = null,
        val viewersCount: Int? = null,
    )

    @Serializable
    data class StreamConnection(
        val edges: List<StreamEdge>? = null,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class StreamEdge(
        val cursor: String? = null,
        val node: Stream? = null,
    )

    @Serializable
    data class Tag(
        val id: String? = null,
        val localizedName: String? = null,
        val scope: String? = null,
    )

    @Serializable
    data class User(
        val bannerImageURL: String? = null,
        val broadcastBadges: List<Badge?>? = null,
        val broadcastSettings: BroadcastSettings? = null,
        val cheer: CheerInfo? = null,
        val clips: ClipConnection? = null,
        val createdAt: String? = null,
        val description: String? = null,
        val displayName: String? = null,
        val emoteSets: List<EmoteSet>? = null,
        val follow: Follow? = null,
        val followedGames: FollowedGameConnection? = null,
        val followedLiveUsers: FollowedLiveUserConnection? = null,
        val followedVideos: VideoConnection? = null,
        val followers: FollowerConnection? = null,
        val follows: FollowConnection? = null,
        val id: String? = null,
        val login: String? = null,
        val lastBroadcast: Broadcast? = null,
        val profileImageURL: String? = null,
        val roles: UserRoles? = null,
        val self: UserSelfConnection? = null,
        val stream: Stream? = null,
        val videos: VideoConnection? = null,
    )

    @Serializable
    data class UserResult(
        val key: String? = null,
        val reason: String? = null,
        @SerialName("__typename")
        val typeName: String? = null,
    )

    @Serializable
    data class UserRoles(
        val isAffiliate: Boolean? = null,
        val isExtensionsDeveloper: Boolean? = null,
        val isGlobalMod: Boolean? = null,
        val isPartner: Boolean? = null,
        val isSiteAdmin: Boolean? = null,
        val isStaff: Boolean? = null,
    )

    @Serializable
    data class UserSelfConnection(
        val follower: FollowerEdge? = null,
    )

    @Serializable
    data class Video(
        val animatedPreviewURL: String? = null,
        val broadcastType: String? = null,
        val contentTags: List<Tag>? = null,
        val createdAt: String? = null,
        val game: Game? = null,
        val id: String? = null,
        val lengthSeconds: Int? = null,
        val owner: User? = null,
        val previewThumbnailURL: String? = null,
        val title: String? = null,
        val viewCount: Int? = null,
    )

    @Serializable
    data class VideoConnection(
        val edges: List<VideoEdge>? = null,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class VideoEdge(
        val cursor: String? = null,
        val node: Video? = null,
    )
}