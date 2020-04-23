package mozilla.voice.assistant.intents.communication.ui.contact

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.TextView
import androidx.cursoradapter.widget.CursorAdapter
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.contacts_fragment.*
import mozilla.voice.assistant.R
import mozilla.voice.assistant.intents.communication.ContactActivity

class ContactFragment(
    private val cursor: Cursor
) : Fragment(), AdapterView.OnItemClickListener {
    companion object {
        private const val TAG = "ContactFragment"
    }

    // Support for list of partially matching contacts
    private var cursorAdapter: ContactCursorAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.contacts_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        (activity as? ContactActivity)?.let {
            val nickname = it.viewModel.nickname.capitalize()
            contactStatusView.text =
                "Found ${cursor.count} matches.\nSay or tap the $nickname you want."
            cursorAdapter = ContactCursorAdapter(it, cursor)
            contactsList.adapter = cursorAdapter
            contactsList.onItemClickListener = this
        }
    }

    override fun onItemClick(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
        (activity as? ContactActivity)?.let { contactActivity ->
            contactActivity.cursorToContactEntity(contactActivity, cursor, position).let {
                if (contactsCheckBox.isChecked) {
                    contactActivity.addContact(it)
                }
                contactActivity.initiateRequestedActivity(it)
            }
        }
    }

    class ContactCursorAdapter(
        context: Context,
        cursor: Cursor
    ) : CursorAdapter(context, cursor, 0) {
        override fun newView(context: Context?, cursor: Cursor?, parent: ViewGroup?) =
            LayoutInflater.from(context).inflate(R.layout.contact_list_item, parent, false)

        override fun bindView(view: View?, context: Context?, cursor: Cursor?) {
            cursor?.let {
                val displayName = it.getString(ContactActivity.CONTACT_DISPLAY_NAME_INDEX)
                val displayNameView = view?.findViewById<TextView>(R.id.tvContactDisplayName)
                displayNameView?.text = displayName
                it.getString(ContactActivity.CONTACT_PHOTO_URI_INDEX)?.let { photoString ->
                    val iconView = view?.findViewById<ImageView>(R.id.ivContactPhoto)
                    iconView?.setImageURI(Uri.parse(photoString))
                }
            }
        }
    }
}
