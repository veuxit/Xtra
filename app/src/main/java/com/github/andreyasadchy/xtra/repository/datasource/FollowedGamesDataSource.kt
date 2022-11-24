package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.helix.game.Game
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowGameRepository
import com.github.andreyasadchy.xtra.util.C
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope

class FollowedGamesDataSource(
    private val localFollowsGame: LocalFollowGameRepository,
    private val gqlClientId: String?,
    private val gqlToken: String?,
    private val gqlApi: GraphQLRepository,
    private val apiPref: ArrayList<Pair<Long?, String?>?>,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Game>(coroutineScope) {

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Game>) {
        loadInitial(params, callback) {
            val list = mutableListOf<Game>()
            for (i in localFollowsGame.loadFollows()) {
                list.add(Game(id = i.gameId, name = i.gameName, box_art_url = i.boxArt, followLocal = true))
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
                    val item = list.find { it.id == i.id }
                    if (item == null) {
                        i.followTwitch = true
                        list.add(i)
                    } else {
                        item.followTwitch = true
                        item.viewersCount = i.viewersCount
                        item.broadcastersCount = i.broadcastersCount
                        item.tags = i.tags
                    }
                }
            }
            list.sortBy { it.name }
            list
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
        private val apiPref: ArrayList<Pair<Long?, String?>?>,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Game, FollowedGamesDataSource>() {

        override fun create(): DataSource<Int, Game> =
                FollowedGamesDataSource(localFollowsGame, gqlClientId, gqlToken, gqlApi, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
