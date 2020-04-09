package mozilla.voice.assistant.intents.communication.ui.contact

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.voice.assistant.R
import mozilla.voice.assistant.intents.communication.ContactActivity

class ContactFragment : Fragment() {
    companion object {
        fun newInstance() = ContactFragment()
        private const val TAG = "ContactFragment"
        private const val SELECT_CONTACT_FOR_NICKNAME = 1
    }

    private lateinit var viewModel: ContactViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.contact_fragment, container, false)
    }

    private val observer: Observer<List<ContactEntity>> by lazy {
        Observer<List<ContactEntity>> { contacts ->
            Log.e(TAG, "observer is running with contacts: ${contacts.map { it.nickname } }")
            contacts.firstOrNull { it.nickname.toLowerCase() == viewModel.nickname }
                ?.also { contact ->
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
                    viewModel.allUsers.removeObserver(observer)
                    startActivity(intent)
                }
                ?: run {
                    // If no contact found, open the contact picker.
                    viewModel.allUsers.removeObserver(observer)
                    startActivityForResult(
                        Intent(Intent.ACTION_PICK).apply {
                            type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
                        },
                        SELECT_CONTACT_FOR_NICKNAME
                    )
                }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Log.e(TAG, "Entering onActivityCreated()")
        super.onActivityCreated(savedInstanceState)

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

            viewModel.allUsers.observe(activity, observer)

            /*
            // Set up RecyclerView.
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recyclerview)
            val adapter = ContactListAdapter(activity)
            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(activity)

            // Link them together.
            viewModel.allUsers.observe(activity, Observer { contacts ->
                contacts?.let { adapter.setContacts(it) }
            })
             */
        } ?: throw AssertionError("Unable to get parent activity from fragment")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.e(TAG, "In onActivityResult()")
        // https://stackoverflow.com/a/56574502/631051
        if (requestCode == SELECT_CONTACT_FOR_NICKNAME) {
            if (resultCode == Activity.RESULT_OK) {
                data?.data?.let { contactUri ->
                    val projection = arrayOf(
                        android.provider.BaseColumns._ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                        ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
                    )
                    val cursor = requireContext().contentResolver.query(
                        contactUri, projection, null, null, null
                    )
                    if (cursor != null && cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val name =
                            cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY))
                        val number =
                            cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER))
                        viewModel.insert(
                            ContactEntity(viewModel.nickname, name, id, number)
                        )
                    }
                    cursor?.close()
                }
            }
        }
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
