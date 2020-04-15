package mozilla.voice.assistant.intents.communication

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.ContactsContract
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.android.synthetic.main.contact_fragment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.voice.assistant.R
import mozilla.voice.assistant.intents.communication.ui.contact.ContactDatabase
import mozilla.voice.assistant.intents.communication.ui.contact.ContactEntity
import mozilla.voice.assistant.intents.communication.ui.contact.ContactRepository

class ContactActivity : FragmentActivity() {
    private lateinit var viewModel: ContactViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e(TAG, "Entering onCreate()")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contact_activity)
    }

    override fun onStart() {
        super.onStart()
        contactRequestView.text = intent?.getStringExtra(UTTERANCE_KEY)
        viewModel = buildModel()
        viewModel.viewModelScope.launch {
            searchDatabaseForNickname()
        }
    }

    private suspend fun searchDatabaseForNickname() {
        // 1. Try to find an exact match in our database.
        val entity = viewModel.getContact()
        if (entity != null) {
            initiateRequestedActivity(entity)
            return
        }
        // 2. Search device contacts.

        // 3. Start contact picker.
        withContext(Dispatchers.Main) {
            startContactPicker()
        }
    }

    private fun buildModel() =
        ViewModelProvider(
            this,
            ContactViewModelFactory(
                application,
                intent.getStringExtra(ContactActivity.MODE_KEY),
                intent.getStringExtra(ContactActivity.NICKNAME_KEY)
                    .toLowerCase(Locale.getDefault())
            )
        ).get(ContactViewModel::class.java)

    private fun initiateRequestedActivity(contact: ContactEntity) {
        val intent = when (viewModel.mode) {
            ContactActivity.PHONE_MODE -> Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel: ${contact.phone}")
            }
            ContactActivity.SMS_MODE -> Intent(
                Intent.ACTION_VIEW,
                Uri.fromParts("sms", contact.phone, null)
            )
            else -> throw AssertionError("Illegal mode: ${viewModel.mode}")
        }
        startActivity(intent)
        finish()
    }

    private fun startContactPicker() {
        Log.e(TAG, "About to start contact intent")
        startActivityForResult(
            Intent(Intent.ACTION_PICK).apply {
                type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
            },
            SELECT_CONTACT_FOR_NICKNAME
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.e(TAG, "Entering onActivityResult()")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_CONTACT_FOR_NICKNAME) {
            if (resultCode == Activity.RESULT_OK) {
                // TODO: Handle other cases
                data?.data?.let {
                    val contact = getContact(it)
                    viewModel.insert(contact)
                    initiateRequestedActivity(contact)
                    finish()
                }
            }
        }
    }

    private fun getContact(contactUri: Uri): ContactEntity {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            BaseColumns._ID,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
        )
        val cursor = contentResolver.query(
            contactUri, projection, null, null, null
        )
        if (cursor != null && cursor.moveToFirst()) {
            val newContact = ContactEntity(
                viewModel.nickname,
                cursor.getString(0), // DISPLAY_NAME_PRIMARY
                cursor.getLong(1), // _ID
                cursor.getString(2) // NORMALIZED_NUMBER
            )
            cursor.close()
            return newContact
        } else {
            throw java.lang.AssertionError("Unhandled case")
        }
    }

    companion object {
        internal const val TAG = "ContactActivity"
        private const val SELECT_CONTACT_FOR_NICKNAME = 1
        internal const val UTTERANCE_KEY = "utterance"
        internal const val MODE_KEY = "mode"
        internal const val SMS_MODE = "sms"
        internal const val PHONE_MODE = "phone"
        internal const val NICKNAME_KEY = "nickname"
    }
}

class ContactViewModelFactory(
    private val application: Application,
    private val mode: String,
    private val nickname: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        modelClass.getConstructor(
            Application::class.java,
            String::class.java,
            String::class.java
        ).newInstance(application, mode, nickname)
}

class ContactViewModel(
    application: Application,
    val mode: String,
    val nickname: String
) : AndroidViewModel(application) {
    val repository: ContactRepository

    init {
        val contactsDao = ContactDatabase.getDatabase(application, viewModelScope).contactDao()
        repository = ContactRepository(contactsDao)
    }

    suspend fun getContact(contactNickname: String = nickname): ContactEntity? {
        var contact: ContactEntity? = null
        withContext(Dispatchers.IO) {
            contact = repository.get(contactNickname)
        }
        return contact
    }

    fun insert(contact: ContactEntity) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(contact)
    }
}
