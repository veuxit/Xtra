package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.db.ShownNotificationsDao
import com.github.andreyasadchy.xtra.model.ShownNotification
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShownNotificationsRepository @Inject constructor(
    private val shownNotificationsDao: ShownNotificationsDao) {

    suspend fun getNewStreams(notificationsRepository: NotificationsRepository, gqlHeaders: Map<String, String>, gqlApi: GraphQLRepository, helixHeaders: Map<String, String>, helixApi: HelixApi): List<Stream> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Stream>()
        notificationsRepository.loadUsers().map { it.channelId }.takeIf { it.isNotEmpty() }?.let {
            try {
                gqlQueryLocal(gqlHeaders, it, gqlApi)
            } catch (e: Exception) {
                if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    try {
                        helixLocal(helixHeaders, it, helixApi)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
        }?.let { list.addAll(it) }
        val liveList = list.mapNotNull { stream ->
            stream.channelId.takeUnless { it.isNullOrBlank() }?.let { channelId ->
                stream.startedAt.takeUnless { it.isNullOrBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let { startedAt ->
                    ShownNotification(channelId, startedAt)
                }
            }
        }
        val oldList = shownNotificationsDao.getAll()
        oldList.filter { item -> liveList.find { it.channelId == item.channelId } == null }.let {
            shownNotificationsDao.deleteList(it)
        }
        shownNotificationsDao.insertList(liveList)
        val newStreams = liveList.mapNotNull { item ->
            item.takeIf { oldList.find { it.channelId == item.channelId }.let { it == null || it.startedAt < item.startedAt } }?.channelId
        }
        list.filter { it.channelId in newStreams }
    }

    private suspend fun gqlQueryLocal(gqlHeaders: Map<String, String>, ids: List<String>, gqlApi: GraphQLRepository): List<Stream> {
        val items = ids.chunked(100).map { list ->
            gqlApi.loadQueryUsersStream(
                headers = gqlHeaders,
                id = list
            ).also { response ->
                response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
        }.flatMap { it.data!!.users!! }
        val list = items.mapNotNull { item ->
            item?.let {
                if (it.stream?.viewersCount != null) {
                    Stream(
                        id = it.stream.id,
                        channelId = it.id,
                        channelLogin = it.login,
                        channelName = it.displayName,
                        gameId = it.stream.game?.id,
                        gameSlug = it.stream.game?.slug,
                        gameName = it.stream.game?.displayName,
                        type = it.stream.type,
                        title = it.stream.broadcaster?.broadcastSettings?.title,
                        viewerCount = it.stream.viewersCount,
                        startedAt = it.stream.createdAt?.toString(),
                        thumbnailUrl = it.stream.previewImageURL,
                        profileImageUrl = it.profileImageURL,
                        tags = it.stream.freeformTags?.mapNotNull { tag -> tag.name }
                    )
                } else null
            }
        }
        return list
    }

    private suspend fun helixLocal(helixHeaders: Map<String, String>, ids: List<String>, helixApi: HelixApi): List<Stream> {
        val items = ids.chunked(100).map {
            helixApi.getStreams(
                headers = helixHeaders,
                ids = it
            )
        }.flatMap { it.data }
        val users = items.mapNotNull { it.channelId }.chunked(100).map {
            helixApi.getUsers(
                headers = helixHeaders,
                ids = it
            )
        }.flatMap { it.data }
        val list = items.mapNotNull {
            if (it.viewerCount != null) {
                Stream(
                    id = it.id,
                    channelId = it.channelId,
                    channelLogin = it.channelLogin,
                    channelName = it.channelName,
                    gameId = it.gameId,
                    gameName = it.gameName,
                    type = it.type,
                    title = it.title,
                    viewerCount = it.viewerCount,
                    startedAt = it.startedAt,
                    thumbnailUrl = it.thumbnailUrl,
                    profileImageUrl = it.channelId?.let { id ->
                        users.find { user -> user.channelId == id }?.profileImageUrl
                    },
                    tags = it.tags
                )
            } else null
        }
        return list
    }
}
