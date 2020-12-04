package me.hackerchick.sharetoinputstick

import android.database.Cursor

class InputStick(
    val mac: String, var name: String?, var password: String?, var last_used: Long) {
    companion object {
        fun toInputStick(cursor: Cursor): InputStick {
            val mac = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.InputSticksDB.MAC))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.InputSticksDB.NAME))
            val password = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.InputSticksDB.PASSWORD))
            val lastUsed = cursor.getLong(cursor.getColumnIndexOrThrow(DBHelper.InputSticksDB.LAST_USED))
            return InputStick(
                mac,
                name,
                password,
                lastUsed
            )
        }
    }
}