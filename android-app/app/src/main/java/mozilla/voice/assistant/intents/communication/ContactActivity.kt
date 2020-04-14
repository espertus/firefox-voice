package mozilla.voice.assistant.intents.communication

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import mozilla.voice.assistant.R
import mozilla.voice.assistant.intents.communication.ui.contact.ContactFragment

class ContactActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e(TAG, "Entering onCreate()")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contact_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, ContactFragment.newInstance())
                .commitNow()
        }
    }

    companion object {
        internal const val TAG = "ContactActivity"
        internal const val UTTERANCE_KEY = "utterance"
        internal const val MODE_KEY = "mode"
        internal const val SMS_MODE = "sms"
        internal const val PHONE_MODE = "phone"
        internal const val NICKNAME_KEY = "nickname"
    }
}
