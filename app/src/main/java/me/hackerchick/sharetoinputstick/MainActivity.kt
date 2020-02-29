package me.hackerchick.sharetoinputstick

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import android.bluetooth.BluetoothAdapter
import android.widget.ArrayAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.widget.ListView
import androidx.core.app.ActivityCompat
import android.content.Intent
import android.app.Activity
import com.inputstick.api.*
import com.inputstick.api.basic.InputStickHID
import com.inputstick.api.basic.InputStickKeyboard
import com.inputstick.api.broadcast.InputStickBroadcast


class MainActivity : AppCompatActivity(), InputStickStateListener {
    private var listView: ListView? = null
    private val mDeviceList = ArrayList<BluetoothDevice>()

    private var inputStickConnectionManager: BTConnectionManager? = null

    private var PERMISSION_REQUEST_BLUETOOTH = 1
    private var REQUEST_ENABLE_BLUETOOTH = 2

    private var textToSend: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
                textToSend = intent.getStringExtra(Intent.EXTRA_TEXT)!!
            }
        }

        if (textToSend.isEmpty()) {
            Toast.makeText(this, "No configuration settings supported yet. Please use Android's Share functionality.", Toast.LENGTH_LONG).show()
            finish()
        }

        InputStickHID.addStateListener(this)

        // Register list
        listView = findViewById(R.id.listView)
        listView?.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                sendMessageUsingInputStickUtility()
            } else {
                // -1 because the first option in the list is "Use InputStickUtility" and not a device
                sendMessageUsingBluetooth(mDeviceList[position - 1])
            }
        }

        updateDeviceList(this)

        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
    }

    private fun sendMessageUsingBluetooth(device: BluetoothDevice) {
        Toast.makeText(this, device.address, Toast.LENGTH_SHORT).show()

        InputStickHID.connect(application, device.address, null, true)
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
        inputStickConnectionManager?.disconnect()
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
                    mDeviceList.add(device)
                }

                updateDeviceList(context)
            }
        }
    }

    private fun showBluetoothDeniedToast() {
        Toast.makeText(this, "Can only show the Use InputStickUtility option without Bluetooth and location permission...", Toast.LENGTH_LONG).show()
    }

    private fun updateDeviceList(context: Context) {
        val deviceList = ArrayList<String>()
        deviceList.add("Use InputStickUtility (Must be installed)")
        deviceList.addAll(mDeviceList.map { it.name + "\n" + it.address })

        listView?.adapter = ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, deviceList)
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
                Toast.makeText(this, "Sent text over Bluetooth…", Toast.LENGTH_LONG).show()
                InputStickKeyboard.type(textToSend, "en-US")
                finish()
            }
            ConnectionManager.STATE_DISCONNECTED -> {
                Toast.makeText(this, "Disconnected", Toast.LENGTH_LONG).show()
            }
            ConnectionManager.STATE_FAILURE -> {
                Toast.makeText(this, "Connection failed: " + InputStickError.getFullErrorMessage(InputStickHID.getErrorCode()), Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(this, String.format("Unknown state: %s", state.toString()), Toast.LENGTH_LONG).show()
            }
        }
    }
}
