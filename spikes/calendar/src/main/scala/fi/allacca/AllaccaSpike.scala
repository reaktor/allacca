package fi.allacca

import android.app.{LoaderManager, ListActivity}
import android.os.Bundle

import android.provider.CalendarContract.Calendars
import android.database.Cursor
import android.content.{CursorLoader, Loader}
import android.provider.CalendarContract
import android.util.Log
import android.widget.{ListView, SimpleCursorAdapter, ProgressBar}
import android.view.{View, ViewGroup}

class AllaccaSpike extends ListActivity with TypedViewHolder with LoaderManager.LoaderCallbacks[Cursor] {
  private val TAG = getClass.getSimpleName
  private val PROJECTION = Array[String] (
    "_id",                // 0
    Calendars.NAME        // 1
  )
  private lazy val adapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2, null,
    PROJECTION, Array(android.R.id.text1, android.R.id.text1), 0)

  override def onCreate(savedInstanceState: Bundle): Unit = {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        findView(TR.textview).setText("Looks like you've got access to these calendars")

     val progressBar = new ProgressBar(this)
      progressBar.setIndeterminate(true)
      getListView.setEmptyView(progressBar)
      val root = findViewById(android.R.id.content).asInstanceOf[ViewGroup]
      root.addView(progressBar)

      setListAdapter(adapter)
      getLoaderManager.initLoader(0, null, this)
    }


  override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    // Now create and return a CursorLoader that will take care of
    // creating a Cursor for the data being displayed.
    new CursorLoader(this, CalendarContract.Calendars.CONTENT_URI, PROJECTION, "", null, null)
  }

  // Called when a previously created loader has finished loading
  override def onLoadFinished(loader: Loader[Cursor], data: Cursor): Unit = {
    // Swap the new cursor in.  (The framework will take care of closing the
    // old cursor once we return.)
    adapter.swapCursor(data)
  }

  // Called when a previously created loader is reset, making the data unavailable
  override def onLoaderReset(loader: Loader[Cursor]): Unit = {
    // This is called when the last Cursor provided to onLoadFinished()
    // above is about to be closed.  We need to make sure we are no
    // longer using it.
    adapter.swapCursor(null)
  }

  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    Log.d(TAG, "Got a click with position == " + position + " , id == " + id)
  }
}
