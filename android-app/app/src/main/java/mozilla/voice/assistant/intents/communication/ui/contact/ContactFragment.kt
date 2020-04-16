package mozilla.voice.assistant.intents.communication.ui.contact

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import androidx.cursoradapter.widget.CursorAdapter
import androidx.cursoradapter.widget.SimpleCursorAdapter
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

        private val FROM_COLUMNS: Array<String> = arrayOf(
            ContactsContract.Contacts.DISPLAY_NAME
        )
        private val TO_IDS: IntArray = intArrayOf(android.R.id.text1)
        private val PROJECTION: Array<out String> = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )

        // The column index for the _ID column
        private const val CONTACT_ID_INDEX: Int = 0

        // The column index for the CONTACT_KEY column
        private const val CONTACT_KEY_INDEX: Int = 1
        private const val SELECTION: String =
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
    }

    // Support for list of partially matching contacts
    var contactId: Long = 0
    var contactKey: String? = null
    var contactUri: Uri? = null
    private var cursorAdapter: SimpleCursorAdapter? = null
    private var searchString: String? = null
    private val selectionArgs: Array<String> = arrayOf("")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.contacts_list_view, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.also {
            cursorAdapter = SimpleCursorAdapter(
                it,
                R.layout.contact_list_item,
                null,
                FROM_COLUMNS, TO_IDS,
                0
            )
            val contactListView = it.findViewById<ListView>(R.layout.contacts_list_view)
            contactListView.adapter = cursorAdapter
            // TODO: Set click listener
        }
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
        // Put the result Cursor in the adapter for the ListView
        cursorAdapter?.swapCursor(cursor)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        // Delete the reference to the existing Cursor
        cursorAdapter?.swapCursor(null)
    }

    override fun onItemClick(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
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
            /*
             * You can use contactUri as the content URI for retrieving
             * the details for a contact.
             */
        }
    }
}
