package me.hackerchick.sharetoinputstick

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.content.Intent
import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.room.Room
import com.inputstick.api.*
import com.inputstick.api.basic.InputStickHID
import com.inputstick.api.basic.InputStickKeyboard
import com.inputstick.api.broadcast.InputStickBroadcast
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity(), InputStickStateListener {
    private var inputStickDao: InputStickDao? = null

    private var mKnownDevicesListView: ListView? = null
    private var mBluetoothDevicesListView: ListView? = null
    private val mKnownDevicesList = ArrayList<InputStick>()
    private val mBluetoothDeviceList = ArrayList<InputStick>()

    private var connectingDevice: InputStick? = null

    private var PERMISSION_REQUEST_BLUETOOTH = 1
    private var REQUEST_ENABLE_BLUETOOTH = 2

    private var textToSend: String = ""

    private var useInputUtilityButton: View? = null
    private var fab: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputStickDao = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "inputsticks"
        ).allowMainThreadQueries().build().inputStickDao()

        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
                textToSend = intent.getStringExtra(Intent.EXTRA_TEXT)!!
            }
        }

        useInputUtilityButton = findViewById(R.id.useInputStickUtilityButton)
        useInputUtilityButton?.setOnClickListener {
            sendMessageUsingInputStickUtility()
        }

        fab = findViewById(R.id.fab)
        fab?.setOnClickListener {
            showNewMessageDialog()
        }

        InputStickHID.addStateListener(this)

        // Register lists
        mKnownDevicesListView = findViewById(R.id.knownDevicesListView)
        mKnownDevicesListView?.setOnItemClickListener { _, _, position, _ ->
            connectToInputStickUsingBluetooth(mKnownDevicesList[position])
        }

        mBluetoothDevicesListView = findViewById(R.id.bluetoothDevicesListView)
        mBluetoothDevicesListView?.setOnItemClickListener { _, _, position, _ ->
            connectToInputStickUsingBluetooth(mBluetoothDeviceList[position])
        }

        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)

        start()
    }

    override fun onPause() {
        InputStickHID.removeStateListener(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        InputStickHID.addStateListener(this)
    }

    private fun start() {
        if (textToSend.isEmpty()) {
            title = "Edit InputSticks"
            fab?.visibility = View.VISIBLE
            useInputUtilityButton?.visibility = View.GONE
        } else {
            title = "Share To InputStick"
            fab?.visibility = View.INVISIBLE
            useInputUtilityButton?.visibility = View.VISIBLE
        }

        updateKnownDeviceList(this)
        updateBluetoothDeviceList(this)
    }

    private fun getInputStickFromDB(macAddress: String) : InputStick {
        var inputStick = inputStickDao!!.findByMac(macAddress)
        if (inputStick == null) {
            inputStick = InputStick(macAddress, null, null, 0)
            inputStickDao!!.insert(inputStick)
        }
        return inputStick
    }

    private fun getInputStickFromDB(bluetoothDevice: BluetoothDevice) : InputStick {
        var inputStick = getInputStickFromDB(bluetoothDevice.address)
        if (inputStick.name != bluetoothDevice.name) {
            inputStick.name = bluetoothDevice.name
            inputStickDao!!.update(inputStick)
        }

        return inputStick
    }

    private fun getDevicePassword(inputStick: InputStick) : String? {
        return inputStick.password
    }

    private fun setDevicePassword(inputStick: InputStick, devicePassword: String?) {
        inputStick.password = devicePassword
        inputStick.last_used = System.currentTimeMillis() / 1000
        inputStickDao!!.update(inputStick)

        updateKnownDeviceList(this)

        if (mBluetoothDeviceList.remove(inputStick)) {
            updateBluetoothDeviceList(this)
        }
    }

    private fun connectToInputStickUsingBluetooth(device: InputStick) {
        connectingDevice = device

        var connectionPassword: ByteArray? = null
        if (getDevicePassword(device) != null) {
            connectionPassword = Util.getPasswordBytes(getDevicePassword(device))
        }
        InputStickHID.connect(application, device.mac, connectionPassword, true)
    }

    private fun sendMessageUsingInputStickUtility() {
        InputStickBroadcast.type(applicationContext, textToSend, "en-US")
        Toast.makeText(applicationContext, "Sent text to InputStickUtility…", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            unregisterReceiver(mReceiver)
        } catch (Exception: IllegalArgumentException) {
            // Not registered yet, that's fine
        }
        InputStickHID.disconnect()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                // Ensure we have the needed permissions
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_BLUETOOTH)
            }
            else if (resultCode == Activity.RESULT_CANCELED) {
                showBluetoothDeniedToast()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_BLUETOOTH -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Start watching Bluetooth devices
                    val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                    registerReceiver(mReceiver, filter)

                    // Create Bluetooth adapter
                    val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

                    // Start finding devices
                    mBluetoothAdapter.startDiscovery()

                    Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show()
                } else {
                    showBluetoothDeniedToast()
                }
                return
            }
        }
    }

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device = intent
                    .getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                if (device != null) {
                    val inputStick = getInputStickFromDB(device)
                    if (!mKnownDevicesList.contains(inputStick)) {
                        mBluetoothDeviceList.add(inputStick)
                    }
                }

                updateBluetoothDeviceList(context)
            }
        }
    }

    private fun showIncorrectPasswordDialog(inputStick: InputStick) {
        val editText = EditText(this)
        editText.setText(getDevicePassword(inputStick))
        editText.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD

        val dialog = AlertDialog.Builder(this)
            .setTitle("Incorrect InputStick password")
            .setMessage(String.format("Enter password for device %s with mac address %s", inputStick.name, inputStick.mac))
            .setView(editText)
            .setPositiveButton("Save") { _: DialogInterface, _: Int ->
                var devicePassword : String? = null
                if (editText.text.toString().isNotEmpty()) {
                    devicePassword = editText.text.toString()
                }
                setDevicePassword(inputStick, devicePassword)
                connectToInputStickUsingBluetooth(inputStick)
            }
            .setNegativeButton("Cancel") { _: DialogInterface, _: Int -> }
            .create()

        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        editText.requestFocus()
    }

    private fun showNewMessageDialog() {
        val editText = EditText(this)
        editText.inputType = InputType.TYPE_CLASS_TEXT

        val dialog = AlertDialog.Builder(this)
            .setTitle("Send message")
            .setMessage(String.format("Enter the text to send"))
            .setView(editText)
            .setPositiveButton("Send") { _: DialogInterface, _: Int ->
                textToSend = editText.text.toString()
                start()
            }
            .setNegativeButton("Cancel") { _: DialogInterface, _: Int -> }
            .create()

        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        editText.requestFocus()
    }

    private fun showBluetoothDeniedToast() {
        Toast.makeText(this, "Can only show the Use InputStickUtility option without Bluetooth and location permission...", Toast.LENGTH_LONG).show()
    }

    private fun updateBluetoothDeviceList(context: Context) {
        val deviceList = ArrayList<String>()

        deviceList.addAll(mBluetoothDeviceList.map {
            it.name + "\n" + it.mac
        })

        bluetoothDevicesListView?.adapter = ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, deviceList)
    }

    private fun updateKnownDeviceList(context: Context) {
        val deviceList = ArrayList<String>()

        mKnownDevicesList.clear()
        mKnownDevicesList.addAll(inputStickDao!!.getAllByLastUsed())

        deviceList.addAll(mKnownDevicesList.map {
            it.name + "\n" + it.mac
        })

        knownDevicesListView?.adapter = ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, deviceList)
    }

    override fun onStateChanged(state: Int) {
        when (state) {
            ConnectionManager.STATE_CONNECTED -> {
                Toast.makeText(this, "Connected", Toast.LENGTH_LONG).show()
            }
            ConnectionManager.STATE_CONNECTING -> {
                Toast.makeText(this, "Connecting", Toast.LENGTH_LONG).show()
            }
            ConnectionManager.STATE_READY -> {
                if (textToSend.isNotEmpty()) {
                    // Send mode
                    sendToBluetoothDevice(connectingDevice!!, textToSend)
                    finish()
                    finish()
                } else {
                    // Configure mode
                    // TODO: Allow changing the device's password here
                    AlertDialog.Builder(this)
                        .setTitle("Connection test successful")
                        .setMessage(String.format("Successfully connected to device %s with mac address %s. Use Android's share menu to send text to your InputStick.", connectingDevice!!.name, connectingDevice!!.mac))
                        .setPositiveButton("OK") { _: DialogInterface, _: Int -> }
                        .show()

                    InputStickHID.disconnect()
                }
            }
            ConnectionManager.STATE_FAILURE -> {
                when (InputStickHID.getErrorCode()) {
                    InputStickError.ERROR_SECURITY,
                    InputStickError.ERROR_SECURITY_CHALLENGE,
                    InputStickError.ERROR_SECURITY_INVALID_KEY,
                    InputStickError.ERROR_SECURITY_NOT_PROTECTED,
                    InputStickError.ERROR_SECURITY_NOT_SUPPORTED,
                    InputStickError.ERROR_SECURITY_NO_KEY -> {
                        showIncorrectPasswordDialog(connectingDevice!!)
                    }
                    else -> {
                        Toast.makeText(this, "Connection failed: " + InputStickError.getFullErrorMessage(InputStickHID.getErrorCode()), Toast.LENGTH_LONG).show()
                    }
                }
            }
            else -> {
                Toast.makeText(this, String.format("Unknown state: %s", state.toString()), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendToBluetoothDevice(inputStick: InputStick, textToSend: String) {
        inputStick.last_used = System.currentTimeMillis()
        inputStickDao!!.update(inputStick)

        Toast.makeText(this, "Sent text over Bluetooth…", Toast.LENGTH_LONG).show()
        InputStickKeyboard.type(textToSend, "en-US")
    }
}
