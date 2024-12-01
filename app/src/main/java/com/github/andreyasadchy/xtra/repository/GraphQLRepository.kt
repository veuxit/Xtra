package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.api.GraphQLApi
import com.github.andreyasadchy.xtra.model.gql.ErrorResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelClipsResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelVideosResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelViewerListResponse
import com.github.andreyasadchy.xtra.model.gql.chat.BadgesResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelCheerEmotesResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelPointContextResponse
import com.github.andreyasadchy.xtra.model.gql.chat.EmoteCardResponse
import com.github.andreyasadchy.xtra.model.gql.chat.GlobalCheerEmotesResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ModeratorsResponse
import com.github.andreyasadchy.xtra.model.gql.chat.UserEmotesResponse
import com.github.andreyasadchy.xtra.model.gql.chat.VipsResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipDataResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipUrlsResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipVideoResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedChannelsResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedGamesResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedStreamsResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedVideosResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowingGameResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowingUserResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameClipsResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameStreamsResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameVideosResponse
import com.github.andreyasadchy.xtra.model.gql.game.GamesResponse
import com.github.andreyasadchy.xtra.model.gql.playlist.PlaybackAccessTokenResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchChannelsResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchGameTagsResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchGamesResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchStreamTagsResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchVideosResponse
import com.github.andreyasadchy.xtra.model.gql.stream.StreamsResponse
import com.github.andreyasadchy.xtra.model.gql.stream.ViewerCountResponse
import com.github.andreyasadchy.xtra.model.gql.video.VideoGamesResponse
import com.github.andreyasadchy.xtra.model.gql.video.VideoMessagesResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GraphQLRepository @Inject constructor(private val graphQL: GraphQLApi) {

    fun getPlaybackAccessTokenRequestBody(login: String?, vodId: String?, playerType: String?): JsonObject {
        return buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "ed230aa1e33e07eebb8928504583da78a5173989fadfb1ac94be06a04f3cdbe9")
                    put("version", 1)
                }
            }
            put("operationName", "PlaybackAccessToken")
            putJsonObject("variables") {
                put("isLive", !login.isNullOrBlank())
                put("login", login ?: "")
                put("isVod", !vodId.isNullOrBlank())
                put("vodID", vodId ?: "")
                put("platform", "web")
                put("playerType", playerType)
            }
        }
    }

    suspend fun loadPlaybackAccessToken(headers: Map<String, String>, login: String? = null, vodId: String? = null, playerType: String?): PlaybackAccessTokenResponse {
        return graphQL.getPlaybackAccessToken(headers, getPlaybackAccessTokenRequestBody(login, vodId, playerType))
    }

    suspend fun loadClipUrls(headers: Map<String, String>, slug: String?): ClipUrlsResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "6fd3af2b22989506269b9ac02dd87eb4a6688392d67d94e41a6886f1e9f5c00f")
                    put("version", 1)
                }
            }
            put("operationName", "VideoAccessToken_Clip")
            putJsonObject("variables") {
                put("slug", slug)
                put("platform", "web")
            }
        }
        return graphQL.getClipUrls(headers, json)
    }

    suspend fun loadClipData(headers: Map<String, String>, slug: String?): ClipDataResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "a33067cdf92191dccfb53aa86f878a2c271e6a3587a6731dc8275e5751dd133f")
                    put("version", 1)
                }
            }
            put("operationName", "ChannelClipCore")
            putJsonObject("variables") {
                put("clipSlug", slug)
            }
        }
        return graphQL.getClipData(headers, json)
    }

    suspend fun loadClipVideo(headers: Map<String, String>, slug: String?): ClipVideoResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "9aa558e066a22227c5ef2c0a8fded3aaa57d35181ad15f63df25bff516253a90")
                    put("version", 1)
                }
            }
            put("operationName", "ChatClip")
            putJsonObject("variables") {
                put("clipSlug", slug)
            }
        }
        return graphQL.getClipVideo(headers, json)
    }

    suspend fun loadTopGames(headers: Map<String, String>, tags: List<String>?, limit: Int?, cursor: String?): GamesResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "2f67f71ba89f3c0ed26a141ec00da1defecb2303595f5cda4298169549783d9e")
                    put("version", 1)
                }
            }
            put("operationName", "BrowsePage_AllDirectories")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("limit", limit)
                putJsonObject("options") {
                    put("sort", "VIEWER_COUNT")
                    putJsonArray("tags") {
                        tags?.forEach {
                            add(it)
                        }
                    }
                }
            }
        }
        return graphQL.getTopGames(headers, json)
    }

    suspend fun loadTopStreams(headers: Map<String, String>, tags: List<String>?, limit: Int?, cursor: String?): StreamsResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "75a4899f0a765cc08576125512f710e157b147897c06f96325de72d4c5a64890")
                    put("version", 1)
                }
            }
            put("operationName", "BrowsePage_Popular")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("includeIsDJ", true)
                put("limit", limit)
                put("platformType", "all")
                put("sortTypeIsRecency", false)
                putJsonObject("options") {
                    putJsonArray("freeformTags") {
                        tags?.forEach {
                            add(it)
                        }
                    }
                    put("sort", "VIEWER_COUNT")
                }
            }
        }
        return graphQL.getTopStreams(headers, json)
    }

    suspend fun loadGameStreams(headers: Map<String, String>, gameSlug: String?, sort: String?, tags: List<String>?, limit: Int?, cursor: String?): GameStreamsResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "c7c9d5aad09155c4161d2382092dc44610367f3536aac39019ec2582ae5065f9")
                    put("version", 1)
                }
            }
            put("operationName", "DirectoryPage_Game")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("includeIsDJ", true)
                put("limit", limit)
                put("slug", gameSlug)
                put("sortTypeIsRecency", false)
                putJsonObject("options") {
                    putJsonArray("freeformTags") {
                        tags?.forEach {
                            add(it)
                        }
                    }
                    put("sort", sort)
                }
            }
        }
        return graphQL.getGameStreams(headers, json)
    }

    suspend fun loadGameVideos(headers: Map<String, String>, gameSlug: String?, type: String?, sort: String?, limit: Int?, cursor: String?): GameVideosResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "b1b02043611ce6f315eb37cb5ecfd0dab38ffeeab1958dfbe538787cc14d5fc3")
                    put("version", 1)
                }
            }
            put("operationName", "DirectoryVideos_Game")
            putJsonObject("variables") {
                if (type != null) {
                    put("broadcastTypes", type)
                }
                put("followedCursor", cursor)
                put("slug", gameSlug)
                put("videoLimit", limit)
                put("videoSort", sort)
            }
        }
        return graphQL.getGameVideos(headers, json)
    }

    suspend fun loadGameClips(headers: Map<String, String>, gameSlug: String?, sort: String?, limit: Int?, cursor: String?): GameClipsResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "b814240ae1e920af4573e9a9f0b04951528cb5ee60a7c47a484edae15068f26b")
                    put("version", 1)
                }
            }
            put("operationName", "ClipsCards__Game")
            putJsonObject("variables") {
                putJsonObject("criteria") {
                    put("filter", sort)
                }
                put("cursor", cursor)
                put("categorySlug", gameSlug)
                put("limit", limit)
            }
        }
        return graphQL.getGameClips(headers, json)
    }

    suspend fun loadChannelVideos(headers: Map<String, String>, channelLogin: String?, type: String?, sort: String?, limit: Int?, cursor: String?): ChannelVideosResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "08eed732ca804e536f9262c6ce87e0e15f07d6d3c047e8e5d7a461afd5a66a00")
                    put("version", 1)
                }
            }
            put("operationName", "FilterableVideoTower_Videos")
            putJsonObject("variables") {
                put("broadcastType", type)
                put("cursor", cursor)
                put("channelOwnerLogin", channelLogin)
                put("limit", limit)
                put("videoSort", sort)
            }
        }
        return graphQL.getChannelVideos(headers, json)
    }

    suspend fun loadChannelClips(headers: Map<String, String>, channelLogin: String?, sort: String?, limit: Int?, cursor: String?): ChannelClipsResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "fa3122f0b8fbd980f247a0f885c8097c154debc595dbcb815265669ea410c2eb")
                    put("version", 1)
                }
            }
            put("operationName", "ClipsCards__User")
            putJsonObject("variables") {
                putJsonObject("criteria") {
                    put("filter", sort)
                }
                put("cursor", cursor)
                put("login", channelLogin)
                put("limit", limit)
            }
        }
        return graphQL.getChannelClips(headers, json)
    }

    suspend fun loadSearchChannels(headers: Map<String, String>, query: String?, cursor: String?): SearchChannelsResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f6c2575aee4418e8a616e03364d8bcdbf0b10a5c87b59f523569dacc963e8da5")
                    put("version", 1)
                }
            }
            put("operationName", "SearchResultsPage_SearchResults")
            putJsonObject("variables") {
                putJsonObject("options") {
                    putJsonArray("targets") {
                        add(buildJsonObject {
                            put("cursor", cursor)
                            put("index", "CHANNEL")
                        })
                    }
                }
                put("includeIsDJ", true)
                put("query", query)
            }
        }
        return graphQL.getSearchChannels(headers, json)
    }

    suspend fun loadSearchGames(headers: Map<String, String>, query: String?, cursor: String?): SearchGamesResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f6c2575aee4418e8a616e03364d8bcdbf0b10a5c87b59f523569dacc963e8da5")
                    put("version", 1)
                }
            }
            put("operationName", "SearchResultsPage_SearchResults")
            putJsonObject("variables") {
                putJsonObject("options") {
                    putJsonArray("targets") {
                        add(buildJsonObject {
                            put("cursor", cursor)
                            put("index", "GAME")
                        })
                    }
                }
                put("includeIsDJ", true)
                put("query", query)
            }
        }
        return graphQL.getSearchGames(headers, json)
    }

    suspend fun loadSearchVideos(headers: Map<String, String>, query: String?, cursor: String?): SearchVideosResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f6c2575aee4418e8a616e03364d8bcdbf0b10a5c87b59f523569dacc963e8da5")
                    put("version", 1)
                }
            }
            put("operationName", "SearchResultsPage_SearchResults")
            putJsonObject("variables") {
                putJsonObject("options") {
                    putJsonArray("targets") {
                        add(buildJsonObject {
                            put("cursor", cursor)
                            put("index", "VOD")
                        })
                    }
                }
                put("includeIsDJ", true)
                put("query", query)
            }
        }
        return graphQL.getSearchVideos(headers, json)
    }

    suspend fun loadFreeformTags(headers: Map<String, String>, query: String?, limit: Int?): SearchStreamTagsResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "8bc91a618bb5f0c5f9bc19195028c9f4a6a1b8651cf5bd8e4f2408124cdf465a")
                    put("version", 1)
                }
            }
            put("operationName", "SearchFreeformTags")
            putJsonObject("variables") {
                put("first", limit)
                put("userQuery", query ?: "")
            }
        }
        return graphQL.getFreeformTags(headers, json)
    }

    suspend fun loadGameTags(headers: Map<String, String>, query: String?, limit: Int?): SearchGameTagsResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "b4cb189d8d17aadf29c61e9d7c7e7dcfc932e93b77b3209af5661bffb484195f")
                    put("version", 1)
                }
            }
            put("operationName", "SearchCategoryTags")
            putJsonObject("variables") {
                put("limit", limit)
                put("userQuery", query ?: "")
            }
        }
        return graphQL.getGameTags(headers, json)
    }

    suspend fun loadChatBadges(headers: Map<String, String>, channelLogin: String?): BadgesResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "dd0997370fb7ca288bc52a96a9a7e3222c75c4a9a9b03df17d779666f07f7529")
                    put("version", 1)
                }
            }
            put("operationName", "ChatList_Badges")
            putJsonObject("variables") {
                put("channelLogin", channelLogin)
            }
        }
        return graphQL.getChatBadges(headers, json)
    }

    suspend fun loadGlobalCheerEmotes(headers: Map<String, String>): GlobalCheerEmotesResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "6a265b86f3be1c8d11bdcf32c183e106028c6171e985cc2584d15f7840f5fee6")
                    put("version", 1)
                }
            }
            put("operationName", "BitsConfigContext_Global")
        }
        return graphQL.getGlobalCheerEmotes(headers, json)
    }

    suspend fun loadChannelCheerEmotes(headers: Map<String, String>, channelLogin: String?): ChannelCheerEmotesResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "d897953c76165a0d2a12b57c9c013a77b3cf02b5c153645e1e1631f763bf1eb5")
                    put("version", 1)
                }
            }
            put("operationName", "BitsConfigContext_Channel")
            putJsonObject("variables") {
                put("login", channelLogin)
            }
        }
        return graphQL.getChannelCheerEmotes(headers, json)
    }

    private fun getVideoMessagesRequestBody(videoId: String?, offset: Int?, cursor: String?): JsonObject {
        return buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "b70a3591ff0f4e0313d126c6a1502d79a1c02baebb288227c582044aa76adf6a")
                    put("version", 1)
                }
            }
            put("operationName", "VideoCommentsByOffsetOrCursor")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("contentOffsetSeconds", offset)
                put("videoID", videoId)
            }
        }
    }

    suspend fun loadVideoMessages(headers: Map<String, String>, videoId: String?, offset: Int? = null, cursor: String? = null): VideoMessagesResponse {
        return graphQL.getVideoMessages(headers, getVideoMessagesRequestBody(videoId, offset, cursor))
    }

    suspend fun loadVideoMessagesDownload(headers: Map<String, String>, videoId: String?, offset: Int? = null, cursor: String? = null): JsonElement {
        return graphQL.getVideoMessagesDownload(headers, getVideoMessagesRequestBody(videoId, offset, cursor))
    }

    suspend fun loadVideoGames(headers: Map<String, String>, videoId: String?): VideoGamesResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "71835d5ef425e154bf282453a926d99b328cdc5e32f36d3a209d0f4778b41203")
                    put("version", 1)
                }
            }
            put("operationName", "VideoPlayer_ChapterSelectButtonVideo")
            putJsonObject("variables") {
                put("videoID", videoId)
            }
        }
        return graphQL.getVideoGames(headers, json)
    }

    suspend fun loadChannelViewerList(headers: Map<String, String>, channelLogin: String?): ChannelViewerListResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "2e71a3399875770c1e5d81a9774d9803129c44cf8f6bad64973aa0d239a88caf")
                    put("version", 1)
                }
            }
            put("operationName", "CommunityTab")
            putJsonObject("variables") {
                put("login", channelLogin)
            }
        }
        return graphQL.getChannelViewerList(headers, json)
    }

    suspend fun loadViewerCount(headers: Map<String, String>, channelLogin: String?): ViewerCountResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "00b11c9c428f79ae228f30080a06ffd8226a1f068d6f52fbc057cbde66e994c2")
                    put("version", 1)
                }
            }
            put("operationName", "UseViewCount")
            putJsonObject("variables") {
                put("channelLogin", channelLogin)
            }
        }
        return graphQL.getViewerCount(headers, json)
    }

    suspend fun loadEmoteCard(headers: Map<String, String>, emoteId: String?): EmoteCardResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "af523cd8807a390830351eb5362633a2c718d6b5cd05494a894af86770c817d6")
                    put("version", 1)
                }
            }
            put("operationName", "EmoteCard")
            putJsonObject("variables") {
                put("emoteID", emoteId)
                put("octaneEnabled", true)
                put("artistEnabled", true)
            }
        }
        return graphQL.getEmoteCard(headers, json)
    }

    suspend fun loadChannelPanel(headers: Map<String, String>, channelId: String?): Response<ResponseBody> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "236b0ec07489e5172ee1327d114172f27aceca206a1a8053106d60926a7f622e")
                    put("version", 1)
                }
            }
            put("operationName", "ChannelPanels")
            putJsonObject("variables") {
                put("id", channelId)
            }
        }
        return graphQL.getChannelPanel(headers, json)
    }

    suspend fun loadFollowedStreams(headers: Map<String, String>, limit: Int?, cursor: String?): FollowedStreamsResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "ecadcf350272dde399a63385cf888903d7fcd4c8fc6809a8469fe3753579d1c6")
                    put("version", 1)
                }
            }
            put("operationName", "FollowingLive_CurrentUser")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("includeIsDJ", true)
                put("limit", limit)
            }
        }
        return graphQL.getFollowedStreams(headers, json)
    }

    suspend fun loadFollowedVideos(headers: Map<String, String>, limit: Int?, cursor: String?): FollowedVideosResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "11d0ddb94121afab8fa8b641e01f038db35892f95b4e4b9e5380eaa33d5e4a8c")
                    put("version", 1)
                }
            }
            put("operationName", "FollowedVideos_CurrentUser")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("limit", limit)
            }
        }
        return graphQL.getFollowedVideos(headers, json)
    }

    suspend fun loadFollowedChannels(headers: Map<String, String>, limit: Int?, cursor: String?): FollowedChannelsResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "eecf815273d3d949e5cf0085cc5084cd8a1b5b7b6f7990cf43cb0beadf546907")
                    put("version", 1)
                }
            }
            put("operationName", "ChannelFollows")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("limit", limit)
                put("order", "DESC")
            }
        }
        return graphQL.getFollowedChannels(headers, json)
    }

    suspend fun loadFollowedGames(headers: Map<String, String>, limit: Int?): FollowedGamesResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f3c5d45175d623ed3d5ff4ca4c7de379ea6a1a4852236087dc1b81b7dbfd3114")
                    put("version", 1)
                }
            }
            put("operationName", "FollowingGames_CurrentUser")
            putJsonObject("variables") {
                put("limit", limit)
                put("type", "ALL")
            }
        }
        return graphQL.getFollowedGames(headers, json)
    }

    suspend fun loadFollowUser(headers: Map<String, String>, userId: String?): ErrorResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "800e7346bdf7e5278a3c1d3f21b2b56e2639928f86815677a7126b093b2fdd08")
                    put("version", 1)
                }
            }
            put("operationName", "FollowButton_FollowUser")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("disableNotifications", false)
                    put("targetID", userId)
                }
            }
        }
        return graphQL.getFollowUser(headers, json)
    }

    suspend fun loadUnfollowUser(headers: Map<String, String>, userId: String?): ErrorResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f7dae976ebf41c755ae2d758546bfd176b4eeb856656098bb40e0a672ca0d880")
                    put("version", 1)
                }
            }
            put("operationName", "FollowButton_UnfollowUser")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("targetID", userId)
                }
            }
        }
        return graphQL.getUnfollowUser(headers, json)
    }

    suspend fun loadToggleNotificationsUser(headers: Map<String, String>, userId: String?, disableNotifications: Boolean): ErrorResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "2319a2486246f63b13ffc0d1c317c89df177150185352791a81eb7bced0128a1")
                    put("version", 1)
                }
            }
            put("operationName", "LiveNotificationsToggle_ToggleNotifications")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("disableNotifications", disableNotifications)
                    put("targetID", userId)
                }
            }
        }
        return graphQL.getToggleNotificationsUser(headers, json)
    }

    suspend fun loadFollowGame(headers: Map<String, String>, gameId: String?): ErrorResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "b846b65ba4bc9a3561dbe2d069d95deed9b9e031bcfda2482d1bedd84a1c2eb3")
                    put("version", 1)
                }
            }
            put("operationName", "FollowGameButton_FollowGame")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("gameID", gameId)
                }
            }
        }
        return graphQL.getFollowGame(headers, json)
    }

    suspend fun loadUnfollowGame(headers: Map<String, String>, gameId: String?): ErrorResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "811e02e396ebba0664f21ff002f2eff3c6f57e8af9aedb4f4dfa77cefd0db43d")
                    put("version", 1)
                }
            }
            put("operationName", "FollowGameButton_UnfollowGame")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("gameID", gameId)
                }
            }
        }
        return graphQL.getUnfollowGame(headers, json)
    }

    suspend fun loadFollowingUser(headers: Map<String, String>, userLogin: String?): FollowingUserResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "834a75e1c06cffada00f0900664a5033e392f6fb655fae8d2e25b21b340545a9")
                    put("version", 1)
                }
            }
            put("operationName", "ChannelSupportButtons")
            putJsonObject("variables") {
                put("channelLogin", userLogin)
            }
        }
        return graphQL.getFollowingUser(headers, json)
    }

    suspend fun loadFollowingGame(headers: Map<String, String>, gameName: String?): FollowingGameResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "cfeda60899b6b867b2d7f30c8556778c4a9cc8268bd1aadd9f88134a0f642a02")
                    put("version", 1)
                }
            }
            put("operationName", "FollowGameButton_Game")
            putJsonObject("variables") {
                put("name", gameName)
            }
        }
        return graphQL.getFollowingGame(headers, json)
    }

    suspend fun loadChannelPointsContext(headers: Map<String, String>, channelLogin: String?): ChannelPointContextResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "374314de591e69925fce3ddc2bcf085796f56ebb8cad67a0daa3165c03adc345")
                    put("version", 1)
                }
            }
            put("operationName", "ChannelPointsContext")
            putJsonObject("variables") {
                put("channelLogin", channelLogin)
            }
        }
        return graphQL.getChannelPointsContext(headers, json)
    }

    suspend fun loadClaimPoints(headers: Map<String, String>, channelId: String?, claimId: String?): ErrorResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "46aaeebe02c99afdf4fc97c7c0cba964124bf6b0af229395f1f6d1feed05b3d0")
                    put("version", 1)
                }
            }
            put("operationName", "ClaimCommunityPoints")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("claimID", claimId)
                }
            }
        }
        return graphQL.getClaimPoints(headers, json)
    }

    suspend fun loadJoinRaid(headers: Map<String, String>, raidId: String?): Response<ErrorResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "c6a332a86d1087fbbb1a8623aa01bd1313d2386e7c63be60fdb2d1901f01a4ae")
                    put("version", 1)
                }
            }
            put("operationName", "JoinRaid")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("raidID", raidId)
                }
            }
        }
        return graphQL.getJoinRaid(headers, json)
    }

    suspend fun loadUserEmotes(headers: Map<String, String>, channelId: String?, cursor: String?): UserEmotesResponse {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "6c45e0ecaa823cc7db3ecdd1502af2223c775bdcfb0f18a3a0ce9a0b7db8ef6c")
                    put("version", 1)
                }
            }
            put("operationName", "AvailableEmotesForChannelPaginated")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("channelID", channelId)
                put("pageLimit", 350)
                put("withOwner", true)
            }
        }
        return graphQL.getUserEmotes(headers, json)
    }

    suspend fun sendAnnouncement(headers: Map<String, String>, channelId: String?, message: String?, color: String?): Response<ErrorResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f9e37b572ceaca1475d8d50805ae64d6eb388faf758556b2719f44d64e5ba791")
                    put("version", 1)
                }
            }
            put("operationName", "SendAnnouncementMessage")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("message", message)
                    put("color", color)
                }
            }
        }
        return graphQL.sendAnnouncement(headers, json)
    }

    suspend fun banUser(headers: Map<String, String>, channelId: String?, targetLogin: String?, duration: String?, reason: String?): Response<ErrorResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "d7be2d2e1e22813c1c2f3d9d5bf7e425d815aeb09e14001a5f2c140b93f6fb67")
                    put("version", 1)
                }
            }
            put("operationName", "Chat_BanUserFromChatRoom")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("bannedUserLogin", targetLogin)
                    put("expiresIn", duration)
                    put("reason", reason)
                }
            }
        }
        return graphQL.banUser(headers, json)
    }

    suspend fun unbanUser(headers: Map<String, String>, channelId: String?, targetLogin: String?): Response<ErrorResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "bee22da7ae03569eb9ae41ef857fd1bb75507d4984d764a81fe8775accac71bd")
                    put("version", 1)
                }
            }
            put("operationName", "Chat_UnbanUserFromChatRoom")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("bannedUserLogin", targetLogin)
                }
            }
        }
        return graphQL.unbanUser(headers, json)
    }

    suspend fun updateChatColor(headers: Map<String, String>, color: String?): Response<ErrorResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "0371259a74a3db4ff4bf4473d998d8ae8e4f135b20403323691d434f2790e081")
                    put("version", 1)
                }
            }
            put("operationName", "Chat_UpdateChatColor")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("color", color)
                }
            }
        }
        return graphQL.updateChatColor(headers, json)
    }

    suspend fun createStreamMarker(headers: Map<String, String>, channelLogin: String?): Response<ErrorResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "c65f8b33e3bcccf2b16057e8f445311d213ecf8729f842ccdc71908231fa9a78")
                    put("version", 1)
                }
            }
            put("operationName", "VideoMarkersChatCommand")
            putJsonObject("variables") {
                put("channelLogin", channelLogin)
            }
        }
        return graphQL.createStreamMarker(headers, json)
    }

    suspend fun getModerators(headers: Map<String, String>, channelLogin: String?): Response<ModeratorsResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "cb912a7e0789e0f8a4c85c25041a08324475831024d03d624172b59498caf085")
                    put("version", 1)
                }
            }
            put("operationName", "Mods")
            putJsonObject("variables") {
                put("login", channelLogin)
            }
        }
        return graphQL.getModerators(headers, json)
    }

    suspend fun addModerator(headers: Map<String, String>, channelId: String?, targetLogin: String?): Response<ErrorResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "46da4ec4229593fe4b1bce911c75625c299638e228262ff621f80d5067695a8a")
                    put("version", 1)
                }
            }
            put("operationName", "ModUser")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("targetLogin", targetLogin)
                }
            }
        }
        return graphQL.addModerator(headers, json)
    }

    suspend fun removeModerator(headers: Map<String, String>, channelId: String?, targetLogin: String?): Response<ErrorResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "1ed42ccb3bc3a6e79f51e954a2df233827f94491fbbb9bd05b22b1aaaf219b8b")
                    put("version", 1)
                }
            }
            put("operationName", "UnmodUser")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("targetLogin", targetLogin)
                }
            }
        }
        return graphQL.removeModerator(headers, json)
    }

    suspend fun startRaid(headers: Map<String, String>, channelId: String?, targetId: String?): Response<ErrorResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f4fc7ac482599d81dfb6aa37100923c8c9edeea9ca2be854102a6339197f840a")
                    put("version", 1)
                }
            }
            put("operationName", "chatCreateRaid")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("sourceID", channelId)
                    put("targetID", targetId)
                }
            }
        }
        return graphQL.startRaid(headers, json)
    }

    suspend fun cancelRaid(headers: Map<String, String>, channelId: String?): Response<ErrorResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "c388b89e7616a11a8a07b75e3d7bbe7278d37c3c46f43d7c8d4d0262edc00cd9")
                    put("version", 1)
                }
            }
            put("operationName", "chatCancelRaid")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("sourceID", channelId)
                }
            }
        }
        return graphQL.cancelRaid(headers, json)
    }

    suspend fun getVips(headers: Map<String, String>, channelLogin: String?): Response<VipsResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "612a574d07afe5db2f9e878e290225224a0b955e65b5d1235dcd4b68ff668218")
                    put("version", 1)
                }
            }
            put("operationName", "VIPs")
            putJsonObject("variables") {
                put("login", channelLogin)
            }
        }
        return graphQL.getVips(headers, json)
    }

    suspend fun addVip(headers: Map<String, String>, channelId: String?, targetLogin: String?): Response<ErrorResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "e8c397f1ed8b1fdbaa201eedac92dd189ecfb2d828985ec159d4ae77f9920170")
                    put("version", 1)
                }
            }
            put("operationName", "VIPUser")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("granteeLogin", targetLogin)
                }
            }
        }
        return graphQL.addVip(headers, json)
    }

    suspend fun removeVip(headers: Map<String, String>, channelId: String?, targetLogin: String?): Response<ErrorResponse> {
        val json = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "2ce4fcdf6667d013aa1f820010e699d1d4abdda55e26539ecf4efba8aff2d661")
                    put("version", 1)
                }
            }
            put("operationName", "UnVIPUser")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("revokeeLogin", targetLogin)
                }
            }
        }
        return graphQL.removeVip(headers, json)
    }
}