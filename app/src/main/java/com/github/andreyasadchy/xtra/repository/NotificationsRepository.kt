package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.db.NotificationsDao
import com.github.andreyasadchy.xtra.model.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationsRepository @Inject constructor(
    private val notificationsDao: NotificationsDao) {

    suspend fun loadUsers() = withContext(Dispatchers.IO) {
        notificationsDao.getAll()
    }

    suspend fun getByUserId(id: String) = withContext(Dispatchers.IO) {
        notificationsDao.getByUserId(id)
    }

    suspend fun saveUser(item: Notification) = withContext(Dispatchers.IO) {
        notificationsDao.insert(item)
    }

    suspend fun deleteUser(item: Notification) = withContext(Dispatchers.IO) {
        notificationsDao.delete(item)
    }
}
