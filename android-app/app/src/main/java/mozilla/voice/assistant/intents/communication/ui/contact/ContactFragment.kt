package mozilla.voice.assistant.intents.communication.ui.contact

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.cursoradapter.widget.CursorAdapter
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import mozilla.voice.assistant.R
import mozilla.voice.assistant.intents.communication.ContactActivity

class ContactFragment : Fragment(), LoaderManager.LoaderCallbacks<Cursor>, AdapterView.OnItemClickListener {
    companion object {
        fun newInstance() = ContactFragment()
        private const val TAG = "ContactFragment"
        private val PROJECTION: Array<out String> = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
        )
        private const val CONTACT_ID_INDEX = 0
        private const val CONTACT_DISPLAY_NAME_INDEX = 1
        private const val CONTACT_PHOTO_URI_INDEX = 2

        private const val SELECTION: String =
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
    }

    // Support for list of partially matching contacts
    var contactId: Long = 0
    var contactKey: String? = null
    var contactUri: Uri? = null
    private var cursorAdapter: ContactCursorAdapter? = null
    private var searchString: String? = null
    private val selectionArgs: Array<String> = arrayOf("")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.contacts_list_view, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loaderManager.initLoader(0, null, this)
    }

    override fun onCreateLoader(loaderId: Int, args: Bundle?): Loader<Cursor> =
        activity?.let {
            val contactActivity = it as? ContactActivity
            searchString = contactActivity?.viewModel?.nickname
            selectionArgs[0] = "%$searchString%"
            CursorLoader(
                it,
                ContactsContract.Contacts.CONTENT_URI,
                PROJECTION,
                SELECTION,
                selectionArgs,
                null
            )
        } ?: throw IllegalStateException()

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor) {
        activity?.also {
            val contactListView = it.findViewById<ListView>(R.id.lvItems)
            cursorAdapter = ContactCursorAdapter(it, cursor)
            contactListView.adapter = cursorAdapter
            // TODO: Set click listener
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        // Delete the reference to the existing Cursor
        cursorAdapter?.swapCursor(null)
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
                val displayName = it.getString(ContactFragment.CONTACT_DISPLAY_NAME_INDEX)
                val displayNameView = view?.findViewById<TextView>(R.id.tvContactDisplayName)
                displayNameView?.text = displayName
                it.getString(CONTACT_PHOTO_URI_INDEX)?.let { photoString ->
                    val iconView = view?.findViewById<ImageView>(R.id.ivContactPhoto)
                    iconView?.setImageURI(Uri.parse(photoString))
                }
            }
        }
    }
}
