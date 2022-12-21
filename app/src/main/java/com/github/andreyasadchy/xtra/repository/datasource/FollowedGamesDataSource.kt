package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.github.andreyasadchy.xtra.UserFollowedGamesQuery
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowGameRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CoroutineScope

class FollowedGamesDataSource(
    private val localFollowsGame: LocalFollowGameRepository,
    private val gqlClientId: String?,
    private val gqlToken: String?,
    private val gqlApi: GraphQLRepository,
    private val apolloClient: ApolloClient,
    private val apiPref: ArrayList<Pair<Long?, String?>?>,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Game>(coroutineScope) {

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Game>) {
        loadInitial(params, callback) {
            val list = mutableListOf<Game>()
            for (i in localFollowsGame.loadFollows()) {
                list.add(Game(gameId = i.gameId, gameName = i.gameName, boxArtUrl = i.boxArt, followLocal = true))
            }
            val remote = try {
                when (apiPref.elementAt(0)?.second) {
                    C.GQL_QUERY -> if (!gqlToken.isNullOrBlank()) gqlQueryLoad() else throw Exception()
                    C.GQL -> if (!gqlToken.isNullOrBlank()) gqlLoad() else throw Exception()
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.GQL_QUERY -> if (!gqlToken.isNullOrBlank()) gqlQueryLoad() else throw Exception()
                        C.GQL -> if (!gqlToken.isNullOrBlank()) gqlLoad() else throw Exception()
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    listOf()
                }
            }
            if (remote.isNotEmpty()) {
                for (i in remote) {
                    val item = list.find { it.gameId == i.gameId }
                    if (item == null) {
                        i.followAccount = true
                        list.add(i)
                    } else {
                        item.followAccount = true
                        item.viewersCount = i.viewersCount
                        item.broadcastersCount = i.broadcastersCount
                        item.tags = i.tags
                    }
                }
            }
            list.sortBy { it.gameName }
            list
        }
    }

    private suspend fun gqlQueryLoad(): List<Game> {
        val get1 = apolloClient.newBuilder().apply {
            gqlClientId?.let { addHttpHeader("Client-ID", it) }
            gqlToken?.let { addHttpHeader("Authorization", it) }
        }.build().query(UserFollowedGamesQuery(
            first = Optional.Present(100)
        )).execute().data?.user?.followedGames
        val get = get1?.nodes
        val list = mutableListOf<Game>()
        if (get != null) {
            for (i in get) {
                val tags = mutableListOf<Tag>()
                i?.tags?.forEach { tag ->
                    tags.add(Tag(
                        id = tag.id,
                        name = tag.localizedName
                    ))
                }
                list.add(Game(
                    gameId = i?.id,
                    gameName = i?.displayName,
                    boxArtUrl = i?.boxArtURL,
                    viewersCount = i?.viewersCount,
                    broadcastersCount = i?.broadcastersCount,
                    tags = tags
                ))
            }
        }
        return list
    }

    private suspend fun gqlLoad(): List<Game> {
        val get = gqlApi.loadFollowedGames(gqlClientId, gqlToken, 100)
        return get.data
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Game>) {
        loadRange(params, callback) {
            listOf()
        }
    }

    class Factory(
        private val localFollowsGame: LocalFollowGameRepository,
        private val gqlClientId: String?,
        private val gqlToken: String?,
        private val gqlApi: GraphQLRepository,
        private val apolloClient: ApolloClient,
        private val apiPref: ArrayList<Pair<Long?, String?>?>,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Game, FollowedGamesDataSource>() {

        override fun create(): DataSource<Int, Game> =
                FollowedGamesDataSource(localFollowsGame, gqlClientId, gqlToken, gqlApi, apolloClient, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
