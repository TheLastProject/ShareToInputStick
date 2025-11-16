package me.hackerchick.sharetoinputstick

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Insets
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.SpinnerAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.inputstick.api.ConnectionManager
import com.inputstick.api.InputStickError
import com.inputstick.api.InputStickStateListener
import com.inputstick.api.Util
import com.inputstick.api.basic.InputStickHID
import com.inputstick.api.basic.InputStickKeyboard
import com.inputstick.api.broadcast.InputStickBroadcast
import com.inputstick.api.layout.KeyboardLayout
import me.hackerchick.sharetoinputstick.databinding.ActivityMainBinding
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), InputStickStateListener {
    private lateinit var binding: ActivityMainBinding

    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothAdapterAutoRescan: Boolean = true

    private var mBusyDialog: AlertDialog? = null

    private var PERMISSION_REQUEST_BLUETOOTH = 1

    private var dbHelper: DBHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        setContentView(view)

        applyWindowInsets(view)

        setSupportActionBar(findViewById(R.id.appbar))

        dbHelper = DBHelper(this)

        val model: InputStickViewModel by viewModels()

        if (intent?.action == Intent.ACTION_SEND) {
            val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (extraText != null) {
                model.setTextToSend(extraText)
            }
        }

        binding.useInputStickUtilityButton.setOnClickListener {
            sendMessageUsingInputStickUtility()
        }

        binding.fab.setOnClickListener {
            showNewMessageDialog()
        }

        InputStickHID.addStateListener(this)

        // Register lists
        registerForContextMenu(binding.knownDevicesListView)
        binding.knownDevicesListView.setOnItemClickListener { _, _, position, _ ->
            connectToInputStickUsingBluetooth(dbHelper!!.getInputStick(model.getKnownDevicesList(applicationContext).value!![position].mac)!!)
        }

        binding.bluetoothDevicesListView.setOnItemClickListener { _, _, position, _ ->
            connectToInputStickUsingBluetooth(dbHelper!!.getInputStick(model.getBluetoothDevicesList().value!![position].mac)!!)
        }

        val knownDevicesObserver = Observer<ArrayList<InputStick>> {
            binding.knownDevicesListView.adapter = InputStickAdapter(this.applicationContext, it, model)
        }

        // Update lists on change
        model.getKnownDevicesList(applicationContext).observe(this, knownDevicesObserver)
        model.getBluetoothDevicesList().observe(this) {
            binding.bluetoothDevicesListView.adapter = InputStickAdapter(this.applicationContext, it, null)
            val knownDevices = model.getKnownDevicesList(applicationContext).value
            if (knownDevices != null) {
                binding.knownDevicesListView.adapter = InputStickAdapter(
                    this.applicationContext,
                    knownDevices,
                    model
                )
                binding.knownDevicesListView.adapter
            }
        }

        // Ensure we have the needed permissions
        if (Build.VERSION.SDK_INT >= 31) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                PERMISSION_REQUEST_BLUETOOTH
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                PERMISSION_REQUEST_BLUETOOTH
            )
        }

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
                val device = model.getKnownDevicesList(applicationContext).value!![info.position]
                device.password = null
                device.last_used = 0
                model.editDevice(applicationContext, device)

                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun start() {
        val model: InputStickViewModel by viewModels()

        if (model.getTextToSend().value!!.isEmpty()) {
            title = "Edit InputSticks"
            binding.fab.visibility = View.VISIBLE
            binding.useInputStickUtilityButton.visibility = View.GONE
        } else {
            title = "Share To InputStick"
            binding.fab.visibility = View.INVISIBLE
            binding.useInputStickUtilityButton.visibility = View.VISIBLE
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
        inputStick.last_used = System.currentTimeMillis()
        model.editDevice(applicationContext, inputStick)
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

    private val enableBluetoothActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_CANCELED) {
            showBluetoothDeniedToast()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_BLUETOOTH -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Request enabling bluetooth
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBluetoothActivityResult.launch(enableBtIntent)

                    // Start watching Bluetooth devices
                    val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    registerReceiver(mReceiver, filter)

                    // Create Bluetooth adapter
                    mBluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

                    // Start finding devices
                    if (mBluetoothAdapter != null) {
                        mBluetoothAdapter!!.startDiscovery()
                    } else {
                        Toast.makeText(this, "Bluetooth doesn't seem to be available on this device, exiting...", Toast.LENGTH_SHORT).show()
                        finish()
                    }
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
                val device = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                if (device != null) {
                    val inputStick = model.retrieveInputStick(applicationContext, device)
                    model.addToBluetoothDevicesList(inputStick)

                    val waitingDevice = model.getWaitingDevice().value
                    if (inputStick.mac == waitingDevice?.mac) {
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
                val connectingDevice = model.getConnectingDevice().value!!
                val textToSend = model.getTextToSend().value!!
                if (textToSend.isNotEmpty()) {
                    // Prepare keyboard layout state holding spinner
                    val keyboardLayoutSpinner = Spinner(this).apply {
                        val keyboardLayoutList = ArrayList<String>();
                        KeyboardLayout.getLayoutNames(true).forEachIndexed { index, layoutName ->
                            keyboardLayoutList.add(index, layoutName.toString())
                        }

                        adapter = ArrayAdapter(this@MainActivity, android.R.layout.select_dialog_item, keyboardLayoutList)
                    }

                    // Prepare typing speed state holding spinner
                    val typingSpeedSpinner = Spinner(this).apply {
                        val typingSpeedList = ArrayList<String>();
                        typingSpeedList.add(0, "100%")
                        typingSpeedList.add(1, "50%")
                        typingSpeedList.add(2, "33%")
                        typingSpeedList.add(3, "25%")

                        adapter = ArrayAdapter(this@MainActivity, android.R.layout.select_dialog_item, typingSpeedList)
                    }

                    // Show dialog asking for typing speed
                    MaterialAlertDialogBuilder(this).apply {
                        setTitle(R.string.send_text)
                        // Add multiple vertical entries
                        setView(LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            // Add entry for layout
                            addView(LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                addView(TextView(context).apply {
                                    text = getString(R.string.keyboard_layout)
                                })
                                addView(keyboardLayoutSpinner)
                            })
                            // Add entry for for typing speed
                            addView(LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                addView(TextView(context).apply {
                                    text = getString(R.string.typing_speed)
                                })
                                addView(typingSpeedSpinner)
                            })
                        })
                        setPositiveButton(getString(R.string.send)) { _, _ ->
                            // Retrieve keyboard layout
                            // We can safely use getLayoutCodes together with getLayoutNames as the InputStick API guarantees those are in the same order
                            val layoutCode = KeyboardLayout.getLayoutCodes()[keyboardLayoutSpinner.selectedItemPosition]

                            // Apply typing speed
                            when (typingSpeedSpinner.selectedItem) {
                                "100%" -> model.setInputSpeed(InputStickKeyboard.TYPING_SPEED_NORMAL)
                                "50%" -> model.setInputSpeed(InputStickKeyboard.TYPING_SPEED_050X)
                                "33%" -> model.setInputSpeed(InputStickKeyboard.TYPING_SPEED_033X)
                                "25%" -> model.setInputSpeed(InputStickKeyboard.TYPING_SPEED_025X)
                            }

                            // Send data
                            model.setSending(true)
                            updateBusyDialog(context, "Sending data...")
                            sendToBluetoothDevice(connectingDevice, textToSend, layoutCode.toString())
                            Toast.makeText(context, layoutCode.toString(), Toast.LENGTH_LONG).show()
                            closeAfterSendingCompletes()
                        }
                        show()
                    }

                    updateBusyDialog(this, null)
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
                }

                // Consider device used
                connectingDevice.last_used = System.currentTimeMillis()
                model.editDevice(applicationContext, connectingDevice)
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

    private fun sendToBluetoothDevice(inputStick: InputStick, textToSend: String, layoutCode: String) {
        val model: InputStickViewModel by viewModels()

        inputStick.last_used = System.currentTimeMillis()
        model.editDevice(applicationContext, inputStick)

        InputStickKeyboard.type(textToSend, layoutCode, model.getInputSpeed().value!!)
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

    fun applyWindowInsets(root: View) {
        /* This function basically fakes the activity being edge-to-edge. Useful for those activities that are really hard to get to behave well */
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            val layoutParams = view.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.leftMargin = insets.left
            layoutParams.bottomMargin = insets.bottom
            layoutParams.rightMargin = insets.right
            layoutParams.topMargin = insets.top
            view.layoutParams = layoutParams

            WindowInsetsCompat.CONSUMED
        }
    }
}