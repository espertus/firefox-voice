package mozilla.voice.assistant.intents.communication.ui.contact

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.cursoradapter.widget.CursorAdapter
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
import mozilla.voice.assistant.intents.communication.ContactDatabase
import mozilla.voice.assistant.intents.communication.ContactEntity
import mozilla.voice.assistant.intents.communication.ContactRepository
import mozilla.voice.assistant.intents.communication.contactIdToContactEntity

class ContactActivity : FragmentActivity() {
    internal lateinit var viewModel: ContactViewModel
    internal var contactLoader: ContactLoader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
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
        // Try to find an exact match in our database.
        val entity = viewModel.getContact()
        if (entity != null) {
            initiateRequestedActivity(entity)
            return
        }
        // If there's no match in the database, get permissions,
        // and call seekContactsWithNickname().
        getPermissions()
    }

    private fun getPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS),
                PERMISSIONS_REQUEST
            )
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
        ContactLoader().let {
            contactLoader = it
            LoaderManager.getInstance(this).initLoader(0, null, it)
        }
    }

    private fun buildModel() =
        ViewModelProvider(
            this,
            ContactViewModelFactory(
                application,
                intent.getStringExtra(MODE_KEY),
                intent.getStringExtra(NICKNAME_KEY)
                    .toLowerCase(Locale.getDefault())
            )
        ).get(ContactViewModel::class.java)

    internal fun initiateRequestedActivity(contact: ContactEntity) {
        val intent = when (viewModel.mode) {
            VOICE_MODE -> Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel: ${contact.voiceNumber}")
            }
            SMS_MODE -> Intent(
                Intent.ACTION_VIEW,
                Uri.fromParts("sms", contact.smsNumber, null)
            )
            else -> throw AssertionError("Illegal mode: ${viewModel.mode}")
        }
        startActivity(intent)
        finish()
    }

    internal fun addContact(contactEntity: ContactEntity) {
        viewModel.insert(contactEntity)
    }

    private fun addMultipleContactsFragment(cursor: Cursor) {
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fragment_container, MultipleContactsFragment(cursor))
            commit()
        }
    }

    private fun addNoContactFragment() {
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fragment_container, NoContactsFragment())
            commit()
        }
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
            when (cursor.count) {
                0 -> run {
                    cursor.close()
                    addNoContactFragment()
                }
                1 -> cursor.use {
                    it.moveToNext()
                    contactIdToContactEntity(
                        this@ContactActivity,
                        it.getLong(CONTACT_ID_INDEX)
                    ).let { contactEntity ->
                        addContact(contactEntity)
                        initiateRequestedActivity(contactEntity)
                    }
                }
                else -> addMultipleContactsFragment(cursor) // cursor closed by MultipleContactsFragment
            }
        }

        override fun onLoaderReset(loader: Loader<Cursor>) {
            cursorAdapter?.swapCursor(null)
        }
    }

    companion object {
        internal const val TAG = "ContactActivity"
        private const val PERMISSIONS_REQUEST = 100
        internal const val UTTERANCE_KEY = "utterance"
        internal const val MODE_KEY = "mode"
        internal const val SMS_MODE = "sms"
        internal const val VOICE_MODE = "phone"
        internal const val NICKNAME_KEY = "nickname"

        private val PROJECTION: Array<out String> = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )
        internal const val CONTACT_ID_INDEX = 0
        internal const val CONTACT_LOOKUP_KEY_INDEX = 1
        internal const val CONTACT_DISPLAY_NAME_INDEX = 2
        internal const val CONTACT_PHOTO_URI_INDEX = 3
        internal const val CONTACT_HAS_PHONE_NUMBER = 4

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
    private val repository: ContactRepository

    init {
        val contactsDao = ContactDatabase.getDatabase(
            application,
            viewModelScope
        ).contactDao()
        repository =
            ContactRepository(
                contactsDao
            )
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
