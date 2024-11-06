package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.github.andreyasadchy.xtra.model.Notification

@Dao
interface NotificationsDao {

    @Query("SELECT * FROM notifications")
    fun getAll(): List<Notification>

    @Query("SELECT * FROM notifications WHERE channelId = :id")
    fun getByUserId(id: String): Notification?

    @Insert()
    fun insert(item: Notification)

    @Delete
    fun delete(item: Notification)
}