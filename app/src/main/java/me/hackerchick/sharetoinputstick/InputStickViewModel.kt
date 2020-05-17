package me.hackerchick.sharetoinputstick

import android.app.AlertDialog
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.DialogInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.Room
import com.inputstick.api.basic.InputStickHID
import com.inputstick.api.basic.InputStickKeyboard

class InputStickViewModel(application: Application) : AndroidViewModel(application) {
    private val inputStickDao = Room.databaseBuilder(
        this.getApplication(),
        AppDatabase::class.java, "inputsticks"
    ).allowMainThreadQueries().build().inputStickDao()

    private val _knownDevicesList = MutableLiveData<ArrayList<InputStick>>()

    fun getKnownDevicesList(): LiveData<ArrayList<InputStick>> {
        if (_knownDevicesList.value == null) {
            _knownDevicesList.value = loadKnownDevicesList()
        }

        return _knownDevicesList
    }

    private fun addToKnownDevicesList(inputStick: InputStick) {
        var data = getKnownDevicesList().value
        data!!.add(inputStick)
        _knownDevicesList.value = data
    }

    fun editKnownDevice(inputStick: InputStick) {
        inputStickDao.update(inputStick)

        var data = getKnownDevicesList().value!!

        for (device in data) {
            if (device.mac == inputStick.mac) {
                data.remove(device)
                data.add(inputStick)
                _knownDevicesList.value = data
                break
            }
        }
    }

    private val _bluetoothDevicesList = MutableLiveData<ArrayList<InputStick>>()

    fun getBluetoothDevicesList(): LiveData<ArrayList<InputStick>> {
        if (_bluetoothDevicesList.value == null) {
            _bluetoothDevicesList.value = ArrayList<InputStick>()
        }

        return _bluetoothDevicesList
    }

    fun bluetoothDevicesListContains(inputStick: InputStick) : Boolean {
        return getBluetoothDevicesList().value!!.contains(inputStick)
    }
    fun addToBluetoothDevicesList(inputStick: InputStick) {
        var data = getBluetoothDevicesList().value!!
        if (data.contains(inputStick)) return
        data.add(inputStick)
        _bluetoothDevicesList.value = data
    }

    private val _waitingDevice: MutableLiveData<InputStick> = MutableLiveData()
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

    private val _inputSpeed = MutableLiveData<Int>()

    fun getInputSpeed(): LiveData<Int> {
        if (_inputSpeed.value == null) {
            _inputSpeed.value = InputStickKeyboard.TYPING_SPEED_NORMAL
        }

        return _inputSpeed
    }

    fun setInputSpeed(inputSpeed: Int) {
        _inputSpeed.value = inputSpeed
    }

    private fun loadKnownDevicesList(): ArrayList<InputStick> {
        return inputStickDao.getAllByLastUsed() as ArrayList<InputStick>
    }

    private fun retrieveInputStick(mac: String): InputStick {
        var inputStick = inputStickDao.findByMac(mac)
        if (inputStick == null) {
            inputStick = InputStick(mac, null, null, 0)
        }

        return inputStick
    }

    fun retrieveInputStick(bluetoothDevice: BluetoothDevice): InputStick {
        var inputStick = retrieveInputStick(bluetoothDevice.address)
        if (inputStick.name != bluetoothDevice.name) {
            inputStick.name = bluetoothDevice.name

            inputStickDao.update(inputStick)
        }

        return inputStick
    }
}
