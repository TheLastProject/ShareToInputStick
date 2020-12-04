package me.hackerchick.sharetoinputstick

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class InputStick(
    @PrimaryKey val mac: String) {
        @ColumnInfo(name = "name")
        var name: String? = null

        @ColumnInfo(name = "password")
        var password: String? = null

        @ColumnInfo(name = "last_used")
        var last_used: Long = 0  // Unix Time
}
