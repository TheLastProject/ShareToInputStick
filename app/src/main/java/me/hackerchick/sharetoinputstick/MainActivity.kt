package me.hackerchick.sharetoinputstick

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.inputstick.api.broadcast.InputStickBroadcast

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        when {
            intent?.action == Intent.ACTION_SEND -> {
                handleSendText(intent)
            }
        }
    }

    private fun handleSendText(intent: Intent) {
        var sendText = intent.getStringExtra(Intent.EXTRA_TEXT);

        sendText?.let {
            InputStickBroadcast.type(applicationContext, sendText, "en-US")
        }
    }
}
