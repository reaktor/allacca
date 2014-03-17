package fi.allacca

import android.app.Activity
import android.database.Cursor
import android.widget._
import scala.{volatile, Array, Some}
import android.os.Bundle
import android.content.{Intent, CursorLoader, ContentUris, Loader}
import android.provider.CalendarContract
import android.util.Log
import android.view.ViewGroup.LayoutParams
import org.joda.time.{Days, DateTime, LocalDate}
import org.joda.time.format.DateTimeFormat
import android.graphics.{Typeface, Color}
import android.view.{ViewGroup, View}
import android.provider.CalendarContract.Instances
import android.app.LoaderManager.LoaderCallbacks
import android.widget.AbsListView.OnScrollListener
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.collection.mutable

class AgendaView(activity: Activity, statusTextView: TextView) extends ListView(activity) {
  val howManyDaysToLoadAtTime = 120

  private val adapter = new AgendaAdapter(activity, this, statusTextView)

  def start() {
    setAdapter(adapter)
    focusOn(new LocalDate)
    setOnScrollListener(new OnScrollListener {
      def onScrollStateChanged(view: AbsListView, scrollState: Int) {
        Log.d(TAG + AgendaView.this.getClass.getSimpleName, s"scrollState==$scrollState")
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

class AgendaAdapter(activity: Activity, listView: AgendaView, statusTextView: TextView) extends BaseAdapter with LoaderCallbacks[Cursor] {
  private val loadWindowLock = new Object
  private val DAYVIEW_TAG_ID = R.id.dayViewTagId
  private val renderer = new AgendaRenderer(activity)
  private val model = new AgendaModel

  private val howManyDaysToLoadAtTime = listView.howManyDaysToLoadAtTime

  private val loading = new AtomicBoolean(false)
  private val tooMuchPast = new AtomicBoolean(false)
  private val tooMuchFuture = new AtomicBoolean(false) // TODO: Handle going too much to future
  @volatile private var focusDay = new LocalDate
  @volatile private var firstDayToLoad = focusDay.minusDays(howManyDaysToLoadAtTime)
  @volatile private var lastDayToLoad = focusDay.plusDays(howManyDaysToLoadAtTime)
  @volatile private var setSelectionToFocusDayAfterLoading = false

  override def getCount: Int = model.size

  override def getItem(position: Int): Option[DayWithEvents] = model.getItemFromContents(position)

  override def getItemId(position: Int): Long = getItem(position).map { _.id }.getOrElse(-1)

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    if (position == 0 && tooMuchPast.get()) {
      val loadMoreHandler: View => Unit = { _ =>
        tooMuchPast.set(false)
        focusOn(firstDayToLoad)
      }
      renderer.createPastLoadingStopper(firstDayToLoad, { loadMoreHandler })
    } else {
      val item = getItem(position)
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
      firstDayToLoad = if (tooMuchPast.get()) firstDayToLoad else firstDayToLoad.minusDays(howManyDaysToLoadAtTime)
      lastDayToLoad = lastVisibleDay.getOrElse(lastDayToLoad)
      setSelectionToFocusDayAfterLoading = true
      val currentLoadWindowDays = Days.daysBetween(firstDayToLoad, lastDayToLoad).getDays
      if (currentLoadWindowDays > 3 * 360) {
        Log.d(TAG, "currentLoadWindowSize == " + currentLoadWindowDays)
        tooMuchPast.set(true)
        notifyDataSetChanged()
      } else {
        triggerLoading()
      }
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

      val events = time( {EventsLoaderFactory.readEvents(cursor)}, "readEvents")

      val eventsByDays: mutable.Map[LocalDate, Seq[CalendarEvent]] = new mutable.HashMap[LocalDate, Seq[CalendarEvent]]()

      time({
        events.foreach { e =>
          val day = new DateTime(e.startTime).withTimeAtStartOfDay.toLocalDate
          val daysEventsOption: Option[Seq[CalendarEvent]] = eventsByDays.get(day)
          daysEventsOption match {
            case Some(eventList) => eventsByDays.put(day, eventList.+:(e))
            case None => eventsByDays.put(day, List(e))
          }
        }
      }, "groupBy")

      val days = time( { eventsByDays.keys.toSet + focusDay }, "getDays")

      val daysWithEvents: Set[DayWithEvents] = time({
        days.map { day =>
            val eventsOfDay = eventsByDays.get(day).getOrElse(Nil).sortBy { _.startTime }
            DayWithEvents(day, eventsOfDay)
        }
      }, "create daysWithEventsMap")
      time({
        model.addOrUpdate(daysWithEvents, days)
      }, "Update model")
      time({activity.runOnUiThread { statusTextView.setText("") }}, "setViewText")
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

class AgendaRenderer(activity: Activity) {
  private val DAYVIEW_TAG_ID = R.id.dayViewTagId
  private val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)
  private val dateFormat = DateTimeFormat.forPattern("d.M.yyyy")

  def createLoadingOrRealViewFor(content: Option[DayWithEvents]): View = {
    val view: View = content match {
      case None =>
        val pendingView = new TextView(activity)
        pendingView.setText("Loading")
        pendingView
      case Some(dayWithEvents) => time( { createDayView(dayWithEvents) }, "createDayView")
    }
    view.setId(View.generateViewId())
    val dayViewParams = new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    view.setLayoutParams(dayViewParams)
    view
  }

  private def createDayView(dayWithEvents: DayWithEvents): View = {
    val dayView = new LinearLayout(activity)
    dayView.setOrientation(LinearLayout.VERTICAL)

    val dayNameView = new TextView(activity)
    dayNameView.setId(View.generateViewId())
    val dayNameParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    dayNameView.setLayoutParams(dayNameParams)
    dayNameView.setTextSize(dimensions.overviewContentTextSize)
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

  def createPastLoadingStopper(day: LocalDate, loadingHandler: View => Unit ): View = {
    val view = new TextView(activity)
    view.setId(View.generateViewId())
    val params = new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    view.setLayoutParams(params)
    view.setTextSize(dimensions.overviewContentTextSize)
    view.setTypeface(null, Typeface.BOLD)
    view.setText("Click to load events before " + dateFormat.print(day))
    view.setOnClickListener(loadingHandler)
    view
  }
}

class AgendaModel {
  @volatile
  private var contents: Map[Long, DayWithEvents] = Map()
  @volatile
  private var sortedIds = List[Long]()

  def size = synchronized { sortedIds.size }

  def firstDay: LocalDate = synchronized {
    if (size < 1) {
      new LocalDate
    } else {
      val firstItemsKey = sortedIds.head
      contents(firstItemsKey).day
    }
  }

  def lastDay: LocalDate = synchronized {
    if (size < 1) {
      new LocalDate
    } else {
      val lastItemsKey = sortedIds.last
      contents(lastItemsKey).day
    }
  }

  def getItemFromContents(index: Int): Option[DayWithEvents] = synchronized {
    if (sortedIds.isEmpty) {
      None
    } else {
      if (sortedIds.size < index + 1) {
        None
      } else {
        val keyOfIndex = sortedIds(index)
        contents.lift(keyOfIndex)
      }
    }
  }

  def indexOf(day: LocalDate) = synchronized {
    sortedIds.indexOf(day.toDate.getTime)
  }

  /**
   * @param newDaysAndEventsFromLoader data to add
   * @param days days in data to add, passed in redundantly for performance reasons
   */
  def addOrUpdate(newDaysAndEventsFromLoader: Set[DayWithEvents], days: Set[LocalDate]) {
    synchronized {
      val oldItemsToRetain = contents.values.filter { dwe => !days.contains(dwe.day) }
      val itemsInTotal: Iterable[DayWithEvents] = oldItemsToRetain ++ newDaysAndEventsFromLoader
      val newIdsArray = new Array[Long](itemsInTotal.size)
      var i = 0
      itemsInTotal.foreach { dwe =>
        newIdsArray.update(i, dwe.id)
        i = i + 1
      }
      sortedIds = listSortedDistinctValues(newIdsArray)
      contents = itemsInTotal.map { dwe => (dwe.id, dwe) }.toMap
    }
  }

  /**
   * Fast unique sort from http://stackoverflow.com/a/8162643
   */
  def listSortedDistinctValues(someArray: Array[Long]): List[Long] = {
    if (someArray.length == 0) List[Long]()
    else {
      java.util.Arrays.sort(someArray)
      var last = someArray(someArray.length - 1)
      var list = last :: Nil
      var i = someArray.length - 2
      while (i >= 0) {
        if (someArray(i) < last) {
          last = someArray(i)
          if (last <= 0) return list
          list = last :: list
        }
        i -= 1
      }
      list
    }
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

  def readEvents(cursor: Cursor): Seq[CalendarEvent] = {
    val result = new Array[CalendarEvent](cursor.getCount)
    var i = 0
    while (cursor.moveToNext()) {
      result.update(i, readEventFrom(cursor))
      i = i + 1
    }
    result.toSeq
  }

  private def readEventFrom(cursor: Cursor): CalendarEvent = {
    new CalendarEvent(id = Some(cursor.getLong(0)), title = cursor.getString(1), startTime = cursor.getLong(2), endTime = cursor.getLong(3))
  }
}
