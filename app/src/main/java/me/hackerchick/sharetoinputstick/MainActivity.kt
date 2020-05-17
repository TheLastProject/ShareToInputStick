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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
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
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothAdapterAutoRescan: Boolean = true

    private var mKnownDevicesListView: ListView? = null
    private var mBluetoothDevicesListView: ListView? = null

    private var mBusyDialog: AlertDialog? = null

    private var PERMISSION_REQUEST_BLUETOOTH = 1
    private var REQUEST_ENABLE_BLUETOOTH = 2

    private var mUseInputUtilityButton: View? = null
    private var mFab: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.appbar))

        val model: InputStickViewModel by viewModels()

        if (intent?.action == Intent.ACTION_SEND) {
            var extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (extraText != null) {
                model.setTextToSend(extraText)
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
            connectToInputStickUsingBluetooth(model.getKnownDevicesList().value!![position])
        }

        mBluetoothDevicesListView = findViewById(R.id.bluetoothDevicesListView)
        mBluetoothDevicesListView?.setOnItemClickListener { _, _, position, _ ->
            connectToInputStickUsingBluetooth(model.getBluetoothDevicesList().value!![position])
        }

        // Update lists on change
        model.getKnownDevicesList().observe(this, Observer<ArrayList<InputStick>> {
            var bluetoothDevices = model.getBluetoothDevicesList().value
            if (bluetoothDevices == null) {
                bluetoothDevices = ArrayList<InputStick>()
            }
            knownDevicesListView?.adapter = InputStickAdapter(this.applicationContext, it, bluetoothDevices)
        })
        model.getBluetoothDevicesList().observe(this, Observer<ArrayList<InputStick>> {
            bluetoothDevicesListView?.adapter = InputStickAdapter(this.applicationContext, it)
            var knownDevices = model.getKnownDevicesList().value
            if (knownDevices != null) {
                knownDevicesListView?.adapter = InputStickAdapter(
                    this.applicationContext,
                    knownDevices,
                    it
                )
            }
        })

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val model: InputStickViewModel by viewModels()

        var inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)

        when (model.getInputSpeed().value) {
            InputStickKeyboard.TYPING_SPEED_NORMAL -> {
                val menuItem = menu.findItem(R.id.option_typing_speed_100)
                menuItem.isChecked = true
            }
            InputStickKeyboard.TYPING_SPEED_050X -> {
                val menuItem = menu.findItem(R.id.option_typing_speed_50)
                menuItem.isChecked = true
            }
            InputStickKeyboard.TYPING_SPEED_033X -> {
                val menuItem = menu.findItem(R.id.option_typing_speed_33)
                menuItem.isChecked = true
            }
            InputStickKeyboard.TYPING_SPEED_025X -> {
                val menuItem = menu.findItem(R.id.option_typing_speed_25)
                menuItem.isChecked = true
            }
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.option_typing_speed_100 -> {
            val model: InputStickViewModel by viewModels()
            model.setInputSpeed(InputStickKeyboard.TYPING_SPEED_NORMAL)
            item.isChecked = true
            true
        }
        R.id.option_typing_speed_50 -> {
            val model: InputStickViewModel by viewModels()
            model.setInputSpeed(InputStickKeyboard.TYPING_SPEED_050X)
            item.isChecked = true
            true
        }
        R.id.option_typing_speed_33 -> {
            val model: InputStickViewModel by viewModels()
            model.setInputSpeed(InputStickKeyboard.TYPING_SPEED_033X)
            item.isChecked = true
            true
        }
        R.id.option_typing_speed_25 -> {
            val model: InputStickViewModel by viewModels()
            model.setInputSpeed(InputStickKeyboard.TYPING_SPEED_025X)
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
        val model: InputStickViewModel by viewModels()

        val info = item.menuInfo as AdapterView.AdapterContextMenuInfo
        return when (item.itemId) {
            R.id.forget -> {
                val device = model.getKnownDevicesList().value!![info.position]
                device.password = null
                device.last_used = 0
                model.editKnownDevice(device)

                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun start() {
        val model: InputStickViewModel by viewModels()

        if (model.getTextToSend().value!!.isEmpty()) {
            title = "Edit InputSticks"
            mFab?.visibility = View.VISIBLE
            mUseInputUtilityButton?.visibility = View.GONE
        } else {
            title = "Share To InputStick"
            mFab?.visibility = View.INVISIBLE
            mUseInputUtilityButton?.visibility = View.VISIBLE
        }

        // Reshow dialog if in progress
        updateBusyDialog(this, model.getBusyDialogMessage())

        // If sending, close after it completes
        if (model.isSending().value!!) {
            closeAfterSendingCompletes()
        }
    }

    private fun getDevicePassword(inputStick: InputStick) : String? {
        return inputStick.password
    }

    private fun setDevicePassword(inputStick: InputStick, devicePassword: String?) {
        val model: InputStickViewModel by viewModels()

        inputStick.password = devicePassword
        inputStick.last_used = System.currentTimeMillis() / 1000
        model.editKnownDevice(inputStick)
    }

    private fun connectToInputStickUsingBluetooth(device: InputStick) {
        val model: InputStickViewModel by viewModels()

        if (!model.bluetoothDevicesListContains(device)) {
            updateBusyDialog(this,"Waiting for device to show up in Bluetooth scan...")
            model.setWaitingDevice(device)
            return
        }

        model.setConnectingDevice(device)

        var connectionPassword: ByteArray? = null
        if (getDevicePassword(device) != null) {
            connectionPassword = Util.getPasswordBytes(getDevicePassword(device))
        }
        InputStickHID.connect(application, device.mac, connectionPassword, true)
    }

    private fun sendMessageUsingInputStickUtility() {
        val model: InputStickViewModel by viewModels()

        InputStickBroadcast.type(applicationContext, model.getTextToSend().value, "en-US")
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

        if (isFinishing) {
            InputStickHID.disconnect()
        }
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
        val model: InputStickViewModel by viewModels()

        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device = intent
                    .getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                if (device != null) {
                    val inputStick = model.retrieveInputStick(device)
                    model.addToBluetoothDevicesList(inputStick)

                    var waitingDevice = model.getWaitingDevice().value
                    if (waitingDevice != null && inputStick == waitingDevice) {
                        // We were waiting for this device, connect now
                        model.setWaitingDevice(null)
                        connectToInputStickUsingBluetooth(inputStick)
                    }
                }
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
        val model: InputStickViewModel by viewModels()

        val editText = EditText(this)
        editText.inputType = InputType.TYPE_CLASS_TEXT

        val dialog = AlertDialog.Builder(this)
            .setTitle("Send message")
            .setMessage(String.format("Enter the text to send"))
            .setView(editText)
            .setPositiveButton("Send") { _: DialogInterface, _: Int ->
                model.setTextToSend(editText.text.toString())
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

    override fun onStateChanged(state: Int) {
        val model: InputStickViewModel by viewModels()

        when (state) {
            ConnectionManager.STATE_CONNECTED -> {
                updateBusyDialog(this, "Preparing...")
            }
            ConnectionManager.STATE_CONNECTING -> {
                updateBusyDialog(this, "Connecting...")
            }
            ConnectionManager.STATE_READY -> {
                var connectingDevice = model.getConnectingDevice().value!!
                var textToSend = model.getTextToSend().value!!
                if (textToSend.isNotEmpty()) {
                    // Send mode
                    model.setSending(true)
                    updateBusyDialog(this, "Sending data...")
                    sendToBluetoothDevice(connectingDevice, textToSend)
                    closeAfterSendingCompletes()
                } else {
                    // Configure mode
                    // TODO: Allow changing the device's password here
                    updateBusyDialog(this, null)

                    AlertDialog.Builder(this)
                        .setTitle("Connection test successful")
                        .setMessage(String.format("Successfully connected to device %s with mac address %s. Use Android's share menu to send text to your InputStick.", connectingDevice.name, connectingDevice.mac))
                        .setPositiveButton("OK") { _: DialogInterface, _: Int -> }
                        .show()

                    InputStickHID.disconnect()

                    // Consider device used
                    connectingDevice.last_used = System.currentTimeMillis()
                    model.editKnownDevice(connectingDevice)
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
                        showIncorrectPasswordDialog(model.getConnectingDevice().value!!)
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
        val model: InputStickViewModel by viewModels()

        inputStick.last_used = System.currentTimeMillis()
        model.editKnownDevice(inputStick)

        InputStickKeyboard.type(textToSend, "en-US", model.getInputSpeed().value!!)
    }

    private fun updateBusyDialog(context: Context, message: String?) {
        val model: InputStickViewModel by viewModels()
        model.setBusyDialogMessage(message)

        if (mBusyDialog == null) {
            mBusyDialog = AlertDialog.Builder(context)
                .setNegativeButton("Cancel") { _: DialogInterface, _: Int ->
                    InputStickHID.disconnect()
                    model.setBusyDialogMessage(null)
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