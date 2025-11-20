package me.hackerchick.sharetoinputstick

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.Room
import com.inputstick.api.basic.InputStickKeyboard

class InputStickViewModel(application: Application) : AndroidViewModel(application) {
    private val _knownDevicesList = MutableLiveData<ArrayList<InputStick>>()

    fun getKnownDevicesList(context: Context): LiveData<ArrayList<InputStick>> {
        if (_knownDevicesList.value == null) {
            _knownDevicesList.value = loadKnownDevicesList(context)
        }

        return _knownDevicesList
    }

    fun markInputStickAsKnown(context: Context, inputStick: InputStick) {
        // Remove device from list
        markInputStickAsUnknown(context, inputStick)
        val data = getKnownDevicesList(context).value!!
        data.add(inputStick)
        _knownDevicesList.value = data
    }

    fun markInputStickAsUnknown(context: Context, inputStick: InputStick) {
        // Reorder list if it makes sense
        val data = getKnownDevicesList(context).value!!

        for (device in data) {
            // See if it is an already known device, if so, remove
            if (device.mac == inputStick.mac) {
                data.remove(device)
                break
            }
        }

        _knownDevicesList.value = data
    }

    private val _bluetoothDevicesList = MutableLiveData<ArrayList<InputStick>>()

    fun getBluetoothDevicesList(): LiveData<ArrayList<InputStick>> {
        if (_bluetoothDevicesList.value == null) {
            _bluetoothDevicesList.value = ArrayList<InputStick>()
        }

        return _bluetoothDevicesList
    }

    fun bluetoothDevicesListContains(inputStick: InputStick) : Boolean {
        for (bluetoothDevice in getBluetoothDevicesList().value!!) {
            if (bluetoothDevice.mac == inputStick.mac) {
                return true
            }
        }

        return false
    }
    fun addToBluetoothDevicesList(inputStick: InputStick) {
        var data = getBluetoothDevicesList().value!!
        if (data.contains(inputStick)) return
        data.add(inputStick)
        _bluetoothDevicesList.value = data
    }

    private val _waitingDevice: MutableLiveData<InputStick?> = MutableLiveData()
    fun getWaitingDevice(): LiveData<InputStick?> {
        return _waitingDevice
    }
    fun setWaitingDevice(inputStick: InputStick?) {
        _waitingDevice.value = inputStick
    }

    private val _connectingDevice: MutableLiveData<InputStick?> = MutableLiveData()
    fun getConnectingDevice(): LiveData<InputStick?> {
        return _connectingDevice
    }
    fun setConnectingDevice(inputStick: InputStick?) {
        _connectingDevice.value = inputStick
    }

    private var _busyDialogMessage: String? = null

    fun getBusyDialogMessage(): String? {
        return _busyDialogMessage
    }

    fun setBusyDialogMessage(message: String?) {
        _busyDialogMessage = message
    }

    private val _textToSend = MutableLiveData<String>()

    fun getTextToSend(): LiveData<String> {
        if (_textToSend.value == null) {
            _textToSend.value = ""
        }

        return _textToSend
    }

    fun setTextToSend(text: String) {
        _textToSend.value = text
    }

    private val _sending = MutableLiveData<Boolean>()

    fun isSending(): LiveData<Boolean> {
        if (_sending.value == null) {
            _sending.value = false
        }

        return _sending
    }

    fun setSending(value: Boolean) {
        _sending.value = value
    }

    private fun loadKnownDevicesList(context: Context): ArrayList<InputStick> {
        val db = DBHelper(context)
        return db.getAllByLastUsed(context) as ArrayList<InputStick>
    }

    private fun retrieveInputStick(context: Context, mac: String): InputStick {
        val db = DBHelper(context)

        var inputStick = db.getInputStick(context, mac)

        return inputStick
    }

    fun retrieveInputStick(context: Context, bluetoothDevice: BluetoothDevice): InputStick {
        var inputStick = retrieveInputStick(context, bluetoothDevice.address)
        if (inputStick.name != bluetoothDevice.name) {
            inputStick.name = bluetoothDevice.name

            val db = DBHelper(context)
            db.setInputStickName(inputStick.mac, inputStick.name)
        }

        return inputStick
    }
}
