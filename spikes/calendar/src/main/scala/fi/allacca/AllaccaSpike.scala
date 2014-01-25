package fi.allacca

import android.app.Activity
import android.os.Bundle

import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.SyncColumns
import android.database.Cursor
import android.content.ContentResolver
import android.provider.CalendarContract
import android.net.Uri
import android.provider.ContactsContract.SyncState
import android.provider.SyncStateContract.Columns
import android.util.Log

object MyConstants extends Columns

class AllaccaSpike extends Activity with TypedViewHolder {
  private val TAG = getClass.getSimpleName

    override def onCreate(savedInstanceState: Bundle): Unit = {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        findView(TR.textview).setText("Terve android-sdksta!")

        // Projection array. Creating indices for this array instead of doing
        // dynamic lookups improves performance.
/*        val EVENT_PROJECTION = Array[String] (
          Calendars._ID,                           // 0
          Calendars.ACCOUNT_NAME,                  // 1
          Calendars.CALENDAR_DISPLAY_NAME,         // 2
          Calendars.OWNER_ACCOUNT                  // 3
        )
        */
      val EVENT_PROJECTION = Array[String] (
        Calendars.NAME                           // 0
      )

        val PROJECTION_ID_INDEX = 0
        val PROJECTION_ACCOUNT_NAME_INDEX = 1
        val PROJECTION_DISPLAY_NAME_INDEX = 2
        val PROJECTION_OWNER_ACCOUNT_INDEX = 3

      val cr: ContentResolver = getContentResolver
      val uri: Uri = CalendarContract.Calendars.CONTENT_URI
      val selectionArgs: Array[String] = Array("timo.rantalaiho@gmail.com")

      val selection = "((" + Calendars.NAME+ " = ?))"
/*      val selection = "((" + Calendars.ACCOUNT_NAME + " = ?) AND ("
      + Calendars.ACCOUNT_TYPE + " = ?) AND ("
      + Calendars.OWNER_ACCOUNT + " = ?))"*/


      //val selectionArgs: Array[String] = Array[String]("sampleuser@gmail.com", "com.google", "sampleuser@gmail.com")

      // Submit the query and get a Cursor object back.
      Log.d(TAG, "Starting call...")
      val cur = cr.query(uri, EVENT_PROJECTION, selection, selectionArgs, null)

      while (cur.moveToNext()) {
        val calID = cur.getLong(PROJECTION_ID_INDEX)
        val displayName = cur.getString(PROJECTION_DISPLAY_NAME_INDEX)
        val accountName = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX)
        val ownerName = cur.getString(PROJECTION_OWNER_ACCOUNT_INDEX)

        // Do something with the values...

        Log.d(TAG, "\tProcessing:")
        Log.d(TAG, "\t" + displayName)

        findView(TR.calendarname).setText(displayName)
      }
      Log.d(TAG, "...cursor loop ended.")
    }
}
