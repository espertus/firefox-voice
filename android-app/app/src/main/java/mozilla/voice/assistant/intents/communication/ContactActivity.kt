package mozilla.voice.assistant.intents.communication

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.ContactsContract
import android.util.Log
import android.widget.CursorAdapter
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.voice.assistant.R
import mozilla.voice.assistant.intents.communication.ui.contact.ContactDatabase
import mozilla.voice.assistant.intents.communication.ui.contact.ContactEntity
import mozilla.voice.assistant.intents.communication.ui.contact.ContactFragment
import mozilla.voice.assistant.intents.communication.ui.contact.ContactRepository

class ContactActivity : FragmentActivity() {
    internal lateinit var viewModel: ContactViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e(TAG, "Entering onCreate()")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contact_activity)
    }

    override fun onStart() {
        super.onStart()
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
        getPermissions()
    }

    private suspend fun getPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), PERMISSIONS_REQUEST)
        } else {
            seekContactsWithNickname()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                seekContactsWithNickname()
            } else {
                Toast.makeText(this, "Unable to proceed without permissions", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun seekContactsWithNickname() {
        val contactLoader: LoaderManager.LoaderCallbacks<Cursor> = ContactLoader()
        LoaderManager.getInstance(this).initLoader(0, null, contactLoader)
        /*
        val contactFragment = ContactFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, contactFragment)
            .commit()

        // 3. Start contact picker.
        withContext(Dispatchers.Main) {
            startContactPicker()
        }

         */
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

    // Called by fragment when it has resolved the contact.
    internal fun resolveContact() {}

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

    private fun addContact() {
        // TODO: Add contact to database.
        // TODO: Launch activity.
    }

    private fun addContactFragment(cursor: Cursor) {
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.fragment_container, ContactFragment(cursor))
        ft.commit()
    }

    private fun addNoContactFragment() {
        // TODO: Ask user to pick a contact.
    }

    inner class ContactLoader : LoaderManager.LoaderCallbacks<Cursor> {
        private var cursorAdapter: CursorAdapter? = null

        internal fun registerAdapter(adapter: CursorAdapter) {
            cursorAdapter = adapter
        }

        override fun onCreateLoader(loaderId: Int, args: Bundle?): Loader<Cursor> {
            val nickname = viewModel.nickname
            return CursorLoader(
                this@ContactActivity,
                ContactsContract.Contacts.CONTENT_URI,
                PROJECTION,
                SELECTION,
                arrayOf(
                    nickname, // just the nickname
                    "$nickname %", // first name
                    "% $nickname", // last name
                    "% $nickname %" // middle name
                ),
                null
            )
        }

        override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor) {
            Log.e(TAG, "Finished loading")
            when (cursor.count) {
                0 -> run {
                    cursor.close()
                    addNoContactFragment()
                }
                1 -> addContact()
                else -> addContactFragment(cursor)
            }
        }

        override fun onLoaderReset(loader: Loader<Cursor>) {
            cursorAdapter?.swapCursor(null)
        }
    }

    companion object {
        internal const val TAG = "ContactActivity"
        private const val SELECT_CONTACT_FOR_NICKNAME = 1
        private const val PERMISSIONS_REQUEST = 100
        internal const val UTTERANCE_KEY = "utterance"
        internal const val MODE_KEY = "mode"
        internal const val SMS_MODE = "sms"
        internal const val PHONE_MODE = "phone"
        internal const val NICKNAME_KEY = "nickname"

        private val PROJECTION: Array<out String> = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
        )
        internal const val CONTACT_ID_INDEX = 0
        internal const val CONTACT_KEY_INDEX = 1
        internal const val CONTACT_DISPLAY_NAME_INDEX = 2
        internal const val CONTACT_PHOTO_URI_INDEX = 3

        private const val TERM = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        private val SELECTION: String =
            generateSequence { TERM }
            .take(4)
            .joinToString(separator = " OR ")
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
