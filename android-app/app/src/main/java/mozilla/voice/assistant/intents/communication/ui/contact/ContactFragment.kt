package mozilla.voice.assistant.intents.communication.ui.contact

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.ContactsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.voice.assistant.R
import mozilla.voice.assistant.intents.communication.ContactActivity

// https://code.luasoftware.com/tutorials/android/android-livedata-observe-once-only-kotlin/
fun <T> LiveData<T>.observeOnce(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
    observe(lifecycleOwner, object : Observer<T> {
        override fun onChanged(t: T?) {
            observer.onChanged(t)
            removeObserver(this)
        }
    })
}

class ContactFragment : Fragment() {
    companion object {
        fun newInstance() = ContactFragment()
        private const val TAG = "ContactFragment"
        private const val SELECT_CONTACT_FOR_NICKNAME = 1
    }

    private lateinit var viewModel: ContactViewModel
    private var newContact: ContactEntity? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.contact_fragment, container, false)
    }

    private fun initiateActivity(contact: ContactEntity) {
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

    private val observer: Observer<List<ContactEntity>> by lazy {
        Observer<List<ContactEntity>> { contacts ->
            Log.e(
                TAG,
                "observer is running with contacts: ${contacts.map { it.nickname }}, newContact: $newContact"
            )
            (contacts.firstOrNull { it.nickname.toLowerCase() == viewModel.nickname } ?: newContact)
                ?.also { initiateActivity(it) }
                ?: startContactPicker()
        }
    }

    override fun onStart() {
        Log.e(TAG, "Entering onStart()")
        super.onStart()
        activity?.let { activity ->
            val mode = activity.intent.getStringExtra(ContactActivity.MODE_KEY)
            val nickname =
                activity.intent.getStringExtra(ContactActivity.NICKNAME_KEY).toLowerCase()
            // Set up model.
            viewModel = ViewModelProvider(
                activity,
                ContactViewModelFactory(
                    activity.application,
                    mode,
                    nickname
                )
            ).get(ContactViewModel::class.java)
            viewModel.allUsers.observeOnce(activity, observer)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.e(TAG, "Entering onActivityResult()")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_CONTACT_FOR_NICKNAME) {
            if (resultCode == Activity.RESULT_OK) {
                data?.data?.let { addContact(it) }
            }
        }
    }

    private fun addContact(contactUri: Uri) {
        // https://stackoverflow.com/a/56574502/631051
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            BaseColumns._ID,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
        )
        val cursor = requireContext().contentResolver.query(
            contactUri, projection, null, null, null
        )
        if (cursor != null && cursor.moveToFirst()) {
            newContact = ContactEntity(
                viewModel.nickname,
                cursor.getString(0), // DISPLAY_NAME_PRIMARY
                cursor.getLong(1), // _ID
                cursor.getString(2) // NORMALIZED_NUMBER
            ).apply {
                Log.e(TAG, "Inserting record for $nickname")
                viewModel.insert(this)
            }
        }
        cursor?.close()
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
    val allUsers: LiveData<List<ContactEntity>>

    init {
        val contactsDao = ContactDatabase.getDatabase(application, viewModelScope).contactDao()
        repository = ContactRepository(contactsDao)
        allUsers = repository.allContacts
    }

    fun insert(contact: ContactEntity) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(contact)
    }
}
