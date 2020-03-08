package me.hackerchick.sharetoinputstick

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = arrayOf(InputStick::class), version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inputStickDao(): InputStickDao
}