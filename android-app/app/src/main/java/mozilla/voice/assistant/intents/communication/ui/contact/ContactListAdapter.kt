package mozilla.voice.assistant.intents.communication.ui.contact

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.recyclerview_item.view.*
import mozilla.voice.assistant.R

class ContactListAdapter internal constructor(
    context: Context
) : RecyclerView.Adapter<ContactListAdapter.ContactViewHolder>() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var contacts = emptyList<ContactEntity>() // cached data

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val wordItemView: TextView = itemView.findViewById(R.id.textView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val itemView = inflater.inflate(R.layout.recyclerview_item, parent, false)
        return ContactViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val current = contacts[position]
        holder.wordItemView.text = current.nickname
    }

    internal fun setContacts(contacts: List<ContactEntity>) {
        this.contacts = contacts
        notifyDataSetChanged()
    }

    override fun getItemCount() = contacts.size
}