package com.xtra.kick.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xtra.kick.model.ui.ChannelSort

@Dao
interface ChannelSortDao {

    @Query("SELECT * FROM sort_channel WHERE id = :id")
    fun getById(id: String): ChannelSort?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: ChannelSort)

    @Delete
    fun delete(item: ChannelSort)
}
