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
    private var searchString: String? = null
    private val selectionArgs: Array<String> = arrayOf("")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.contacts_fragment, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.let {
            cursorAdapter = ContactCursorAdapter(it, cursor)
            lvItems.adapter = cursorAdapter
        }
    }

    override fun onItemClick(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
        /*
        // Get the Cursor
        val cursor: Cursor? = (parent.adapter as? CursorAdapter)?.cursor?.apply {
            // Move to the selected contact
            moveToPosition(position)
            // Get the _ID value
            contactId = getLong(CONTACT_ID_INDEX)
            // Get the selected LOOKUP KEY
            contactKey = getString(CONTACT_KEY_INDEX)
            // Create the contact's content Uri
            contactUri = ContactsContract.Contacts.getLookupUri(contactId, contactKey)
        }

         */
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
