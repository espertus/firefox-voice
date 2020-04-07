package mozilla.voice.assistant.intents.communication.ui.contact

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.voice.assistant.R
import mozilla.voice.assistant.intents.communication.ContactActivity
import java.lang.AssertionError

class ContactFragment : Fragment() {
    companion object {
        fun newInstance() = ContactFragment()
    }

    private lateinit var viewModel: ContactViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.contact_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        activity?.let { activity ->
            viewModel = ViewModelProvider(
                activity,
                ContactViewModelFactory(
                    activity.application,
                    activity.intent.getStringExtra(ContactActivity.MODE_KEY),
                    activity.intent.getStringExtra(ContactActivity.NICKNAME_KEY)
                )
            ).get(ContactViewModel::class.java)

            // Set up RecyclerView.
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recyclerview)
            val adapter = ContactListAdapter(activity)
            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(activity)

            // Link them together.
            viewModel.allUsers.observe(activity, Observer { contacts ->
                contacts?.let { adapter.setContacts(it) }
            })
        } ?: throw AssertionError("Unable to get parent activity from fragment")
    }
}

class ContactViewModelFactory(val application: Application, val mode: String, val nickname: String) : ViewModelProvider.Factory {
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

    init {
        val contactsDao = ContactDatabase.getDatabase(application, viewModelScope).contactDao()
        repository = ContactRepository(contactsDao)
        allUsers = repository.allContacts
    }

    fun insert(contact: ContactEntity) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(contact)
    }
}

@Entity(tableName = "contact_table")
class ContactEntity(
    @PrimaryKey @ColumnInfo(name = "nickname") val nickname: String,
    // long name because it is a foreign key, not a key to this table
    @ColumnInfo(name = "contactId") val contactId: Long,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "phone") val phone: String
)

@Dao
interface ContactDao {
    @Query("SELECT * FROM contact_table WHERE nickname = :nickname")
    fun findByNickname(nickname: String): LiveData<ContactEntity>

    @Query("SELECT * FROM contact_table ORDER BY nickname")
    fun findAll(): LiveData<List<ContactEntity>>

    @Query("DELETE FROM contact_table")
    fun deleteAll()

    @Insert
    fun insert(contact: ContactEntity)
}

@Database(entities = arrayOf(ContactEntity::class), version = 1)
abstract class ContactDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao

    private class ContactDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.contactDao())
                }
            }
        }

        suspend fun populateDatabase(contactDao: ContactDao) {
            contactDao.deleteAll()

            // Add sample entries, just while testing.
            contactDao.insert(
                ContactEntity(
                "Mom",
                31L,
                "mom@gmail.com",
                "512-555-1212"
            ))
        }
    }

    // https://codelabs.developers.google.com/codelabs/android-room-with-a-view-kotlin/#6
    companion object {
        @Volatile
        private var INSTANCE: ContactDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): ContactDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContactDatabase::class.java,
                    "contact_database"
                )
                    // Wipes and rebuilds instead of migrating if no Migration object.
                    // Migration is not part of this codelab.
                    .fallbackToDestructiveMigration()
                    .addCallback(ContactDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}

class ContactRepository(private val contactDao: ContactDao) {
    val allContacts: LiveData<List<ContactEntity>> = contactDao.findAll()

    suspend fun insert(contactEntity: ContactEntity) {
        contactDao.insert(contactEntity)
    }
}