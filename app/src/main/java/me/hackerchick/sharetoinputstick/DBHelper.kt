package me.hackerchick.sharetoinputstick

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.lang.RuntimeException

class DBHelper(context: Context?):
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    internal object InputSticksDB {
        val TABLE = "inputsticks"
        val MAC = "mac"
        val NAME = "name"
        val PASSWORD = "password"
        var LAST_USED = "last_used"
        var INPUT_SPEED = "input_speed"
        var KEYBOARD_LAYOUT_CODE = "keyboard_layout_code"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // create table for inputsticks
        db.execSQL(
            "CREATE TABLE " + InputSticksDB.TABLE + "(" +
                InputSticksDB.MAC + " TEXT PRIMARY KEY NOT NULL," +
                InputSticksDB.NAME + " TEXT," +
                InputSticksDB.PASSWORD + " TEXT," +
                InputSticksDB.LAST_USED + " INTEGER DEFAULT '0'," +
                InputSticksDB.INPUT_SPEED + " INTEGER DEFAULT '100'," +
                InputSticksDB.KEYBOARD_LAYOUT_CODE + " TEXT" +
            ")"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE " + InputSticksDB.TABLE +
                    " ADD COLUMN " + InputSticksDB.INPUT_SPEED + " INTEGER DEFAULT '100'"
            )
            db.execSQL(
                "ALTER TABLE " + InputSticksDB.TABLE +
                    " ADD COLUMN " + InputSticksDB.KEYBOARD_LAYOUT_CODE + " TEXT"
            )
        }
    }

    fun createInputStickIfNotExisting(
        mac: String
    ) {
        val db = writableDatabase
        val contentValues = ContentValues()
        contentValues.put(InputSticksDB.MAC, mac)
        db.insertWithOnConflict(InputSticksDB.TABLE, null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);
    }

    fun setInputStickName(
        mac: String, name: String?
    ) {
        // Ensure exists
        createInputStickIfNotExisting(mac)

        // Set name
        val db = writableDatabase
        val contentValues = ContentValues()
        contentValues.put(InputSticksDB.NAME, name)
        db.beginTransaction()
        val rows = db.update(InputSticksDB.TABLE, contentValues, InputSticksDB.MAC + " = ?", arrayOf(mac))
        if (rows != 1) {
            throw RuntimeException("Did not update exactly one record")
        } else {
            db.setTransactionSuccessful();
        }
        db.endTransaction()
    }

    fun setInputStickPassword(
        mac: String, password: String?
    ) {
        // Ensure exists
        createInputStickIfNotExisting(mac)

        // Set password
        val db = writableDatabase
        val contentValues = ContentValues()
        contentValues.put(InputSticksDB.PASSWORD, password)
        db.beginTransaction()
        val rows = db.update(InputSticksDB.TABLE, contentValues, InputSticksDB.MAC + " = ?", arrayOf(mac))
        if (rows != 1) {
            throw RuntimeException("Did not update exactly one record")
        } else {
            db.setTransactionSuccessful();
        }
        db.endTransaction()
    }

    fun setInputStickLastUsed(
        mac: String, lastUsed: Long
    ) {
        // Ensure exists
        createInputStickIfNotExisting(mac)

        // Set last used
        val db = writableDatabase
        val contentValues = ContentValues()
        contentValues.put(InputSticksDB.LAST_USED, lastUsed)
        db.beginTransaction()
        val rows = db.update(InputSticksDB.TABLE, contentValues, InputSticksDB.MAC + " = ?", arrayOf(mac))
        if (rows != 1) {
            throw RuntimeException("Did not update exactly one record")
        } else {
            db.setTransactionSuccessful();
        }
        db.endTransaction()
    }

    fun setInputStickInputSpeed(
        mac: String, inputSpeed: Int
    ) {
        // Ensure exists
        createInputStickIfNotExisting(mac)

        // Set input speed
        val db = writableDatabase
        val contentValues = ContentValues()
        contentValues.put(InputSticksDB.INPUT_SPEED, inputSpeed)
        db.beginTransaction()
        val rows = db.update(InputSticksDB.TABLE, contentValues, InputSticksDB.MAC + " = ?", arrayOf(mac))
        if (rows != 1) {
            throw RuntimeException("Did not update exactly one record")
        } else {
            db.setTransactionSuccessful();
        }
        db.endTransaction()
    }

    fun setInputStickKeyboardLayoutCode(
        mac: String, keyboardLayoutCode: String?
    ) {
        // Ensure exists
        createInputStickIfNotExisting(mac)

        // Set keyboard layout code
        val db = writableDatabase
        val contentValues = ContentValues()
        contentValues.put(InputSticksDB.KEYBOARD_LAYOUT_CODE, keyboardLayoutCode)
        db.beginTransaction()
        val rows = db.update(InputSticksDB.TABLE, contentValues, InputSticksDB.MAC + " = ?", arrayOf(mac))
        if (rows != 1) {
            throw RuntimeException("Did not update exactly one record")
        } else {
            db.setTransactionSuccessful();
        }
        db.endTransaction()
    }

    fun getInputStick(context: Context, mac: String): InputStick {
        createInputStickIfNotExisting(mac)

        val db = readableDatabase
        val data = db.rawQuery(
            ("SELECT * FROM " + InputSticksDB.TABLE +
                    " WHERE " + InputSticksDB.MAC + "=?"), arrayOf(String.format("%s", mac))
        )
        var card: InputStick? = null
        if (data.count == 1) {
            data.moveToFirst()
            card = InputStick.toInputStick(context, data)
        }
        data.close()

        // Should never happen
        if (card == null) {
            throw RuntimeException("Did not at least get a dummy inputstick when calling getInputStick, this should never happen")
        }

        return card
    }

    fun getAllByLastUsedCursor(db: SQLiteDatabase): Cursor {
        return db.rawQuery(
            "SELECT * FROM " + InputSticksDB.TABLE +
                    " WHERE " + InputSticksDB.LAST_USED + " >0" +
                    " ORDER BY " + InputSticksDB.LAST_USED + " DESC", null);
    }

    fun getAllByLastUsed(context: Context): List<InputStick> {
        val db = readableDatabase
        val data = getAllByLastUsedCursor(db)
        val inputSticks = ArrayList<InputStick>()

        if (!data.moveToFirst()) {
            data.close()
            return inputSticks;
        }

        inputSticks.add(InputStick.toInputStick(context, data));

        while (data.moveToNext()) {
            inputSticks.add(InputStick.toInputStick(context, data));
        }

        data.close()
        return inputSticks
    }

    companion object {
        val DATABASE_NAME = "InputSticks.db"
        val DATABASE_VERSION = 2
    }
}