package fi.allacca

import android.app._
import android.database.Cursor
import android.content.{Context, ContentUris, CursorLoader, Loader}
import android.os.Bundle
import android.widget._
import android.view.View
import android.provider.CalendarContract
import android.util.{AttributeSet, Log}
import android.widget.AbsListView.OnScrollListener

class ObdActivity extends Activity with TypedViewHolder with ScrollViewListener {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.obd)
    val listView = findViewById(R.id.infinite_events_list)
    listView.requestFocus()

    val scroll = findViewById(R.id.infinite_events_scrollview).asInstanceOf[EventsScrollView]
    scroll.setScrollViewListener(this)
  }

  override def onScrollChanged(scrollView: EventsScrollView, x: Int, y: Int, oldX: Int, oldY: Int): Unit = {
    Log.d(AllaccaSpike.TAG, getClass.getSimpleName + s" got onScrollChanged with $x $y $oldX $oldY")
  }
}

class InfiniteEventsListFragment extends ListFragment with LoaderManager.LoaderCallbacks[Cursor] {
  private val PROJECTION = Array[String] (
    "_id",
    "title",
    "dtstart",
    "dtend"
  )
  private lazy val activity = getActivity
  private lazy val adapter = new SimpleCursorAdapter(activity, R.layout.single_event, null,
    PROJECTION, Array(R.id.title, R.id.dtstart, R.id.dtend), 0)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    setListAdapter(adapter)
    getLoaderManager.initLoader(0, null, this)
  }

  override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon
    ContentUris.appendId(builder, System.currentTimeMillis)
    ContentUris.appendId(builder, System.currentTimeMillis() + 8 * 24 * 60 * 60 * 1000)

    new CursorLoader(getActivity, builder.build, PROJECTION, "", null, null)
  }

  override def onLoadFinished(loader: Loader[Cursor], data: Cursor): Unit = adapter.swapCursor(data)

  override def onLoaderReset(loader: Loader[Cursor]): Unit = adapter.swapCursor(null)

  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    Log.d(AllaccaSpike.TAG, getClass.getSimpleName + " got a click with position == " + position + " , id == " + id)
    getActivity.findViewById(R.id.obd_selected).asInstanceOf[TextView].setText(id.toString)
  }
}

trait ScrollViewListener {
  def onScrollChanged(scrollView: EventsScrollView, x: Int, y: Int, oldX: Int, oldY: Int)
}

class EventsScrollView(context: Context, attrs: AttributeSet) extends ScrollView(context, attrs) {
  private var scrollViewListener: ScrollViewListener = null

  def setScrollViewListener(listener: ScrollViewListener): Unit = {
    scrollViewListener = listener
  }

  override protected def onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int): Unit = {
    super.onScrollChanged(l, t, oldl, oldt)
    if (scrollViewListener != null) {
      scrollViewListener.onScrollChanged(this, l, t, oldl, oldt)
      Log.d(AllaccaSpike.TAG, getClass.getSimpleName + s" scrolling with $l $t $oldl $oldt")
    }
    Log.d(AllaccaSpike.TAG, getClass.getSimpleName + s" Bad! ScrollViewListener == null")
  }
}
