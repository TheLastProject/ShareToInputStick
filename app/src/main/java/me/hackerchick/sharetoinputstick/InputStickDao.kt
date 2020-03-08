package me.hackerchick.sharetoinputstick

import androidx.room.*

@Dao
interface InputStickDao {
    @Query("SELECT * FROM inputstick WHERE last_used > 0 ORDER BY last_used DESC")
    fun getAllByLastUsed(): List<InputStick>

    @Query("SELECT * FROM inputstick WHERE mac = :mac LIMIT 1")
    fun findByMac(mac: String): InputStick?

    @Insert
    fun insert(inputStick: InputStick)

    @Update
    fun update(inputStick: InputStick)

    @Delete
    fun delete(inputStick: InputStick)
}