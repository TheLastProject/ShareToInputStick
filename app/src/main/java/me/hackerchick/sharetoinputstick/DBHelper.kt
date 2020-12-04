package me.hackerchick.sharetoinputstick

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DBHelper(context: Context?) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    internal object InputSticksDB {
        val TABLE = "inputsticks"
        val MAC = "mac"
        val NAME = "name"
        val PASSWORD = "password"
        var LAST_USED = "last_used"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // create table for inputsticks
        db.execSQL(
            "CREATE TABLE " + InputSticksDB.TABLE + "(" +
                    InputSticksDB.MAC + " TEXT PRIMARY KEY NOT NULL," +
                    InputSticksDB.NAME + " TEXT," +
                    InputSticksDB.PASSWORD + " TEXT," +
                    InputSticksDB.LAST_USED + " INTEGER DEFAULT '0' )"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun upsertInputStick(
        mac: String, name: String?, password: String?, lastUsed: Long
    ): Long {
        val db = writableDatabase
        val contentValues = ContentValues()
        contentValues.put(InputSticksDB.MAC, mac)
        contentValues.put(InputSticksDB.NAME, name)
        contentValues.put(InputSticksDB.PASSWORD, password)
        contentValues.put(InputSticksDB.LAST_USED, lastUsed)
        return db.insertWithOnConflict(InputSticksDB.TABLE, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
    }

    fun getInputStick(mac: String): InputStick? {
        val db = readableDatabase
        val data = db.rawQuery(
            ("SELECT * FROM " + InputSticksDB.TABLE +
                    " WHERE " + InputSticksDB.MAC + "=?"), arrayOf(String.format("%s", mac))
        )
        var card: InputStick? = null
        if (data.count == 1) {
            data.moveToFirst()
            card = InputStick.toInputStick(data)
        }
        data.close()
        return card
    }

    fun getAllByLastUsedCursor(): Cursor {
        val db = readableDatabase
        return db.rawQuery(
            "SELECT * FROM " + InputSticksDB.TABLE +
                    " WHERE " + InputSticksDB.LAST_USED + " >0" +
                    " ORDER BY " + InputSticksDB.LAST_USED + " DESC", null);
    }

    fun getAllByLastUsed(): List<InputStick> {
        val data = getAllByLastUsedCursor()
        val inputSticks = ArrayList<InputStick>()

        if (!data.moveToFirst()) {
            return inputSticks;
        }

        inputSticks.add(InputStick.toInputStick(data));

        while (data.moveToNext()) {
            inputSticks.add(InputStick.toInputStick(data));
        }

        data.close()
        return inputSticks
    }

    companion object {
        val DATABASE_NAME = "InputSticks.db"
        val ORIGINAL_DATABASE_VERSION = 1
        val DATABASE_VERSION = 1
    }
}