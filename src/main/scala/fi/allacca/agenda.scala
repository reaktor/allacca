package fi.allacca

import android.app.Activity
import android.database.Cursor
import android.widget._
import scala.Array
import android.os.Bundle
import android.content.{Intent, CursorLoader, ContentUris, Loader}
import android.provider.CalendarContract
import android.util.Log
import android.view.ViewGroup.LayoutParams
import org.joda.time.{DateTime, LocalDate}
import scala.annotation.tailrec
import org.joda.time.format.DateTimeFormat
import android.graphics.Color
import android.view.View
import android.provider.CalendarContract.Instances
import android.app.LoaderManager.LoaderCallbacks
import scala.Some
import android.view.ViewTreeObserver.OnGlobalLayoutListener

class AgendaView(activity: Activity) extends ScrollView(activity) {
  private lazy val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)
  /**
   * How much off-screen content we want to maintain loaded to facilitate scrolling
   */
  private lazy val verticalViewPortPadding = activity.getResources.getDisplayMetrics.heightPixels
  private lazy val pastCreator = new PastAgendaCreator(activity, verticalViewPortPadding, daysListView)
//  private lazy val futureCreator = new FutureAgendaCreator(activity, verticalViewPortPadding, daysListView)
  private lazy val daysListView = new DaysListView(activity)
  private var focusDay: LocalDate = new LocalDate

  def start() {
    addView(daysListView)
    resetTo(new LocalDate)
  }
  
  def resetTo(newFocusDay: LocalDate) {
    focusDay = newFocusDay
    pastCreator.loadEnoughPastFrom(focusDay)

    getViewTreeObserver.addOnGlobalLayoutListener(new OnGlobalLayoutListener {
      def onGlobalLayout() {
        scrollTo(0, 100) // So, here we could do some scrolling... and maybe see the finished layout
      }
    })
  }
}

class DaysListView(activity: Activity) extends LinearLayout(activity)  {
  private val layoutParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  setLayoutParams(layoutParams)
  setOrientation(LinearLayout.VERTICAL)
  setId(View.generateViewId())
}

class PastAgendaCreator(activity: Activity, howMuchExtraPastToLoadInPixels: Int, daysListView: DaysListView) extends LoaderCallbacks[Cursor] {
  private lazy val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)
  private lazy val loader = EventsLoaderFactory.createLoader(activity)
  private var currentBeginning: LocalDate = new LocalDate
  private val daysToLoadAtTime = 30

  private val DAYVIEW_TAG_ID = R.id.dayViewTagId

  def loadEnoughPastFrom(focusDay: LocalDate) {
    currentBeginning = focusDay
    var counter = 0 // Counter is a kludge to stop loading, as the real stop condition daysListView.getTop < howMuchExtraPastToLoadInPixels never gets fulfilled
    while (daysListView.getTop < howMuchExtraPastToLoadInPixels && counter < 1) {
      val newBeginning = currentBeginning.minusDays(daysToLoadAtTime)
      loadBatch(newBeginning, currentBeginning)
      currentBeginning = newBeginning
      counter = counter + 1
    }
  }

  private def loadBatch(start: LocalDate, end: LocalDate) {
    Log.d(TAG, getClass.getSimpleName + " loading " + start + " -- " + end)
    val loadArguments = new Bundle
    loadArguments.putLong("start", start)
    loadArguments.putLong("end", end)
    activity.getLoaderManager.initLoader(0, loadArguments, this)
  }

  def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    Log.d(TAG, "onCreateLoader starting")
    val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon
    ContentUris.appendId(uriBuilder, args.get("start").asInstanceOf[Long])
    ContentUris.appendId(uriBuilder, args.get("end").asInstanceOf[Long])
    loader.setUri(uriBuilder.build)
    loader
  }

  def onLoadFinished(loader: Loader[Cursor], cursor: Cursor): Unit = {
    Log.d(TAG, "onLoadFinished starting")
    val events = readEvents(cursor)
    events.foreach { e => Log.d(TAG, getClass.getSimpleName + " loaded " + e.toString) }
    val eventsByDays = events.groupBy { e => new DateTime(e.startTime).withTimeAtStartOfDay.toLocalDate }
    val daysInOrder = eventsByDays.keys.toSeq.sortBy(_.toDateTimeAtCurrentTime.getMillis).reverse
    daysInOrder.foreach { day =>
      val dayView = new LinearLayout(activity)
      dayView.setOrientation(LinearLayout.VERTICAL)
      dayView.setId(View.generateViewId())
      val dayViewParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
      dayView.setLayoutParams(dayViewParams)

      val dayNameView = new TextView(activity)
      dayNameView.setId(View.generateViewId())
      val dayNameParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
      dayNameView.setLayoutParams(dayNameParams)
      dayNameView.setTextSize(dimensions.overviewContentTextSize)
      val dateFormat = DateTimeFormat.forPattern("d.M.yyyy")
      dayNameView.setText(dateFormat.print(day))
      dayView.addView(dayNameView)

      val eventsOfDay = events.filter { _.isDuring(day.toDateTimeAtStartOfDay) } sortBy { _.startTime }
      val dayWithEvents = DayWithEvents(day, eventsOfDay)
      dayView.setTag(DAYVIEW_TAG_ID, dayWithEvents)

      eventsOfDay foreach {
        event =>
          val titleView = new TextView(activity)
          titleView.setId(View.generateViewId())
          val params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
          titleView.setLayoutParams(params)
          titleView.setTextSize(dimensions.overviewContentTextSize)
          titleView.setText(event.title)
          titleView.setBackgroundColor(dimensions.pavlova)
          titleView.setTextColor(Color.BLACK)
          activity.runOnUiThread(dayView.addView(titleView))
          val onClick: (View => Unit) = { _ =>
              Log.i(TAG, "event clicked, starting activity")
              val intent = new Intent(activity, classOf[EditEventActivity])
              intent.putExtra(EVENT_ID, event.id.get)
              activity.startActivity(intent)
              Log.i(TAG, "After start activity")
          }
          titleView.setOnClickListener(onClick)
      }
      daysListView.addView(dayView, 0)
    }

    Log.d(TAG, "Adjusting scroll")
    daysListView.getParent.asInstanceOf[AgendaView].fullScroll(View.FOCUS_DOWN) // Doesn't go down here, layout not yet done
  }

  def onLoaderReset(loader: Loader[Cursor]) {}

  private def readEvents(cursor: Cursor): Seq[CalendarEvent] = {
    @tailrec
    def readEvents0(cursor: Cursor, events: Seq[CalendarEvent] = Nil): Seq[CalendarEvent] = {
      val newEvents = events :+ readEventFrom(cursor)
      if (!cursor.moveToNext()) {
        newEvents
      } else readEvents0(cursor, newEvents)
    }

    if (!cursor.moveToFirst()) {
      Nil
    } else {
      readEvents0(cursor)
    }
  }

  private def readEventFrom(cursor: Cursor): CalendarEvent = {
    new CalendarEvent(id = Some(cursor.getLong(0)), title = cursor.getString(1), startTime = cursor.getLong(2), endTime = cursor.getLong(3))
  }
}

object EventsLoaderFactory {
  private val columnsToSelect = Array(Instances.EVENT_ID, "title", "begin", "end")

  def createLoader(activity: Activity): CursorLoader = {
    val loader = new CursorLoader(activity)
    loader.setProjection(columnsToSelect)
    loader.setSelection("")
    loader.setSelectionArgs(null)
    loader.setSortOrder("begin asc")
    loader
  }
}
