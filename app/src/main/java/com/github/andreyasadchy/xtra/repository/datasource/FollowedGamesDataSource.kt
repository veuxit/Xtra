package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.github.andreyasadchy.xtra.UserFollowedGamesQuery
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowGameRepository
import com.github.andreyasadchy.xtra.util.C

class FollowedGamesDataSource(
    private val localFollowsGame: LocalFollowGameRepository,
    private val gqlHeaders: Map<String, String>,
    private val gqlApi: GraphQLRepository,
    private val apolloClient: ApolloClient,
    private val checkIntegrity: Boolean,
    private val apiPref: ArrayList<Pair<Long?, String?>?>) : PagingSource<Int, Game>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Game> {
        return try {
            val response = try {
                val list = mutableListOf<Game>()
                localFollowsGame.loadFollows().forEach {
                    list.add(Game(
                        gameId = it.gameId,
                        gameSlug = it.gameSlug,
                        gameName = it.gameName,
                        boxArtUrl = it.boxArt,
                        followLocal = true
                    ))
                }
                try {
                    when (apiPref.elementAt(0)?.second) {
                        C.GQL_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) gqlQueryLoad() else throw Exception()
                        C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) gqlLoad() else throw Exception()
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") return LoadResult.Error(e)
                    try {
                        when (apiPref.elementAt(1)?.second) {
                            C.GQL_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) gqlQueryLoad() else throw Exception()
                            C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) gqlLoad() else throw Exception()
                            else -> throw Exception()
                        }
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check") return LoadResult.Error(e)
                        listOf()
                    }
                }.forEach { game ->
                    val item = list.find { it.gameId == game.gameId }
                    if (item == null) {
                        game.followAccount = true
                        list.add(game)
                    } else {
                        item.followAccount = true
                        item.viewersCount = game.viewersCount
                        item.broadcastersCount = game.broadcastersCount
                        item.tags = game.tags
                    }
                }
                list.sortBy { it.gameName }
                list
            } catch (e: Exception) {
                if (e.message == "failed integrity check") return LoadResult.Error(e)
                listOf()
            }
            LoadResult.Page(
                data = response,
                prevKey = null,
                nextKey = null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private suspend fun gqlQueryLoad(): List<Game> {
        val response = apolloClient.newBuilder().apply {
            gqlHeaders.entries.forEach { addHttpHeader(it.key, it.value) }
        }.build().query(UserFollowedGamesQuery(
            first = Optional.Present(100)
        )).execute()
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        return response.data!!.user!!.followedGames!!.nodes!!.mapNotNull { item ->
            item?.let {
                Game(
                    gameId = it.id,
                    gameSlug = it.slug,
                    gameName = it.displayName,
                    boxArtUrl = it.boxArtURL,
                    viewersCount = it.viewersCount,
                    broadcastersCount = it.broadcastersCount,
                    tags = it.tags?.map { tag ->
                        Tag(
                            id = tag.id,
                            name = tag.localizedName
                        )
                    }
                )
            }
        }
    }

    private suspend fun gqlLoad(): List<Game> {
        val response = gqlApi.loadFollowedGames(gqlHeaders, 100)
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        return response.data!!.currentUser.followedGames.nodes.map { item ->
            item.let {
                Game(
                    gameId = it.id,
                    gameName = it.displayName,
                    boxArtUrl = it.boxArtURL,
                    viewersCount = it.viewersCount ?: 0,
                    tags = it.tags?.map { tag ->
                        Tag(
                            id = tag.id,
                            name = tag.localizedName
                        )
                    }
                )
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Game>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
