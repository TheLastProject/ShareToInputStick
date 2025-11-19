package me.hackerchick.sharetoinputstick

import android.content.Context
import android.database.Cursor

class InputStick(private val context: Context, mac: String, name: String?, password: String?, lastUsed: Long, inputSpeed: Int, keyboardLayoutCode: String?) {
    private val _mac = mac
    val mac: String
        get() {
            return _mac
        }

    private var _name = name
    var name: String?
        get() {
            return _name
        }
        set(value) {
            DBHelper(context).setInputStickName(mac, value)
            _name = value
        }

    private var _password = password
    var password: String?
        get() {
            return _password
        }
        set(value) {
            DBHelper(context).setInputStickPassword(mac, value)
            _password = value
        }

    private var _lastUsed = lastUsed
    var lastUsed: Long
        get() {
            return _lastUsed
        }
        set(value) {
            DBHelper(context).setInputStickLastUsed(mac, value)
            _lastUsed = value
        }

    private var _inputSpeed = inputSpeed
    var inputSpeed: Int
        get() {
            return _inputSpeed
        }
        set(value) {
            DBHelper(context).setInputStickInputSpeed(mac, value)
            _inputSpeed = value
        }

    private var _keyboardLayoutCode = keyboardLayoutCode
    var keyboardLayoutCode: String?
        get() {
            return _keyboardLayoutCode
        }
        set(value) {
            DBHelper(context).setInputStickKeyboardLayoutCode(mac, value)
            _keyboardLayoutCode = value
        }

    companion object {
        fun toInputStick(context: Context, cursor: Cursor): InputStick {
            val mac = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.InputSticksDB.MAC))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.InputSticksDB.NAME))
            val password = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.InputSticksDB.PASSWORD))
            val lastUsed = cursor.getLong(cursor.getColumnIndexOrThrow(DBHelper.InputSticksDB.LAST_USED))
            val inputSpeed = cursor.getInt(cursor.getColumnIndexOrThrow(DBHelper.InputSticksDB.INPUT_SPEED))
            val keyboardLayoutCode = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.InputSticksDB.KEYBOARD_LAYOUT_CODE))
            return InputStick(
                context,
                mac,
                name,
                password,
                lastUsed,
                inputSpeed,
                keyboardLayoutCode
            )
        }
    }
}