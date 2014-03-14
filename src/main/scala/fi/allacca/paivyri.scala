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
import org.joda.time.{Days, DateTime, LocalDate}
import scala.annotation.tailrec
import org.joda.time.format.DateTimeFormat
import android.graphics.Color
import android.view.{ViewGroup, View}
import android.provider.CalendarContract.Instances
import android.app.LoaderManager.LoaderCallbacks
import scala.Some
import scala.collection.mutable
import android.widget.AbsListView.OnScrollListener
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class PaivyriView(activity: Activity, statusTextView: TextView) extends ListView(activity) {
  /**
   * Approximation of how many items (day + events) can fit on the screen.
   */
  private lazy val rowsVisibleAtTime: Int = (activity.getResources.getDisplayMetrics.heightPixels.toFloat / dimensions.overviewHeaderTextSize).toInt
  /**
   * How much off-screen content we want to maintain loaded to facilitate scrolling
   */
  private lazy val verticalViewPortPadding: Int = rowsVisibleAtTime / 2
  val howManyDaysToLoadAtTime = 30

  private lazy val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)
  private val adapter = new PaivyriAdapter(activity, this, statusTextView)

  def start() {
    setAdapter(adapter)
    focusOn(new LocalDate)
    setOnScrollListener(new OnScrollListener {
      def onScrollStateChanged(view: AbsListView, scrollState: Int) {
        Log.d(TAG + PaivyriView.this.getClass.getSimpleName, s"scrollState==$scrollState")
      }

      def onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
        val lastVisibleItem = firstVisibleItem + visibleItemCount
        val dayOf: Int => Option[LocalDate] = adapter.getItem(_).map { _.day }
        if (firstVisibleItem == 0) {
          adapter.loadMorePast(dayOf(firstVisibleItem), dayOf(lastVisibleItem))
        }
        if (lastVisibleItem > (adapter.getCount - howManyDaysToLoadAtTime)) {
          adapter.loadMoreFuture(dayOf(firstVisibleItem), dayOf(lastVisibleItem))
        }
      }
    })
  }

  def focusOn(day: LocalDate) {
    adapter.focusOn(day)
  }
}

class PaivyriAdapter(activity: Activity, listView: PaivyriView, statusTextView: TextView) extends BaseAdapter with LoaderCallbacks[Cursor] {
  private val loadWindowLock = new Object
  private val DAYVIEW_TAG_ID = R.id.dayViewTagId
  private val renderer = new PaivyriRenderer(activity)
  private val model = new PaivyriModel

  private val howManyDaysToLoadAtTime = listView.howManyDaysToLoadAtTime

  private var loading = new AtomicBoolean(false)
  @volatile private var focusDay = new LocalDate
  @volatile private var firstDayToLoad = focusDay.minusDays(howManyDaysToLoadAtTime)
  @volatile private var lastDayToLoad = focusDay.plusDays(howManyDaysToLoadAtTime)
  @volatile private var setSelectionToFocusDayAfterLoading = false

  override def getCount: Int = model.size

  override def getItem(position: Int): Option[DayWithEvents] = model.getItemFromContents(position)

  override def getItemId(position: Int): Long = getItem(position).map { _.id }.getOrElse(-1)

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val item = getItem(position)
    if (convertView != null && item.isDefined && convertView.getTag(DAYVIEW_TAG_ID).asInstanceOf[Long] == item.get.id) {
      convertView
    } else {
      renderer.createLoadingOrRealViewFor(item)
    }
  }

  def focusOn(day: LocalDate) {
    resetLoadingWindowTo(day)
    setSelectionToFocusDayAfterLoading = true
    triggerLoading()
  }

  private def resetLoadingWindowTo(day: LocalDate) {
    loadWindowLock.synchronized {
      focusDay = day
      firstDayToLoad = focusDay.minusDays(howManyDaysToLoadAtTime)
      lastDayToLoad = focusDay.plusDays(howManyDaysToLoadAtTime)
    }
  }

  private def triggerLoading() {
    if (loading.getAndSet(true)) {
      Log.d(TAG, "Already load in progress")
      return
    }
    statusTextView.setText("Loading...")
    val args = new Bundle
    args.putLong("start", firstDayToLoad)
    args.putLong("end", lastDayToLoad)
    Log.d(TAG, "Initing loading with " + firstDayToLoad + "--" + lastDayToLoad)
    activity.getLoaderManager.initLoader(19, args, this)
  }

  def loadMorePast(firstVisibleDay: Option[LocalDate], lastVisibleDay: Option[LocalDate]) {
    Log.d(TAG, "Going to load more past...")
    if (loading.get()) {
      Log.d(TAG, "Already loading, not loading past then")
      return
    }
    if (focusDay == model.firstDay) {
      firstDayToLoad = firstDayToLoad.minusDays(howManyDaysToLoadAtTime)
      lastDayToLoad = lastVisibleDay.getOrElse(lastDayToLoad)
      setSelectionToFocusDayAfterLoading = true
      triggerLoading()
    } else {
      focusOn(model.firstDay)
    }
  }

  def loadMoreFuture(firstVisibleDay: Option[LocalDate], lastVisibleDay: Option[LocalDate]) {
    Log.d(TAG, s"Going to load more future... visible currently $firstVisibleDay -- $lastVisibleDay")
    if (loading.get()) {
      Log.d(TAG, "Already loading, not loading future then")
      return
    }
    if (lastVisibleDay.isDefined && Days.daysBetween(lastVisibleDay.get, model.lastDay).getDays < howManyDaysToLoadAtTime) {
      val currentWindowEnd = lastVisibleDay.map { d => if (d.isAfter(lastDayToLoad)) d else lastDayToLoad }.get
      Log.d(TAG, s"Going to load up to " + currentWindowEnd)
      firstDayToLoad = firstVisibleDay.getOrElse(firstDayToLoad)
      lastDayToLoad = currentWindowEnd.plusDays(howManyDaysToLoadAtTime)
      triggerLoading()
    }
  }

  override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon
    ContentUris.appendId(uriBuilder, args.get("start").asInstanceOf[Long])
    ContentUris.appendId(uriBuilder, args.get("end").asInstanceOf[Long])
    val loader = EventsLoaderFactory.createLoader(activity)
    loader.setUri(uriBuilder.build)
    loader
  }

  override def onLoadFinished(loader: Loader[Cursor], cursor: Cursor) {
    val f: Future[Unit] = Future {
      Log.d(TAG, "Starting the Finished call")

      val events = EventsLoaderFactory.readEvents(cursor)
      val eventsByDays = events.groupBy {
        e => new DateTime(e.startTime).withTimeAtStartOfDay.toLocalDate
      }
      val days = (eventsByDays.keys.toSet + focusDay).toList.sortBy { _.toDate }
      days.foreach { day =>
        val eventsOfDay = events.filter { _.isDuring(day.toDateTimeAtStartOfDay) } sortBy { _.startTime }
        val dayWithEvents = DayWithEvents(day, eventsOfDay)
        model.addOrUpdate(dayWithEvents)
      }
      activity.runOnUiThread { statusTextView.setText("") }
    }

    f onComplete {
      case Success(_) =>
        activity.runOnUiThread(new Runnable() {
          def run() {
            notifyDataSetChanged()
            loadWindowLock.synchronized {
              if (setSelectionToFocusDayAfterLoading) {
                listView.setSelection(model.indexOf(focusDay))
                setSelectionToFocusDayAfterLoading = false
              }
            }
            activity.getLoaderManager.destroyLoader(19) // This makes onCreateLoader run again and use fresh search URI
            loading.set(false)
            Log.d(TAG, "Finished loading!")
          }
        })
      case Failure(t) =>
        loading.set(false)
        throw t
    }
  }

  override def onLoaderReset(loader: Loader[Cursor]) {}
}

class PaivyriRenderer(activity: Activity) {
  private val DAYVIEW_TAG_ID = R.id.dayViewTagId
  private val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)

  def createLoadingOrRealViewFor(content: Option[DayWithEvents]): View = {
    val view: View = content match {
      case None =>
        val pendingView = new TextView(activity)
        pendingView.setText("Loading")
        pendingView
      case Some(dayWithEvents) => createDayView(dayWithEvents)
    }
    view.setId(View.generateViewId())
    val dayViewParams = new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    view.setLayoutParams(dayViewParams)
    view
  }

  private def createDayView(dayWithEvents: DayWithEvents): View = {
    Log.d(TAG, dayWithEvents.toString)
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
    dayView.setTag(DAYVIEW_TAG_ID, dayWithEvents.id)
    dayView
  }
}

class PaivyriModel {
  @volatile
  private var contents = mutable.ListBuffer[DayWithEvents]()

  def size = synchronized { contents.size }

  def firstDay: LocalDate = synchronized { contents.headOption.map { _.day }.getOrElse(new LocalDate) }
  def lastDay: LocalDate = synchronized { contents.lastOption.map { _.day }.getOrElse(new LocalDate) }

  def getItemFromContents(index: Int): Option[DayWithEvents] = synchronized { contents.lift(index) }

  def indexOf(day: LocalDate) = synchronized {
    findFromContents { _.day == day } match {
      case Some(dayWithEvents) => contents.indexOf(dayWithEvents)
      case None => -1
    }
  }

  def addOrUpdate(dwe: DayWithEvents) {
    findFromContents { _.day == dwe.day } match {
      case None => add(dwe)
      case Some(oldDwe) =>
        if (oldDwe.events != dwe.events) {
          removeFromContents(oldDwe)
          add(dwe)
        }
    }
  }

  private def add(dwe: DayWithEvents) {
    synchronized {
      contents.append(dwe)
      contents = contents.sortBy { dwe => dwe.day.toDate }
    }
  }

  def removeFromContents(oldDwe: DayWithEvents) { synchronized { contents -= oldDwe } }

  def findFromContents(p: DayWithEvents => Boolean): Option[DayWithEvents] = synchronized { contents.find(p) }
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

  def readEvents(cursor: Cursor): Seq[CalendarEvent] = {
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
