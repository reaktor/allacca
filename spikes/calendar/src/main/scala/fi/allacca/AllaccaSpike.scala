package fi.allacca

import android.app.{LoaderManager, ListActivity}
import android.os.Bundle

import android.provider.CalendarContract.Calendars
import android.database.Cursor
import android.content.{Intent, CursorLoader, Loader}
import android.provider.CalendarContract
import android.util.Log
import android.widget.{AbsListView, ListView, SimpleCursorAdapter, ProgressBar}
import android.view.{View, ViewGroup}
import android.widget.AbsListView.OnScrollListener
import android.view.ViewTreeObserver.OnScrollChangedListener
import java.util.{Calendar, TimeZone, GregorianCalendar}

object AllaccaSpike {
  val TAG = "AllaccaSpike"
}

class AllaccaSpike extends ListActivity with TypedViewHolder with LoaderManager.LoaderCallbacks[Cursor] with OnScrollListener with OnScrollChangedListener {
  private val PROJECTION = Array[String] (
    "_id",                // 0
    Calendars.NAME        // 1
  )
  private lazy val adapter = new SimpleCursorAdapter(this, R.layout.single_calendar, null,
    PROJECTION, Array(R.id.calendar_id, R.id.calendar_name), 0)


  def openObd(view: View): Unit = {
    Log.d(AllaccaSpike.TAG, "OpenObd has been clicked")
    startActivity(new Intent(this, classOf[ObdActivity]))
  }

  def addFixedEvent(view: View): Unit = {
    Log.d(AllaccaSpike.TAG, "addFixedEvent has been clicked")
    val service = new CalendarEventService(this)

    val cal = new GregorianCalendar(2014, 1, 3)
    cal.setTimeZone(TimeZone.getTimeZone("UTC"))
    cal.set(Calendar.HOUR, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start: Long = cal.getTimeInMillis()
    val end = start + (1000 * 60 * 60)
    service.createEvent(1, new CalendarEvent("Joku tapaaminen", start, end, "Jotain hämärähommia"))
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        findView(TR.textview).setText("Looks like you've got access to these calendars")

     val progressBar = new ProgressBar(this)
      progressBar.setIndeterminate(true)
      getListView.setEmptyView(progressBar)
      getListView.setOnScrollListener(this)
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
    Log.d(AllaccaSpike.TAG, "Got a click with position == " + position + " , id == " + id)
    val intent = new Intent(this, classOf[EventListActivity])
    startActivity(intent)
  }

  def onScrollChanged(): Unit = Log.d(AllaccaSpike.TAG, getClass.getSimpleName + " onScrollChanged")

  def onScrollStateChanged(p1: AbsListView, p2: Int): Unit = Log.d(AllaccaSpike.TAG, getClass.getSimpleName + s" onScrollStateChanged $p2 $p1")

  def onScroll(p1: AbsListView, p2: Int, p3: Int, p4: Int): Unit = Log.d(AllaccaSpike.TAG, getClass.getSimpleName + s" onScroll $p2 $p3 $p4 $p1")
}
