package mozilla.voice.assistant.intents.communication

import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts.CONTENT_LOOKUP_URI
import android.util.Log
import mozilla.voice.assistant.intents.communication.ui.contact.ContactEntity
import java.net.URI

internal data class ContactNumber(
    val number: String,
    val type: Int,
    val isPrimary: Boolean,
    val isSuperPrimary: Boolean
) {
    internal fun getScore(mode: String) =
        listOf(
            Pair(isPrimary, PRIMARY_BONUS),
            Pair(isSuperPrimary, SUPER_PRIMARY_BONUS),
            Pair(
                mode == ContactActivity.SMS_MODE &&
                        (type == Phone.TYPE_MOBILE || type == Phone.TYPE_MMS),
                MOBILE_BONUS_FOR_SMS
            )
        ).sumBy {
            if (it.first) it.second else 0
        }

    companion object {
        private const val MOBILE_BONUS_FOR_SMS = 10
        private const val PRIMARY_BONUS = 1
        private const val SUPER_PRIMARY_BONUS = 2
        private const val TAG = "NoContactFragment"

        internal fun getBestNumber(contactActivity: ContactActivity, lookupKey: String, contactId: Long): String? =
            // https://learning.oreilly.com/library/view/android-cookbook-2nd/9781449374471/ch10.html
            contactActivity.contentResolver?.let { resolver ->
                val mode = contactActivity.viewModel.mode
                //val uri = CONTENT_LOOKUP_URI.buildUpon().appendPath(lookupKey).build()
                // val uri = Phone.CONTENT_URI.buildUpon().appendQueryParameter(Phone.CONTACT_ID, contactId.toString()).build()
                val bestNumber = resolver.query(
                    Phone.CONTENT_URI,
                    null,
                    "${Phone.CONTACT_ID}=?",
                    arrayOf(contactId.toString()),
                    null
                )?.use { numbers ->
                    // https://stackoverflow.com/a/39067259/631051
                    (1..numbers.count).map {
                        numbers.moveToNext()
                        ContactNumber(
                            numbers.getString(numbers.getColumnIndex(Phone.NORMALIZED_NUMBER)),
                            numbers.getInt(numbers.getColumnIndex(ContactsContract.Data.DATA2)),
                            numbers.getInt(numbers.getColumnIndex(Phone.IS_PRIMARY)) > 0,
                            numbers.getInt(numbers.getColumnIndex(Phone.IS_SUPER_PRIMARY)) > 0
                        )
                    }
                }?.maxBy { it.getScore(mode) }?.number
                return bestNumber
            }

    }
}
