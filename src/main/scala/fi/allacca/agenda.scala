package fi.allacca

import android.app.{ProgressDialog, Activity}
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
import android.view.{ViewGroup, View}
import android.provider.CalendarContract.Instances
import android.app.LoaderManager.LoaderCallbacks
import scala.Some
import scala.collection.mutable
import java.util.Date

class AgendaView(activity: Activity) extends ListView(activity) {
  private lazy val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)
  /**
   * Approximation of how many items (day + events) can fit on the screen.
   */
  private lazy val rowsVisibleAtTime: Int = (activity.getResources.getDisplayMetrics.heightPixels.toFloat / dimensions.overviewHeaderTextSize).toInt
  /**
   * How much off-screen content we want to maintain loaded to facilitate scrolling
   */
  private lazy val verticalViewPortPadding: Int = rowsVisibleAtTime

  lazy val pastModel = new AgendaModel(rowsVisibleAtTime, verticalViewPortPadding)

  private lazy val fullModel = new CombinedModel(pastModel)

  private lazy val pastCreator = new PastAgendaCreator(activity, verticalViewPortPadding, pastModel, adapter, this)
//  private lazy val futureCreator = new FutureAgendaCreator(activity, verticalViewPortPadding, ... )
  private var focusDay: LocalDate = new LocalDate

  private lazy val adapter = new AgendaAdapter(activity, fullModel)

  def start() {
    setAdapter(adapter)
    resetTo(new LocalDate)
  }
  
  def resetTo(newFocusDay: LocalDate) {
    focusDay = newFocusDay
    pastCreator.loadEnoughPastFrom(focusDay)
  }

  def setSelectionToIndexOf(date: LocalDate) {
    val indexOfDate = fullModel.indexOf(date)
    setSelection(indexOfDate)
  }

  def goto(date: LocalDate) {
    val indexOfDate = fullModel.indexOf(date)
    if (indexOfDate == -1) {
      resetTo(date)
    } else {
      setSelection(indexOfDate)
    }
  }
}

class AgendaAdapter(activity: Activity, fullModel: CombinedModel) extends BaseAdapter {
  private lazy val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)

    def getItemId(position: Int): Long = getItem(position).map {
      _.id
    }.getOrElse(-1)

    def getCount: Int = Integer.MAX_VALUE

    def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      if (convertView != null && false /* Seems like view reuse is difficult with variable sized model */) {
        convertView
      } else {
        render(getItem(position))
      }
    }

    def getItem(position: Int): Option[DayWithEvents] = {
      if (fullModel.size == 0) {
        None
      } else {
        Some(fullModel.getItem(position % fullModel.size))
      }
    }

    private def render(content: Option[DayWithEvents]): View = {
      val view: View = content match {
        case None =>
          val pendingView = new TextView(activity)
          pendingView.setText("Loading")
          pendingView
        case Some(dayWithEvents) => render(dayWithEvents)
      }
      view.setId(View.generateViewId())
      val dayViewParams = new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
      view.setLayoutParams(dayViewParams)
      view
    }

    private def render(dayWithEvents: DayWithEvents): View = {
      val dayView = new LinearLayout(activity)
      dayView.setOrientation(LinearLayout.VERTICAL)

      val dayNameView = new TextView(activity)
      dayNameView.setId(View.generateViewId())
      val dayNameParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
      dayNameView.setLayoutParams(dayNameParams)
      dayNameView.setTextSize(dimensions.overviewContentTextSize)
      val dateFormat = DateTimeFormat.forPattern("d.M.yyyy")
      val day = dayWithEvents.day
      dayNameView.setText(dateFormat.print(day))
      dayView.addView(dayNameView)

      val eventsOfDay = dayWithEvents.events

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
          val onClick: (View => Unit) = {
            _ =>
              Log.i(TAG, "event clicked, starting activity")
              val intent = new Intent(activity, classOf[EditEventActivity])
              intent.putExtra(EVENT_ID, event.id.get)
              activity.startActivityForResult(intent, REQUEST_CODE_EDIT_EVENT)
              Log.i(TAG, "After start activity")
          }
          titleView.setOnClickListener(onClick)
      }
      dayView
    }
}

class PastAgendaCreator(activity: Activity, howMuchExtraPastToLoadInPixels: Int, model: AgendaModel,
                        adapter: AgendaAdapter, view: AgendaView) extends LoaderCallbacks[Cursor] {
  private lazy val loader = EventsLoaderFactory.createLoader(activity)
  private var focusDay: LocalDate = new LocalDate
  private var currentBeginning: LocalDate = new LocalDate
  private val daysToLoadAtTime = 30
  private var newBeginning: LocalDate = currentBeginning.minusDays(daysToLoadAtTime)
  private lazy val progressDialog = new ProgressDialog(activity)
  progressDialog.setTitle("Loading")
  progressDialog.setMessage("events")
  progressDialog.setCancelable(false)
  progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)

  def loadEnoughPastFrom(focusDay: LocalDate) {
    this.focusDay = focusDay
    currentBeginning = focusDay.plusDays(daysToLoadAtTime)
    loadBatch(newBeginning, currentBeginning)
  }

  private def loadBatch(start: LocalDate, end: LocalDate) {
    progressDialog.setMessage(start.toString + " -- " + end.toString)
    progressDialog.show()
    Log.d(TAG, getClass.getSimpleName + " loading " + start + " -- " + end)
    val loadArguments = new Bundle
    loadArguments.putLong("start", start)
    loadArguments.putLong("end", end)
    activity.getLoaderManager.initLoader(0, loadArguments, this)
  }

  def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    Log.d(TAG, "onCreateLoader starting" + " and  loading " + new Date(args.get("start").asInstanceOf[Long]) + " -- " + new Date(args.get("end").asInstanceOf[Long]))
    val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon
    ContentUris.appendId(uriBuilder, args.get("start").asInstanceOf[Long])
    ContentUris.appendId(uriBuilder, args.get("end").asInstanceOf[Long])
    loader.setUri(uriBuilder.build)
    loader
  }

  def onLoadFinished(loader: Loader[Cursor], cursor: Cursor): Unit = {
    Log.d(TAG, "onLoadFinished starting")
    val events = readEvents(cursor)
    val eventsByDays = events.groupBy { e => new DateTime(e.startTime).withTimeAtStartOfDay.toLocalDate }
    val days = eventsByDays.keys
    days.foreach { day =>
      val eventsOfDay = events.filter { _.isDuring(day.toDateTimeAtStartOfDay) } sortBy { _.startTime }
      val dayWithEvents = DayWithEvents(day, eventsOfDay)
      Log.d(TAG, "adding " + dayWithEvents)
      model.add(dayWithEvents)
    }
    adapter.notifyDataSetChanged()
    activity.getLoaderManager.destroyLoader(0) // This makes onCreateLoader run again and use fresh search URI

    currentBeginning = newBeginning
    newBeginning = currentBeginning.minusDays(daysToLoadAtTime)
    if (!model.hasEnoughContentBefore(focusDay)) {
      Log.d(TAG, "Got to load more")
      progressDialog.setMessage(newBeginning.toString + " -- " + currentBeginning.toString)
      loadBatch(newBeginning, currentBeginning)
    } else {
      progressDialog.dismiss()
      view.setSelectionToIndexOf(focusDay)
    }
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

class AgendaModel(rowsVisibleAtTime: Int, howManyRowsToLoadInAdvance: Int) {
  val contents = mutable.SortedSet[DayWithEvents]()

  def add(dwe: DayWithEvents) { contents.add(dwe) }

  def hasEnoughContentBefore(day: LocalDate): Boolean = {
    val pastLength = contents.count { _.day.isBefore(day) }
    Log.d(TAG, getClass.getSimpleName + " pastLength == " + pastLength)
    pastLength >= howManyRowsToLoadInAdvance
  }
}

class CombinedModel(past: AgendaModel /*, future: AgendaModel */) {
  def getItem(index: Int): DayWithEvents = past.contents.toArray.apply(index)

  def size = past.contents.size

  def indexOf(date: LocalDate): Int = {
    val item = past.contents.find { _.day == date }
    item.map { past.contents.toIndexedSeq.indexOf(_) }.getOrElse(-1)
  }
}