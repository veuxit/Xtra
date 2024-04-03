package com.github.andreyasadchy.xtra.repository

import android.content.Context
import com.github.andreyasadchy.xtra.db.LocalFollowsGameDao
import com.github.andreyasadchy.xtra.model.offline.LocalFollowGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFollowGameRepository @Inject constructor(
        private val localFollowsGameDao: LocalFollowsGameDao) {

    suspend fun loadFollows() = withContext(Dispatchers.IO) {
        localFollowsGameDao.getAll()
    }

    suspend fun getFollowByGameId(id: String) = withContext(Dispatchers.IO) {
        localFollowsGameDao.getByGameId(id)
    }

    suspend fun saveFollow(item: LocalFollowGame) = withContext(Dispatchers.IO) {
        localFollowsGameDao.insert(item)
    }

    suspend fun deleteFollow(context: Context, item: LocalFollowGame) = withContext(Dispatchers.IO) {
        if (!item.gameId.isNullOrBlank()) {
            File(context.filesDir.path + File.separator + "box_art" + File.separator + "${item.gameId}.png").delete()
        }
        localFollowsGameDao.delete(item)
    }

    suspend fun updateFollow(item: LocalFollowGame) = withContext(Dispatchers.IO) {
        localFollowsGameDao.update(item)
    }
}
