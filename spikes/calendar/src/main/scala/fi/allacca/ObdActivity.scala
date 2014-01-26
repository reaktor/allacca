package fi.allacca

import android.app.{LoaderManager, ListActivity}
import android.database.Cursor
import android.content.{ContentUris, CursorLoader, Loader}
import android.os.Bundle
import android.widget._
import android.view.{View, ViewGroup}
import android.provider.CalendarContract
import android.util.Log
import android.widget.AbsListView.OnScrollListener

class ObdActivity extends ListActivity with TypedViewHolder with LoaderManager.LoaderCallbacks[Cursor] with OnScrollListener {
  private val PROJECTION = Array[String] (
    "_id",
    "title",
    "dtstart",
    "dtend"
  )
  private lazy val adapter = new SimpleCursorAdapter(this, R.layout.single_event, null,
    PROJECTION, Array(R.id.title, R.id.dtstart, R.id.dtend), 0)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.obd)

    setListAdapter(adapter)
    getLoaderManager.initLoader(0, null, this)
  }


  override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon
    ContentUris.appendId(builder, System.currentTimeMillis)
    ContentUris.appendId(builder, System.currentTimeMillis() + 8 * 24 * 60 * 60 * 1000)

    new CursorLoader(this, builder.build, PROJECTION, "", null, null)
  }

  override def onLoadFinished(loader: Loader[Cursor], data: Cursor): Unit = adapter.swapCursor(data)

  override def onLoaderReset(loader: Loader[Cursor]): Unit = adapter.swapCursor(null)

  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    Log.d(AllaccaSpike.TAG, getClass.getSimpleName + " got a click with position == " + position + " , id == " + id)
    findViewById(R.id.obd_selected).asInstanceOf[TextView].setText(id.toString)
  }


  override def onScrollStateChanged(absListView: AbsListView, i: Int): Unit = Log.d(AllaccaSpike.TAG, "Scrolling state canged " + i)

  override def onScroll(absListView: AbsListView, i: Int, i2: Int, i3: Int): Unit = Log.d(AllaccaSpike.TAG, s"Scrolling $i $i2 $i3")
}
