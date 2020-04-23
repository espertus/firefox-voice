package mozilla.voice.assistant.intents.communication.ui.contact

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.no_contacts_fragment.*

import mozilla.voice.assistant.R
import mozilla.voice.assistant.intents.communication.ContactActivity

/**
 * A simple [Fragment] subclass.
 * Use the [NoContactFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class NoContactFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
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
                    /* val contact = getContact(it)
                     viewModel.insert(contact)
                     initiateRequestedActivity(contact)
                    finish()
                     */

                }
            }
        }
    }

    companion object {
        private const val SELECT_CONTACT_FOR_NICKNAME = 1
    }
}
