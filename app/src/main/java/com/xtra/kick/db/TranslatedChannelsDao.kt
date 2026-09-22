package com.xtra.kick.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.xtra.kick.model.ui.TranslatedChannel

@Dao
interface TranslatedChannelsDao {

    @Query("SELECT * FROM translate_all_messages WHERE channelId = :id")
    fun getById(id: String): TranslatedChannel?

    @Insert
    fun insert(item: TranslatedChannel)

    @Delete
    fun delete(item: TranslatedChannel)
}