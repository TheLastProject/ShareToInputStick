package me.hackerchick.sharetoinputstick

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.room.Room
import com.inputstick.api.ConnectionManager
import com.inputstick.api.InputStickError
import com.inputstick.api.InputStickStateListener
import com.inputstick.api.Util
import com.inputstick.api.basic.InputStickHID
import com.inputstick.api.basic.InputStickKeyboard
import com.inputstick.api.broadcast.InputStickBroadcast
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity(), InputStickStateListener {
    private var inputStickDao: InputStickDao? = null

    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothAdapterAutoRescan: Boolean = true

    private var mKnownDevicesListView: ListView? = null
    private var mBluetoothDevicesListView: ListView? = null
    private val mKnownDevicesList = ArrayList<InputStick>()
    private val mBluetoothDevicesList = ArrayList<InputStick>()

    private var mWaitingDevice: InputStick? = null
    private var mConnectingDevice: InputStick? = null

    private var mBusyDialog: AlertDialog? = null

    private var PERMISSION_REQUEST_BLUETOOTH = 1
    private var REQUEST_ENABLE_BLUETOOTH = 2

    private var mTextToSend: String = ""

    private var mUseInputUtilityButton: View? = null
    private var mFab: View? = null

    private var mInputSpeed: Int = InputStickKeyboard.TYPING_SPEED_NORMAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.appbar))

        inputStickDao = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "inputsticks"
        ).allowMainThreadQueries().build().inputStickDao()

        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
                mTextToSend = intent.getStringExtra(Intent.EXTRA_TEXT)!!
            }
        }

        mUseInputUtilityButton = findViewById(R.id.useInputStickUtilityButton)
        mUseInputUtilityButton?.setOnClickListener {
            sendMessageUsingInputStickUtility()
        }

        mFab = findViewById(R.id.fab)
        mFab?.setOnClickListener {
            showNewMessageDialog()
        }

        InputStickHID.addStateListener(this)

        // Register lists
        mKnownDevicesListView = findViewById(R.id.knownDevicesListView)
        registerForContextMenu(mKnownDevicesListView)
        mKnownDevicesListView?.setOnItemClickListener { _, _, position, _ ->
            connectToInputStickUsingBluetooth(mKnownDevicesList[position])
        }

        mBluetoothDevicesListView = findViewById(R.id.bluetoothDevicesListView)
        mBluetoothDevicesListView?.setOnItemClickListener { _, _, position, _ ->
            connectToInputStickUsingBluetooth(mBluetoothDevicesList[position])
        }

        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)

        start()
    }

    override fun onPause() {
        mBluetoothAdapterAutoRescan = false
        mBluetoothAdapter?.cancelDiscovery()
        InputStickHID.removeStateListener(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mBluetoothAdapterAutoRescan = false
        mBluetoothAdapter?.startDiscovery()
        InputStickHID.addStateListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        var inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.option_typing_speed_100 -> {
            mInputSpeed = InputStickKeyboard.TYPING_SPEED_NORMAL
            item.isChecked = true
            true
        }
        R.id.option_typing_speed_50 -> {
            mInputSpeed = InputStickKeyboard.TYPING_SPEED_050X
            item.isChecked = true
            true
        }
        R.id.option_typing_speed_33 -> {
            mInputSpeed = InputStickKeyboard.TYPING_SPEED_033X
            item.isChecked = true
            true
        }
        R.id.option_typing_speed_25 -> {
            mInputSpeed = InputStickKeyboard.TYPING_SPEED_025X
            item.isChecked = true
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.inputstick_context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val info = item.menuInfo as AdapterView.AdapterContextMenuInfo
        return when (item.itemId) {
            R.id.forget -> {
                val device = mKnownDevicesList[info.position]
                device.password = null
                device.last_used = 0
                inputStickDao!!.update(device)

                updateBluetoothDeviceList(this)
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun start() {
        if (mTextToSend.isEmpty()) {
            title = "Edit InputSticks"
            mFab?.visibility = View.VISIBLE
            mUseInputUtilityButton?.visibility = View.GONE
        } else {
            title = "Share To InputStick"
            mFab?.visibility = View.INVISIBLE
            mUseInputUtilityButton?.visibility = View.VISIBLE
        }

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
    }

    private fun connectToInputStickUsingBluetooth(device: InputStick) {
        if (!mBluetoothDevicesList.contains(device)) {
            updateBusyDialog(this, "Waiting for device to show up in Bluetooth scan...")
            mWaitingDevice = device
            return
        }

        mConnectingDevice = device

        var connectionPassword: ByteArray? = null
        if (getDevicePassword(device) != null) {
            connectionPassword = Util.getPasswordBytes(getDevicePassword(device))
        }
        InputStickHID.connect(application, device.mac, connectionPassword, true)
    }

    private fun sendMessageUsingInputStickUtility() {
        InputStickBroadcast.type(applicationContext, mTextToSend, "en-US")
        Toast.makeText(applicationContext, "Sent text to InputStickUtility…", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun closeAfterSendingCompletes() {
        // Wait until there's no text left to send before disconnecting
        val executor = ScheduledThreadPoolExecutor(1)
        executor.scheduleWithFixedDelay({
            if (InputStickHID.isKeyboardLocalBufferEmpty()) finish()
        }, 0L, 10, TimeUnit.MILLISECONDS)
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
                    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    registerReceiver(mReceiver, filter)

                    // Create Bluetooth adapter
                    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

                    // Start finding devices
                    mBluetoothAdapter!!.startDiscovery()
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
                    if (!mBluetoothDevicesList.contains(inputStick)) {
                        mBluetoothDevicesList.add(inputStick)
                    }

                    if (inputStick == mWaitingDevice) {
                        // We were waiting for this device, connect now
                        mWaitingDevice = null
                        connectToInputStickUsingBluetooth(inputStick)
                    }
                }

                updateBluetoothDeviceList(context)
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == intent.action) {
                if (mBluetoothAdapterAutoRescan) {
                    mBluetoothAdapter!!.startDiscovery()

                    Toast.makeText(context, "Scanning...", Toast.LENGTH_SHORT).show()
                }
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
                mTextToSend = editText.text.toString()
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
        bluetoothDevicesListView?.adapter = InputStickAdapter(context, mBluetoothDevicesList)

        updateKnownDeviceList(context)
    }

    private fun updateKnownDeviceList(context: Context) {
        mKnownDevicesList.clear()
        mKnownDevicesList.addAll(inputStickDao?.getAllByLastUsed() as ArrayList)

        knownDevicesListView?.adapter = InputStickAdapter(context, mKnownDevicesList, mBluetoothDevicesList)
    }

    override fun onStateChanged(state: Int) {
        when (state) {
            ConnectionManager.STATE_CONNECTED -> {
                updateBusyDialog(this, "Preparing...")
            }
            ConnectionManager.STATE_CONNECTING -> {
                updateBusyDialog(this, "Connecting...")
            }
            ConnectionManager.STATE_READY -> {
                if (mTextToSend.isNotEmpty()) {
                    // Send mode
                    updateBusyDialog(this, "Sending data...")
                    sendToBluetoothDevice(mConnectingDevice!!, mTextToSend)
                    closeAfterSendingCompletes()
                } else {
                    // Configure mode
                    // TODO: Allow changing the device's password here
                    updateBusyDialog(this, null)

                    AlertDialog.Builder(this)
                        .setTitle("Connection test successful")
                        .setMessage(String.format("Successfully connected to device %s with mac address %s. Use Android's share menu to send text to your InputStick.", mConnectingDevice!!.name, mConnectingDevice!!.mac))
                        .setPositiveButton("OK") { _: DialogInterface, _: Int -> }
                        .show()

                    InputStickHID.disconnect()

                    // Consider device used
                    mConnectingDevice!!.last_used = System.currentTimeMillis()
                    inputStickDao?.update(mConnectingDevice!!)

                    updateBluetoothDeviceList(this)
                }
            }
            ConnectionManager.STATE_FAILURE -> {
                updateBusyDialog(this, null)

                when (InputStickHID.getErrorCode()) {
                    InputStickError.ERROR_SECURITY,
                    InputStickError.ERROR_SECURITY_CHALLENGE,
                    InputStickError.ERROR_SECURITY_INVALID_KEY,
                    InputStickError.ERROR_SECURITY_NOT_PROTECTED,
                    InputStickError.ERROR_SECURITY_NOT_SUPPORTED,
                    InputStickError.ERROR_SECURITY_NO_KEY -> {
                        showIncorrectPasswordDialog(mConnectingDevice!!)
                    }
                    else -> {
                        AlertDialog.Builder(this)
                            .setTitle("Failed to connect")
                            .setMessage(InputStickError.getFullErrorMessage(InputStickHID.getErrorCode()))
                            .setPositiveButton("OK") { _: DialogInterface, _: Int -> }
                            .show()
                    }
                }
            }
        }
    }

    private fun sendToBluetoothDevice(inputStick: InputStick, textToSend: String) {
        inputStick.last_used = System.currentTimeMillis()
        inputStickDao!!.update(inputStick)

        InputStickKeyboard.type(textToSend, "en-US", mInputSpeed)
    }

    private fun updateBusyDialog(context: Context, message: String?) {
        if (mBusyDialog == null) {
            mBusyDialog = AlertDialog.Builder(context)
                .setNegativeButton("Cancel") { _: DialogInterface, _: Int ->
                    InputStickHID.disconnect()
                }
                .create()
        }

        if (message != null) {
            mBusyDialog!!.setMessage(message)
            mBusyDialog!!.show()
        } else {
            mBusyDialog!!.hide()
        }
    }
}
