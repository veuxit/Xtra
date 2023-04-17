package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.api.GraphQLApi
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelClipsDataResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelViewerListDataResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelPointsContextDataResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ChatBadgesDataResponse
import com.github.andreyasadchy.xtra.model.gql.chat.EmoteCardResponse
import com.github.andreyasadchy.xtra.model.gql.chat.GlobalCheerEmotesDataResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ModeratorsDataResponse
import com.github.andreyasadchy.xtra.model.gql.chat.UserEmotesDataResponse
import com.github.andreyasadchy.xtra.model.gql.chat.VipsDataResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipDataResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipVideoResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowDataResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedChannelsDataResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedGamesDataResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedStreamsDataResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowingGameDataResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowingUserDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameClipsDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameStreamsDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.playlist.PlaybackAccessTokenResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchChannelDataResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchGameDataResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.stream.StreamsDataResponse
import com.github.andreyasadchy.xtra.model.gql.stream.ViewersDataResponse
import com.github.andreyasadchy.xtra.model.gql.tag.FreeformTagDataResponse
import com.github.andreyasadchy.xtra.model.gql.tag.TagGameDataResponse
import com.github.andreyasadchy.xtra.model.gql.video.VideoGamesDataResponse
import com.github.andreyasadchy.xtra.model.gql.video.VideoMessagesDataResponse
import com.github.andreyasadchy.xtra.model.query.BadgesQueryResponse
import com.github.andreyasadchy.xtra.model.query.GameBoxArtQueryResponse
import com.github.andreyasadchy.xtra.model.query.GameClipsQueryResponse
import com.github.andreyasadchy.xtra.model.query.GameStreamsQueryResponse
import com.github.andreyasadchy.xtra.model.query.GameVideosQueryResponse
import com.github.andreyasadchy.xtra.model.query.SearchChannelsQueryResponse
import com.github.andreyasadchy.xtra.model.query.SearchGamesQueryResponse
import com.github.andreyasadchy.xtra.model.query.SearchStreamsQueryResponse
import com.github.andreyasadchy.xtra.model.query.SearchVideosQueryResponse
import com.github.andreyasadchy.xtra.model.query.TopGamesQueryResponse
import com.github.andreyasadchy.xtra.model.query.TopStreamsQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserBadgesQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserChannelPageQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserCheerEmotesQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserClipsQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserEmotesQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserFollowedGamesQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserFollowedStreamsQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserFollowedUsersQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserFollowedVideosQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserMessageClickedQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserResultIDQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserResultLoginQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserVideosQueryResponse
import com.github.andreyasadchy.xtra.model.query.UsersLastBroadcastQueryResponse
import com.github.andreyasadchy.xtra.model.query.UsersStreamQueryResponse
import com.github.andreyasadchy.xtra.model.query.UsersTypeQueryResponse
import com.github.andreyasadchy.xtra.model.query.VideoQueryResponse
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GraphQLRepository"

@Singleton
class GraphQLRepository @Inject constructor(private val graphQL: GraphQLApi) {

    suspend fun loadQueryBadges(clientId: String?, query: String?, variables: JsonObject): BadgesQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryBadges(clientId, json)
    }

    suspend fun loadQueryGameBoxArt(clientId: String?, query: String?, variables: JsonObject): GameBoxArtQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryGameBoxArt(clientId, json)
    }

    suspend fun loadQueryGameClips(clientId: String?, query: String?, variables: JsonObject): GameClipsQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryGameClips(clientId, json)
    }

    suspend fun loadQueryGameStreams(clientId: String?, query: String?, variables: JsonObject): GameStreamsQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryGameStreams(clientId, json)
    }

    suspend fun loadQueryGameVideos(clientId: String?, query: String?, variables: JsonObject): GameVideosQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryGameVideos(clientId, json)
    }

    suspend fun loadQuerySearchChannels(clientId: String?, query: String?, variables: JsonObject): SearchChannelsQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQuerySearchChannels(clientId, json)
    }

    suspend fun loadQuerySearchGames(clientId: String?, query: String?, variables: JsonObject): SearchGamesQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQuerySearchGames(clientId, json)
    }

    suspend fun loadQuerySearchStreams(clientId: String?, query: String?, variables: JsonObject): SearchStreamsQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQuerySearchStreams(clientId, json)
    }

    suspend fun loadQuerySearchVideos(clientId: String?, query: String?, variables: JsonObject): SearchVideosQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQuerySearchVideos(clientId, json)
    }

    suspend fun loadQueryTopGames(clientId: String?, query: String?, variables: JsonObject): TopGamesQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryTopGames(clientId, json)
    }

    suspend fun loadQueryTopStreams(clientId: String?, query: String?, variables: JsonObject): TopStreamsQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryTopStreams(clientId, json)
    }

    suspend fun loadQueryUser(clientId: String?, query: String?, variables: JsonObject): UserQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUser(clientId, json)
    }

    suspend fun loadQueryUserBadges(clientId: String?, query: String?, variables: JsonObject): UserBadgesQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUserBadges(clientId, json)
    }

    suspend fun loadQueryUserChannelPage(clientId: String?, query: String?, variables: JsonObject): UserChannelPageQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUserChannelPage(clientId, json)
    }

    suspend fun loadQueryUserCheerEmotes(clientId: String?, query: String?, variables: JsonObject): UserCheerEmotesQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getUserCheerEmotes(clientId, json)
    }

    suspend fun loadQueryUserClips(clientId: String?, query: String?, variables: JsonObject): UserClipsQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUserClips(clientId, json)
    }

    suspend fun loadQueryUserEmotes(clientId: String?, token: String?, query: String?, variables: JsonObject): UserEmotesQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUserEmotes(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadQueryUserFollowedGames(clientId: String?, token: String?, query: String?, variables: JsonObject): UserFollowedGamesQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUserFollowedGames(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadQueryUserFollowedStreams(clientId: String?, token: String?, query: String?, variables: JsonObject): UserFollowedStreamsQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUserFollowedStreams(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadQueryUserFollowedUsers(clientId: String?, token: String?, query: String?, variables: JsonObject): UserFollowedUsersQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUserFollowedUsers(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadQueryUserFollowedVideos(clientId: String?, token: String?, query: String?, variables: JsonObject): UserFollowedVideosQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUserFollowedVideos(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadQueryUserMessageClicked(clientId: String?, query: String?, variables: JsonObject): UserMessageClickedQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUserMessageClicked(clientId, json)
    }

    suspend fun loadQueryUserVideos(clientId: String?, query: String?, variables: JsonObject): UserVideosQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUserVideos(clientId, json)
    }

    suspend fun loadQueryUsersLastBroadcast(clientId: String?, query: String?, variables: JsonObject): UsersLastBroadcastQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUsersLastBroadcast(clientId, json)
    }

    suspend fun loadQueryUsersStream(clientId: String?, query: String?, variables: JsonObject): UsersStreamQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUsersStream(clientId, json)
    }

    suspend fun loadQueryUsersType(clientId: String?, query: String?, variables: JsonObject): UsersTypeQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUsersType(clientId, json)
    }

    suspend fun loadQueryVideo(clientId: String?, query: String?, variables: JsonObject): VideoQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryVideo(clientId, json)
    }

    suspend fun loadQueryUserResultID(clientId: String?, query: String?, variables: JsonObject): UserResultIDQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUserResultID(clientId, json)
    }

    suspend fun loadQueryUserResultLogin(clientId: String?, query: String?, variables: JsonObject): UserResultLoginQueryResponse {
        val json = JsonObject().apply {
            add("variables", variables)
            addProperty("query", query)
        }
        return graphQL.getQueryUserResultLogin(clientId, json)
    }

    suspend fun loadPlaybackAccessToken(clientId: String?, headers: Map<String, String>, login: String? = null, vodId: String? = null, playerType: String?): PlaybackAccessTokenResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "0828119ded1c13477966434e15800ff57ddacf13ba1911c129dc2200705b0712")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "PlaybackAccessToken")
            add("variables", JsonObject().apply {
                addProperty("isLive", !login.isNullOrBlank())
                addProperty("login", login ?: "")
                addProperty("isVod", !vodId.isNullOrBlank())
                addProperty("vodID", vodId ?: "")
                addProperty("playerType", playerType)
            })
        }
        return graphQL.getPlaybackAccessToken(clientId, headers, json)
    }

    suspend fun loadClipUrls(clientId: String?, slug: String?): Map<String, String>? = withContext(Dispatchers.IO) {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "36b89d2507fce29e5ca551df756d27c1cfe079e2609642b4390aa4c35796eb11")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "VideoAccessToken_Clip")
            add("variables", JsonObject().apply {
                addProperty("slug", slug)
            })
        }
        val response = graphQL.getClipUrls(clientId, json)
        response.body()?.data?.withIndex()?.associateBy({
            if (!it.value.quality.isNullOrBlank()) {
                if ((it.value.frameRate ?: 0) < 60) {
                    "${it.value.quality}p"
                } else {
                    "${it.value.quality}p${it.value.frameRate}"
                }
            } else {
                it.index.toString()
            }
        }, { it.value.url })
    }

    suspend fun loadClipData(clientId: String?, slug: String?): ClipDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "16d402536bdd88b9db9a7cc87da5769607676abf22ad46b6cfab57a2b8b0b20e")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "ChannelClipCore")
            add("variables", JsonObject().apply {
                addProperty("clipSlug", slug)
            })
        }
        return graphQL.getClipData(clientId, json)
    }

    suspend fun loadClipVideo(clientId: String?, slug: String?): ClipVideoResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "9aa558e066a22227c5ef2c0a8fded3aaa57d35181ad15f63df25bff516253a90")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "ChatClip")
            add("variables", JsonObject().apply {
                addProperty("clipSlug", slug)
            })
        }
        return graphQL.getClipVideo(clientId, json)
    }

    suspend fun loadTopGames(clientId: String?, tags: List<String>?, limit: Int?, cursor: String?): GameDataResponse {
        val array = JsonArray()
        if (tags != null) {
            for (i in tags) {
                array.add(i)
            }
        }
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "1d1914ca3cbfaa607ecd5595b2e305e96acf987c8f25328f7713b25f604c4668")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "BrowsePage_AllDirectories")
            add("variables", JsonObject().apply {
                addProperty("cursor", cursor)
                addProperty("limit", limit)
                add("options", JsonObject().apply {
                    addProperty("sort", "VIEWER_COUNT")
                    add("tags", array)
                })
            })
        }
        return graphQL.getTopGames(clientId, json)
    }

    suspend fun loadTopStreams(clientId: String?, tags: List<String>?, limit: Int?, cursor: String?): StreamsDataResponse {
        val array = JsonArray()
        if (tags != null) {
            for (i in tags) {
                array.add(i)
            }
        }
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "b32fa28ffd43e370b42de7d9e6e3b8a7ca310035fdbb83932150443d6b693e4d")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "BrowsePage_Popular")
            add("variables", JsonObject().apply {
                addProperty("cursor", cursor)
                addProperty("limit", limit)
                addProperty("platformType", "all")
                addProperty("sortTypeIsRecency", false)
                add("options", JsonObject().apply {
                    add("freeformTags", array)
                    addProperty("sort", "VIEWER_COUNT")
                })
            })
        }
        return graphQL.getTopStreams(clientId, json)
    }

    suspend fun loadGameStreams(clientId: String?, gameName: String?, sort: String?, tags: List<String>?, limit: Int?, cursor: String?): GameStreamsDataResponse {
        val array = JsonArray()
        if (tags != null) {
            for (i in tags) {
                array.add(i)
            }
        }
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "df4bb6cc45055237bfaf3ead608bbafb79815c7100b6ee126719fac3762ddf8b")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "DirectoryPage_Game")
            add("variables", JsonObject().apply {
                addProperty("cursor", cursor)
                addProperty("limit", limit)
                addProperty("name", gameName)
                addProperty("sortTypeIsRecency", false)
                add("options", JsonObject().apply {
                    add("freeformTags", array)
                    addProperty("sort", sort)
                })
            })
        }
        return graphQL.getGameStreams(clientId, json)
    }

    suspend fun loadGameVideos(clientId: String?, gameName: String?, type: String?, sort: String?, limit: Int?, cursor: String?): GameVideosDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "c04a45b3adfcfacdff2bf4c4172ca4904870d62d6d19f3d490705c5d0a9e511e")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "DirectoryVideos_Game")
            add("variables", JsonObject().apply {
                if (type != null) {
                    addProperty("broadcastTypes", type)
                }
                addProperty("followedCursor", cursor)
                addProperty("gameName", gameName)
                addProperty("videoLimit", limit)
                addProperty("videoSort", sort)
            })
        }
        return graphQL.getGameVideos(clientId, json)
    }

    suspend fun loadGameClips(clientId: String?, gameName: String?, sort: String?, limit: Int?, cursor: String?): GameClipsDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "0d8d0eba9fc7ef77de54a7d933998e21ad7a1274c867ec565ac14ffdce77b1f9")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "ClipsCards__Game")
            add("variables", JsonObject().apply {
                add("criteria", JsonObject().apply {
                    addProperty("filter", sort)
                })
                addProperty("cursor", cursor)
                addProperty("gameName", gameName)
                addProperty("limit", limit)
            })
        }
        return graphQL.getGameClips(clientId, json)
    }

    suspend fun loadChannelVideos(clientId: String?, channelLogin: String?, type: String?, sort: String?, limit: Int?, cursor: String?): ChannelVideosDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "a937f1d22e269e39a03b509f65a7490f9fc247d7f83d6ac1421523e3b68042cb")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "FilterableVideoTower_Videos")
            add("variables", JsonObject().apply {
                addProperty("broadcastType", type)
                addProperty("cursor", cursor)
                addProperty("channelOwnerLogin", channelLogin)
                addProperty("limit", limit)
                addProperty("videoSort", sort)
            })
        }
        return graphQL.getChannelVideos(clientId, json)
    }

    suspend fun loadChannelClips(clientId: String?, channelLogin: String?, sort: String?, limit: Int?, cursor: String?): ChannelClipsDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "b73ad2bfaecfd30a9e6c28fada15bd97032c83ec77a0440766a56fe0bd632777")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "ClipsCards__User")
            add("variables", JsonObject().apply {
                add("criteria", JsonObject().apply {
                    addProperty("filter", sort)
                })
                addProperty("cursor", cursor)
                addProperty("login", channelLogin)
                addProperty("limit", limit)
            })
        }
        return graphQL.getChannelClips(clientId, json)
    }

    suspend fun loadSearchChannels(clientId: String?, query: String?, cursor: String?): SearchChannelDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "6ea6e6f66006485e41dbe3ebd69d5674c5b22896ce7b595d7fce6411a3790138")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "SearchResultsPage_SearchResults")
            add("variables", JsonObject().apply {
                add("options", JsonObject().apply {
                    add("targets", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("cursor", cursor)
                            addProperty("index", "CHANNEL")
                        })
                    })
                })
                addProperty("query", query)
            })
        }
        return graphQL.getSearchChannels(clientId, json)
    }

    suspend fun loadSearchGames(clientId: String?, query: String?, cursor: String?): SearchGameDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "6ea6e6f66006485e41dbe3ebd69d5674c5b22896ce7b595d7fce6411a3790138")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "SearchResultsPage_SearchResults")
            add("variables", JsonObject().apply {
                add("options", JsonObject().apply {
                    add("targets", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("cursor", cursor)
                            addProperty("index", "GAME")
                        })
                    })
                })
                addProperty("query", query)
            })
        }
        return graphQL.getSearchGames(clientId, json)
    }

    suspend fun loadSearchVideos(clientId: String?, query: String?, cursor: String?): SearchVideosDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "6ea6e6f66006485e41dbe3ebd69d5674c5b22896ce7b595d7fce6411a3790138")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "SearchResultsPage_SearchResults")
            add("variables", JsonObject().apply {
                add("options", JsonObject().apply {
                    add("targets", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("cursor", cursor)
                            addProperty("index", "VOD")
                        })
                    })
                })
                addProperty("query", query)
            })
        }
        return graphQL.getSearchVideos(clientId, json)
    }

    suspend fun loadFreeformTags(clientId: String?, query: String?, limit: Int?): FreeformTagDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "8bc91a618bb5f0c5f9bc19195028c9f4a6a1b8651cf5bd8e4f2408124cdf465a")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "SearchFreeformTags")
            add("variables", JsonObject().apply {
                addProperty("first", limit)
                addProperty("userQuery", query ?: "")
            })
        }
        return graphQL.getFreeformTags(clientId, json)
    }

    suspend fun loadGameTags(clientId: String?, query: String?, limit: Int?): TagGameDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "b4cb189d8d17aadf29c61e9d7c7e7dcfc932e93b77b3209af5661bffb484195f")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "SearchCategoryTags")
            add("variables", JsonObject().apply {
                addProperty("limit", limit)
                addProperty("userQuery", query ?: "")
            })
        }
        return graphQL.getGameTags(clientId, json)
    }

    suspend fun loadChatBadges(clientId: String?, channelLogin: String?): ChatBadgesDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "86f43113c04606e6476e39dcd432dee47c994d77a83e54b732e11d4935f0cd08")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "ChatList_Badges")
            add("variables", JsonObject().apply {
                addProperty("channelLogin", channelLogin)
            })
        }
        return graphQL.getChatBadges(clientId, json)
    }

    suspend fun loadCheerEmotes(clientId: String?, channelLogin: String?, animateGifs: Boolean): List<CheerEmote> {
        val data = mutableListOf<CheerEmote>()
        val tiers = mutableListOf<GlobalCheerEmotesDataResponse.CheerTier>()
        val global = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "6a265b86f3be1c8d11bdcf32c183e106028c6171e985cc2584d15f7840f5fee6")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "BitsConfigContext_Global")
        }
        val response = graphQL.getGlobalCheerEmotes(clientId, global)
        tiers.addAll(response.tiers)
        val channel = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "368aaf9c04d3876cdd0076c105af2cd44b3bfd51a688462152ed4d3a5657e2b9")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "BitsConfigContext_Channel")
            add("variables", JsonObject().apply {
                addProperty("login", channelLogin)
            })
        }
        tiers.addAll(graphQL.getChannelCheerEmotes(clientId, channel).data)
        val background = (response.config.backgrounds.find { it.asString == "dark" } ?: response.config.backgrounds.last()).asString
        val scale = response.config.scales
        val type = (if (animateGifs) {
            response.config.types.find { it.asJsonObject.get("animation").asString == "animated" } ?: response.config.types.find { it.asJsonObject.get("animation").asString == "static" }
        } else {
            response.config.types.find { it.asJsonObject.get("animation").asString == "static" }
        } ?: response.config.types.first()).asJsonObject
        tiers.forEach { tier ->
            val item = response.config.colors.find { it.asJsonObject.get("bits").asInt == tier.tierBits }?.asJsonObject
            if (item != null) {
                val url = tier.template
                    .replaceFirst("PREFIX", tier.prefix)
                    .replaceFirst("BACKGROUND", background)
                    .replaceFirst("ANIMATION", type.get("animation").asString)
                    .replaceFirst("TIER", item.get("bits").asString)
                    .replaceFirst("EXTENSION", type.get("extension").asString)
                data.add(CheerEmote(
                    name = tier.prefix,
                    url1x = (scale.find { it.asString.startsWith("1") })?.asString?.let { url.replaceFirst("SCALE", it) } ?: scale.last()?.asString,
                    url2x = (scale.find { it.asString.startsWith("2") })?.asString?.let { url.replaceFirst("SCALE", it) },
                    url3x = (scale.find { it.asString.startsWith("3") })?.asString?.let { url.replaceFirst("SCALE", it) },
                    url4x = (scale.find { it.asString.startsWith("4") })?.asString?.let { url.replaceFirst("SCALE", it) },
                    type = if (type.get("animation").asString == "animated") "gif" else null,
                    isAnimated = type.get("animation").asString == "animated",
                    minBits = item.get("bits").asInt,
                    color = item.get("color").asString
                ))
            }
        }
        return data
    }

    suspend fun loadVideoMessages(clientId: String?, videoId: String?, offset: Int? = null, cursor: String? = null): VideoMessagesDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "b70a3591ff0f4e0313d126c6a1502d79a1c02baebb288227c582044aa76adf6a")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "VideoCommentsByOffsetOrCursor")
            add("variables", JsonObject().apply {
                addProperty("cursor", cursor)
                addProperty("contentOffsetSeconds", offset)
                addProperty("videoID", videoId)
            })
        }
        return graphQL.getVideoMessages(clientId, json)
    }

    suspend fun loadVideoGames(clientId: String?, videoId: String?): VideoGamesDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "8d2793384aac3773beab5e59bd5d6f585aedb923d292800119e03d40cd0f9b41")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "VideoPlayer_ChapterSelectButtonVideo")
            add("variables", JsonObject().apply {
                addProperty("videoID", videoId)
            })
        }
        return graphQL.getVideoGames(clientId, json)
    }

    suspend fun loadChannelViewerList(clientId: String?, channelLogin: String?): ChannelViewerListDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "2e71a3399875770c1e5d81a9774d9803129c44cf8f6bad64973aa0d239a88caf")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "CommunityTab")
            add("variables", JsonObject().apply {
                addProperty("login", channelLogin)
            })
        }
        return graphQL.getChannelViewerList(clientId, json)
    }

    suspend fun loadViewerCount(clientId: String?, channelLogin: String?): ViewersDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "00b11c9c428f79ae228f30080a06ffd8226a1f068d6f52fbc057cbde66e994c2")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "UseViewCount")
            add("variables", JsonObject().apply {
                addProperty("channelLogin", channelLogin)
            })
        }
        return graphQL.getViewerCount(clientId, json)
    }

    suspend fun loadEmoteCard(clientId: String?, emoteId: String?): EmoteCardResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "556230dd63957761355ba54232c43f4781f31ed6686fc827053b9aa7b199848f")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "EmoteCard")
            add("variables", JsonObject().apply {
                addProperty("emoteID", emoteId)
                addProperty("octaneEnabled", true)
                addProperty("artistEnabled", true)
            })
        }
        return graphQL.getEmoteCard(clientId, json)
    }

    suspend fun loadChannelPanel(clientId: String?, channelId: String?): Response<ResponseBody> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "236b0ec07489e5172ee1327d114172f27aceca206a1a8053106d60926a7f622e")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "ChannelPanels")
            add("variables", JsonObject().apply {
                addProperty("id", channelId)
            })
        }
        return graphQL.getChannelPanel(clientId, json)
    }

    suspend fun loadFollowedStreams(clientId: String?, token: String?, limit: Int?, cursor: String?): FollowedStreamsDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "26fa7fb132379e29dc9dc5757ced2d2259ae0ab69460e2b9a7db9cff60e57cd2")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "FollowingLive_CurrentUser")
            add("variables", JsonObject().apply {
                addProperty("cursor", cursor)
                addProperty("limit", limit)
            })
        }
        return graphQL.getFollowedStreams(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadFollowedVideos(clientId: String?, token: String?, limit: Int?, cursor: String?): FollowedVideosDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "a8e02d4cc25511e9997842c80333e15ba0bb9e11b4199e31c5207317faff9618")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "FollowedVideos_CurrentUser")
            add("variables", JsonObject().apply {
                addProperty("cursor", cursor)
                addProperty("limit", limit)
            })
        }
        return graphQL.getFollowedVideos(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadFollowedChannels(clientId: String?, token: String?, limit: Int?, cursor: String?): FollowedChannelsDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "eecf815273d3d949e5cf0085cc5084cd8a1b5b7b6f7990cf43cb0beadf546907")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "ChannelFollows")
            add("variables", JsonObject().apply {
                addProperty("cursor", cursor)
                addProperty("limit", limit)
                addProperty("order", "DESC")
            })
        }
        return graphQL.getFollowedChannels(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadFollowedGames(clientId: String?, token: String?, limit: Int?): FollowedGamesDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "f41c04483bc94b8a046aadca5b83f144205eb588009147a96cf0d89b88905c96")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "FollowingGames_CurrentUser")
            add("variables", JsonObject().apply {
                addProperty("limit", limit)
                addProperty("type", "ALL")
            })
        }
        return graphQL.getFollowedGames(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadFollowUser(clientId: String?, token: String?, userId: String?): FollowDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "800e7346bdf7e5278a3c1d3f21b2b56e2639928f86815677a7126b093b2fdd08")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "FollowButton_FollowUser")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("disableNotifications", false)
                    addProperty("targetID", userId)
                })
            })
        }
        return graphQL.getFollowUser(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadUnfollowUser(clientId: String?, token: String?, userId: String?): FollowDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "f7dae976ebf41c755ae2d758546bfd176b4eeb856656098bb40e0a672ca0d880")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "FollowButton_UnfollowUser")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("targetID", userId)
                })
            })
        }
        return graphQL.getUnfollowUser(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadFollowGame(clientId: String?, token: String?, gameId: String?): FollowDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "b846b65ba4bc9a3561dbe2d069d95deed9b9e031bcfda2482d1bedd84a1c2eb3")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "FollowGameButton_FollowGame")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("gameID", gameId)
                })
            })
        }
        return graphQL.getFollowGame(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadUnfollowGame(clientId: String?, token: String?, gameId: String?): FollowDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "811e02e396ebba0664f21ff002f2eff3c6f57e8af9aedb4f4dfa77cefd0db43d")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "FollowGameButton_UnfollowGame")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("gameID", gameId)
                })
            })
        }
        return graphQL.getUnfollowGame(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadFollowingUser(clientId: String?, token: String?, userLogin: String?): FollowingUserDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "834a75e1c06cffada00f0900664a5033e392f6fb655fae8d2e25b21b340545a9")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "ChannelSupportButtons")
            add("variables", JsonObject().apply {
                addProperty("channelLogin", userLogin)
            })
        }
        return graphQL.getFollowingUser(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadFollowingGame(clientId: String?, token: String?, gameName: String?): FollowingGameDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "cfeda60899b6b867b2d7f30c8556778c4a9cc8268bd1aadd9f88134a0f642a02")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "FollowGameButton_Game")
            add("variables", JsonObject().apply {
                addProperty("name", gameName)
            })
        }
        return graphQL.getFollowingGame(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadChannelPointsContext(clientId: String?, token: String?, channelLogin: String?): ChannelPointsContextDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "1530a003a7d374b0380b79db0be0534f30ff46e61cffa2bc0e2468a909fbc024")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "ChannelPointsContext")
            add("variables", JsonObject().apply {
                addProperty("channelLogin", channelLogin)
            })
        }
        return graphQL.getChannelPointsContext(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadClaimPoints(clientId: String?, token: String?, channelId: String?, claimId: String?) {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "46aaeebe02c99afdf4fc97c7c0cba964124bf6b0af229395f1f6d1feed05b3d0")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "ClaimCommunityPoints")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("channelID", channelId)
                    addProperty("claimID", claimId)
                })
            })
        }
        return graphQL.getClaimPoints(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadJoinRaid(clientId: String?, token: String?, raidId: String?) {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "c6a332a86d1087fbbb1a8623aa01bd1313d2386e7c63be60fdb2d1901f01a4ae")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "JoinRaid")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("raidID", raidId)
                })
            })
        }
        return graphQL.getJoinRaid(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun loadUserEmotes(clientId: String?, token: String?, channelId: String?): UserEmotesDataResponse {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "b9ce64d02e26c6fe9adbfb3991284224498b295542f9c5a51eacd3610e659cfb")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "AvailableEmotesForChannel")
            add("variables", JsonObject().apply {
                addProperty("channelID", channelId)
                addProperty("withOwner", true)
            })
        }
        return graphQL.getUserEmotes(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun sendAnnouncement(clientId: String?, token: String?, channelId: String?, message: String?, color: String?): Response<JsonElement> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "f9e37b572ceaca1475d8d50805ae64d6eb388faf758556b2719f44d64e5ba791")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "SendAnnouncementMessage")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("channelID", channelId)
                    addProperty("message", message)
                    addProperty("color", color)
                })
            })
        }
        return graphQL.sendAnnouncement(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun banUser(clientId: String?, token: String?, channelId: String?, targetLogin: String?, duration: String?, reason: String?): Response<JsonElement> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "d7be2d2e1e22813c1c2f3d9d5bf7e425d815aeb09e14001a5f2c140b93f6fb67")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "Chat_BanUserFromChatRoom")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("channelID", channelId)
                    addProperty("bannedUserLogin", targetLogin)
                    addProperty("expiresIn", duration)
                    addProperty("reason", reason)
                })
            })
        }
        return graphQL.banUser(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun unbanUser(clientId: String?, token: String?, channelId: String?, targetLogin: String?): Response<JsonElement> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "bee22da7ae03569eb9ae41ef857fd1bb75507d4984d764a81fe8775accac71bd")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "Chat_UnbanUserFromChatRoom")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("channelID", channelId)
                    addProperty("bannedUserLogin", targetLogin)
                })
            })
        }
        return graphQL.unbanUser(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun updateChatColor(clientId: String?, token: String?, color: String?): Response<JsonElement> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "0371259a74a3db4ff4bf4473d998d8ae8e4f135b20403323691d434f2790e081")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "Chat_UpdateChatColor")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("color", color)
                })
            })
        }
        return graphQL.updateChatColor(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun createStreamMarker(clientId: String?, token: String?, channelLogin: String?): Response<JsonElement> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "c65f8b33e3bcccf2b16057e8f445311d213ecf8729f842ccdc71908231fa9a78")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "VideoMarkersChatCommand")
            add("variables", JsonObject().apply {
                addProperty("channelLogin", channelLogin)
            })
        }
        return graphQL.createStreamMarker(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun getModerators(clientId: String?, channelLogin: String?): Response<ModeratorsDataResponse> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "cb912a7e0789e0f8a4c85c25041a08324475831024d03d624172b59498caf085")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "Mods")
            add("variables", JsonObject().apply {
                addProperty("login", channelLogin)
            })
        }
        return graphQL.getModerators(clientId, json)
    }

    suspend fun addModerator(clientId: String?, token: String?, channelId: String?, targetLogin: String?): Response<JsonElement> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "46da4ec4229593fe4b1bce911c75625c299638e228262ff621f80d5067695a8a")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "ModUser")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("channelID", channelId)
                    addProperty("targetLogin", targetLogin)
                })
            })
        }
        return graphQL.addModerator(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun removeModerator(clientId: String?, token: String?, channelId: String?, targetLogin: String?): Response<JsonElement> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "1ed42ccb3bc3a6e79f51e954a2df233827f94491fbbb9bd05b22b1aaaf219b8b")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "UnmodUser")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("channelID", channelId)
                    addProperty("targetLogin", targetLogin)
                })
            })
        }
        return graphQL.removeModerator(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun startRaid(clientId: String?, token: String?, channelId: String?, targetId: String?): Response<JsonElement> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "f4fc7ac482599d81dfb6aa37100923c8c9edeea9ca2be854102a6339197f840a")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "chatCreateRaid")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("sourceID", channelId)
                    addProperty("targetID", targetId)
                })
            })
        }
        return graphQL.startRaid(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun cancelRaid(clientId: String?, token: String?, channelId: String?): Response<JsonElement> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "c388b89e7616a11a8a07b75e3d7bbe7278d37c3c46f43d7c8d4d0262edc00cd9")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "chatCancelRaid")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("sourceID", channelId)
                })
            })
        }
        return graphQL.cancelRaid(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun getVips(clientId: String?, channelLogin: String?): Response<VipsDataResponse> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "612a574d07afe5db2f9e878e290225224a0b955e65b5d1235dcd4b68ff668218")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "VIPs")
            add("variables", JsonObject().apply {
                addProperty("login", channelLogin)
            })
        }
        return graphQL.getVips(clientId, json)
    }

    suspend fun addVip(clientId: String?, token: String?, channelId: String?, targetLogin: String?): Response<JsonElement> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "e8c397f1ed8b1fdbaa201eedac92dd189ecfb2d828985ec159d4ae77f9920170")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "VIPUser")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("channelID", channelId)
                    addProperty("granteeLogin", targetLogin)
                })
            })
        }
        return graphQL.addVip(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }

    suspend fun removeVip(clientId: String?, token: String?, channelId: String?, targetLogin: String?): Response<JsonElement> {
        val json = JsonObject().apply {
            add("extensions", JsonObject().apply {
                add("persistedQuery", JsonObject().apply {
                    addProperty("sha256Hash", "2ce4fcdf6667d013aa1f820010e699d1d4abdda55e26539ecf4efba8aff2d661")
                    addProperty("version", 1)
                })
            })
            addProperty("operationName", "UnVIPUser")
            add("variables", JsonObject().apply {
                add("input", JsonObject().apply {
                    addProperty("channelID", channelId)
                    addProperty("revokeeLogin", targetLogin)
                })
            })
        }
        return graphQL.removeVip(clientId, token?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, json)
    }
}