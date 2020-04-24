package mozilla.voice.assistant.intents.communication.ui.contact

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.no_contacts_fragment.*
import mozilla.voice.assistant.R
import mozilla.voice.assistant.intents.communication.contactUriToContactEntity

/**
 * A fragment instantiated when no Android contact matches the uttered name.
 * It informs the user and lets them open the contact picker to select a contact.
 */
internal class NoContactsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.no_contacts_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        noContactsStatusView.text = "No matches for ${(activity as? ContactActivity)?.viewModel?.nickname}."
        noContactsButton.setOnClickListener {
            startContactPicker()
        }
    }

    private fun startContactPicker() {
        startActivityForResult(
            Intent(Intent.ACTION_PICK).apply {
                type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
            },
            SELECT_CONTACT_FOR_NICKNAME
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_CONTACT_FOR_NICKNAME) {
            if (resultCode == Activity.RESULT_OK) {
                // TODO: Handle other cases
                data?.data?.let {
                    (activity as? ContactActivity)?.let { contactActivity ->
                        contactUriToContactEntity(contactActivity, it)
                            .let { contactEntity ->
                                contactActivity.addContact(contactEntity)
                                contactActivity.initiateRequestedActivity(contactEntity)
                            }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "NoContactsFragment"
        private const val SELECT_CONTACT_FOR_NICKNAME = 1

        @JvmStatic
        fun newInstance() = NoContactsFragment()
    }
}
