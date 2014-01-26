package fi.allacca

import android.app.{LoaderManager, ListActivity}
import android.database.Cursor
import android.content.{ContentUris, CursorLoader, Loader}
import android.os.Bundle
import android.widget.{ListView, ProgressBar, SimpleCursorAdapter}
import android.view.{View, ViewGroup}
import android.provider.CalendarContract
import android.util.Log

class EventListActivity extends ListActivity with TypedViewHolder with LoaderManager.LoaderCallbacks[Cursor] {
  private val PROJECTION = Array[String] (
    "_id",
    "title",
    "dtstart",
    "dtend"
  )
  private lazy val adapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2, null,
    PROJECTION, Array(android.R.id.text1, android.R.id.text1, android.R.id.text1, android.R.id.text1), 0)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.eventlist)
    findView(TR.eventlisttitle).setText("Here are some events")

    val progressBar = new ProgressBar(this)
    progressBar.setIndeterminate(true)
    getListView.setEmptyView(progressBar)
    val root = findViewById(android.R.id.content).asInstanceOf[ViewGroup]
    root.addView(progressBar)

    setListAdapter(adapter)
    getLoaderManager.initLoader(0, null, this)
  }


  override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon
    ContentUris.appendId(builder, System.currentTimeMillis)
    ContentUris.appendId(builder, System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000)

    new CursorLoader(this, builder.build, PROJECTION, "", null, null)
  }

  override def onLoadFinished(loader: Loader[Cursor], data: Cursor): Unit = adapter.swapCursor(data)

  override def onLoaderReset(loader: Loader[Cursor]): Unit = adapter.swapCursor(null)

  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    Log.d(AllaccaSpike.TAG, getClass.getSimpleName + " got a click with position == " + position + " , id == " + id)
  }
}
