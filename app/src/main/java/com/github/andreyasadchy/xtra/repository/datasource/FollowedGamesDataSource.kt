package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowGameRepository
import com.github.andreyasadchy.xtra.util.C
import com.google.gson.JsonObject

class FollowedGamesDataSource(
    private val localFollowsGame: LocalFollowGameRepository,
    private val gqlClientId: String?,
    private val gqlToken: String?,
    private val gqlApi: GraphQLRepository,
    private val apiPref: ArrayList<Pair<Long?, String?>?>) : PagingSource<Int, Game>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Game> {
        return try {
            val response = try {
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
            } catch (e: Exception) {
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
        val context = XtraApp.INSTANCE.applicationContext
        val get = gqlApi.loadQueryUserFollowedGames(
            clientId = gqlClientId,
            token = gqlToken,
            query = context.resources.openRawResource(R.raw.userfollowedgames).bufferedReader().use { it.readText() },
            variables = JsonObject().apply {
                addProperty("first", 100)
            })
        return get.data
    }

    private suspend fun gqlLoad(): List<Game> {
        val get = gqlApi.loadFollowedGames(gqlClientId, gqlToken, 100)
        return get.data
    }

    override fun getRefreshKey(state: PagingState<Int, Game>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
