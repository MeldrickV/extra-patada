package com.xtra.kick.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.xtra.kick.model.NotificationUser

@Dao
interface NotificationUsersDao {

    @Query("SELECT * FROM notifications")
    fun getAll(): List<NotificationUser>

    @Query("SELECT * FROM notifications WHERE channelId = :id")
    fun getById(id: String): NotificationUser?

    @Insert
    fun insert(item: NotificationUser)

    @Delete
    fun delete(item: NotificationUser)
}