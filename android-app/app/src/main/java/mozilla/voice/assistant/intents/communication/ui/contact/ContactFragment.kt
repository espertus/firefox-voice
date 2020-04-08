package mozilla.voice.assistant.intents.communication.ui.contact

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
    }

    private lateinit var viewModel: ContactViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.contact_fragment, container, false)
    }

    private fun applyIfPossible(contacts: List<ContactEntity>) {
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
                // TODO: Remove self as observer.
                startActivity(intent)
            }
            ?: throw AssertionError("Could not find contact named ${viewModel.nickname}")
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
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

            viewModel.allUsers.observe(activity, Observer {
                applyIfPossible(it)
            })

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
}

class ContactViewModelFactory(
    val application: Application,
    val mode: String,
    val nickname: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        modelClass.getConstructor(Application::class.java, String::class.java, String::class.java)
            .newInstance(application, mode, nickname)
}

class ContactViewModel(
    application: Application,
    val mode: String,
    val nickname: String
) : AndroidViewModel(application) {
    private val repository: ContactRepository
    val allUsers: LiveData<List<ContactEntity>>
    var contactMap: Map<String, ContactEntity> = emptyMap()

    init {
        val contactsDao = ContactDatabase.getDatabase(application, viewModelScope).contactDao()
        repository = ContactRepository(contactsDao)
        allUsers = repository.allContacts
        allUsers.value?.map {
            it.nickname to it
        }?.let {
            contactMap = it.toMap()
        }
        // TODO: Update contactMap whenever allUsers changes.
    }

    fun insert(contact: ContactEntity) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(contact)
    }
}
