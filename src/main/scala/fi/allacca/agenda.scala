package fi.allacca

import android.app.Activity
import android.database.Cursor
import android.widget._
import android.os.Bundle
import android.content._
import android.provider.CalendarContract
import android.view.ViewGroup.LayoutParams
import org.joda.time.{Days, DateTime, LocalDate}
import org.joda.time.format.DateTimeFormat
import android.graphics.{Typeface, Color}
import android.view.{ViewGroup, View}
import android.app.LoaderManager.LoaderCallbacks
import android.widget.AbsListView.OnScrollListener
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import fi.allacca.Logger._
import java.util.Locale
import scala.util.{Failure, Success}

class AgendaView(activity: Activity, statusTextView: TextView) extends ListView(activity) {
  private val adapter = new AgendaAdapter(activity, this, statusTextView)
  private lazy val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)
  private val idGenerator = new IdGenerator
  lazy val headerView = new LoadingStopper(activity, dimensions, idGenerator)
  lazy val footerView = new LoadingStopper(activity, dimensions, idGenerator)

  def start(initialFocusDate: LocalDate) {
    addHeaderView(headerView)
    addFooterView(footerView)
    setAdapter(adapter)
    setOnScrollListener(new OnScrollListener {
      def onScrollStateChanged(view: AbsListView, scrollState: Int) {
        Logger.debug(s"${AgendaView.this.getClass.getSimpleName} scrollState==$scrollState")
      }

      def onScroll(view: AbsListView, firstVisibleItemIndex: Int, visibleItemCount: Int, totalItemCount: Int) {
        val numberOfExtraViewsSuchAsHeaderAndFooter = 2
        val lastVisibleItemIndex = firstVisibleItemIndex + visibleItemCount - numberOfExtraViewsSuchAsHeaderAndFooter - 1
        val dayOf: Int => Option[LocalDate] = adapter.getItem(_).map { _.day }
        val topOfLoadedContentIsDisplayed = firstVisibleItemIndex == 0
        if (topOfLoadedContentIsDisplayed && !adapter.tooMuchPast.get()) {
          adapter.loadMorePast(dayOf(firstVisibleItemIndex), dayOf(lastVisibleItemIndex))
        }
        val bottomOfLoadedContentIsDisplayed = lastVisibleItemIndex > (adapter.getCount - EventLoaderConfig.howManyDaysToLoadAtTime)
        if (bottomOfLoadedContentIsDisplayed && !adapter.tooMuchFuture.get()) {
          adapter.loadMoreFuture(dayOf(firstVisibleItemIndex), dayOf(lastVisibleItemIndex))
        }
      }
    })
    focusOn(initialFocusDate)
  }

  def focusOn(day: LocalDate) {
    adapter.focusOn(day)
  }

  def focusDay: LocalDate = adapter.synchronized { adapter.getFocusDay }
}

/**
 * To add header and footer view that can be hidden without taking up screen space,
 * they must be wrapped to LinearLayouts http://pivotallabs.com/android-tidbits-6-22-2011-hiding-header-views/
 */
class LoadingStopper(context: Context, dimensions: ScreenParameters, idGenerator: IdGenerator) extends LinearLayout(context) {
  setId(idGenerator.nextId)
  setLayoutParams(new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
  setOrientation(LinearLayout.VERTICAL)
  private val view = new TextView(context)
  view.setId(idGenerator.nextId)
  view.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
  view.setTextSize(dimensions.overviewContentTextSize)
  view.setTypeface(null, Typeface.BOLD)
  view.setVisibility(View.GONE)
  addView(view)

  def show(message: String, loadingHandler: => View => Unit) {
    setMessage(message)
    view.setOnClickListener(loadingHandler)
    view.setVisibility(View.VISIBLE)
  }

  def setMessage(message: String) { view.setText(message) }

  def hide() { view.setVisibility(View.GONE) }
}

class AgendaAdapter(activity: Activity, listView: AgendaView, statusTextView: TextView) extends BaseAdapter with LoaderCallbacks[Cursor] {
  private lazy val service = new CalendarEventService(activity)

  private val loadWindowLock = new Object
  private val renderer = new AgendaRenderer(activity)
  private val model = new EventModel
  private val dateFormat = DateTimeFormat.forPattern("d.M.yyyy E").withLocale(Locale.ENGLISH)

  private val howManyDaysToLoadAtTime = EventLoaderConfig.howManyDaysToLoadAtTime
  private val maxEventlessDaysToLoad = 3 * 360

  private val loading = new AtomicBoolean(false)
  val tooMuchPast = new AtomicBoolean(false)
  val tooMuchFuture = new AtomicBoolean(false)
  @volatile private var firstDayToLoad = getFocusDay.minusDays(howManyDaysToLoadAtTime)
  @volatile private var lastDayToLoad = getFocusDay.plusDays(howManyDaysToLoadAtTime)
  @volatile private var setSelectionToFocusDayAfterLoading = false

  def getFocusDay = model.focusDay

  override def getCount: Int = model.size

  override def getItem(position: Int): Option[DayWithEvents] = model.getItemFromContents(position)

  override def getItemId(position: Int): Long = getItem(position).map { _.id }.getOrElse(-1)

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    if (position == 0 && tooMuchPast.get()) {
      val loadMoreHandler: View => Unit = { _ =>
        tooMuchPast.set(false)
        triggerLoading()
      }
      listView.headerView.show("Click to load events before " + dateFormat.print(firstDayToLoad), loadMoreHandler)
    } else if (tooMuchFuture.get()) {
      listView.footerView.show("Click to load events after " + dateFormat.print(lastDayToLoad),
        { v: View => {
          tooMuchFuture.set(false)
          loadWindowLock.synchronized { model.setFocusDay(lastDayToLoad) }
          triggerLoading()
        } })
    }
    val item: Option[DayWithEvents] = getItem(position)
    renderer.createLoadingOrRealViewFor(item, getFocusDay)
  }

  def focusOn(day: LocalDate) {
    resetLoadingWindowTo(day)
    setSelectionToFocusDayAfterLoading = true
    triggerLoading()
  }

  private def resetLoadingWindowTo(focusDay: LocalDate) {
    loadWindowLock.synchronized {
      model.setFocusDay(focusDay)
      firstDayToLoad = focusDay.minusDays(howManyDaysToLoadAtTime)
      lastDayToLoad = focusDay.plusDays(howManyDaysToLoadAtTime)
    }
  }

  private def triggerLoading() {
    triggerLoading(firstDayToLoad, lastDayToLoad)
  }

  private def triggerLoading(startDate: LocalDate, endDate: LocalDate) {
    if (loading.getAndSet(true)) {
      debug("Already load in progress")
      return
    }
    statusTextView.setText("Load")
    val args = new Bundle
    args.putLong("start", startDate)
    args.putLong("end", endDate)
    debug("Initing loading with " + startDate + "--" + endDate)
    activity.getLoaderManager.initLoader(19, args, this)
  }

  def loadMorePast(firstVisibleDay: Option[LocalDate], lastVisibleDay: Option[LocalDate]) {
    debug("Going to load more past...")
    if (loading.get()) {
      debug("Already loading, not loading past then")
      return
    }
    if (getFocusDay == model.firstDay) {
      firstDayToLoad = if (tooMuchPast.get()) firstDayToLoad else firstDayToLoad.minusDays(howManyDaysToLoadAtTime)
      listView.headerView.setMessage("Click to load events before " + dateFormat.print(firstDayToLoad))
      setSelectionToFocusDayAfterLoading = true
      val currentPastDays = Days.daysBetween(firstDayToLoad, getFocusDay).getDays
      if (currentPastDays > maxEventlessDaysToLoad) {
        debug("currentPastDays == " + currentPastDays)
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
    debug(s"Going to load more future... visible currently $firstVisibleDay -- $lastVisibleDay")
    if (loading.get()) {
      debug("Already loading, not loading future then")
      return
    }
    if (lastVisibleDay.isDefined && Days.daysBetween(lastVisibleDay.get, model.lastDay).getDays <= howManyDaysToLoadAtTime) {
      val currentWindowEnd = lastVisibleDay.map { d => if (d.isAfter(lastDayToLoad)) d else lastDayToLoad }.get
      lastDayToLoad = currentWindowEnd.plusDays(howManyDaysToLoadAtTime)
      listView.footerView.setMessage("Click to load events after " + dateFormat.print(lastDayToLoad))

      val dayFromWhichToCalculateLoadedFutureLength = lastVisibleDay.getOrElse(getFocusDay)
      val currentFutureDays = Days.daysBetween(dayFromWhichToCalculateLoadedFutureLength, lastDayToLoad).getDays
      if (currentFutureDays > maxEventlessDaysToLoad) {
        tooMuchFuture.set(true)
        notifyDataSetChanged()
      } else {
        triggerLoading()
      }
    }
  }

  override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon
    ContentUris.appendId(uriBuilder, args.get("start").asInstanceOf[Long])
    ContentUris.appendId(uriBuilder, args.get("end").asInstanceOf[Long])
    val loader = service.createInstanceLoader(activity)
    loader.setUri(uriBuilder.build)
    loader
  }

  override def onLoadFinished(loader: Loader[Cursor], cursor: Cursor) {
    debug("Starting the Finished call")
    val f = Future {
      val events = time({ service.readEventsFromInstances(cursor) }, "readEvents")

      val eventsByDays: mutable.Map[LocalDate, Seq[CalendarEvent]] = new mutable.HashMap[LocalDate, Seq[CalendarEvent]]()
      time({
        events.foreach {
          e =>
            if (e != null) {
              val day = e.startTime.withTimeAtStartOfDay.toLocalDate
              val daysEventsOption: Option[Seq[CalendarEvent]] = eventsByDays.get(day)
              daysEventsOption match {
                case Some(eventList) => eventsByDays.put(day, eventList.+:(e))
                case None => eventsByDays.put(day, List(e))
              }
            }
        }
      }, "groupBy")

      val days = time({ eventsByDays.keys.toSet }, "getDays")

      val daysWithEvents: Set[DayWithEvents] = time({
        days.map {
          day =>
            val eventsOfDay = eventsByDays.get(day).getOrElse(Nil).sortBy { _.startTime.getMillis }
            DayWithEvents(day, eventsOfDay)
        }
      }, "create daysWithEventsMap")
      time({
        activity.runOnUiThread { () =>
          model.addOrUpdate(daysWithEvents, days)
          notifyDataSetChanged()
        }
      }, "Update model")
      time({ activity.runOnUiThread { () => statusTextView.setText("") } }, "setViewText")

      activity.runOnUiThread { () => {
          loadWindowLock.synchronized {
            if (setSelectionToFocusDayAfterLoading) {
              val indexInModelTakingOnAccountListViewHeader = model.indexOf(getFocusDay) + 1
              listView.setSelection(indexInModelTakingOnAccountListViewHeader)
              setSelectionToFocusDayAfterLoading = false
            }
          }
          activity.getLoaderManager.destroyLoader(19) // This makes onCreateLoader run again and use fresh search URI
          loading.set(false)
        }
      }
    }
    f onComplete {
      case Success(_) => debug("Finished loading!")
      case Failure(e) =>
        Logger.error("Error in processing load results", e)
        throw e
    }
  }

  override def onLoaderReset(loader: Loader[Cursor]) {}
}

class AgendaRenderer(activity: Activity) {
  private val DAYVIEW_TAG_ID = R.id.dayViewTagId
  private val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)

  private val dateWithWeekdayFormat = DateTimeFormat.forPattern("d.M.yyyy E").withLocale(Locale.ENGLISH)
  private val yearlessDateFormat = DateTimeFormat.forPattern("d.M.")
  private val dateAndTimeFormat = DateTimeFormat.forPattern("E d.M. HH:mm")
  private val timeFormat = DateTimeFormat.forPattern("HH:mm")

  private val idGenerator = new IdGenerator()

  def createLoadingOrRealViewFor(content: Option[DayWithEvents], focusDay: LocalDate): View = {
    val view: View = content match {
      case None =>
        val pendingView = new TextView(activity)
        pendingView.setText("Loading")
        pendingView
      case Some(dayWithEvents) => createDayView(dayWithEvents, focusDay)
    }
    view.setId(idGenerator.nextId)
    val dayViewParams = new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    view.setLayoutParams(dayViewParams)
    view
  }

  private def createDayView(dayWithEvents: DayWithEvents, focusDay: LocalDate): View = {
    val dayView = new LinearLayout(activity)
    dayView.setOrientation(LinearLayout.VERTICAL)

    val dayNameView = createDayNameView(dayWithEvents, focusDay)
    dayView.addView(dayNameView)

    val eventsOfDay = dayWithEvents.events

    eventsOfDay foreach {
      event =>
        val titleView = createTitleView(event)
        activity.runOnUiThread { () => dayView.addView(titleView) }
        val onClick: (View => Unit) = {
          _ =>
            debug("event clicked, starting activity")
            val intent = new Intent(activity, classOf[EditEventActivity])
            intent.putExtra(EVENT_ID, event.id.get)
            activity.startActivityForResult(intent, REQUEST_CODE_EDIT_EVENT)
            debug("After start activity")
        }
        titleView.setOnClickListener(onClick)
    }
    dayView.setTag(DAYVIEW_TAG_ID, dayWithEvents.id)
    dayView
  }

  private def createTitleView(event: CalendarEvent): TextView = {
    def createTimeRangePrefix: String = (event.spansMultipleDays, event.allDay) match {
      case (true, true) => yearlessDateFormat.print(event.startTime) + "–" + yearlessDateFormat.print(event.endTime) + " "
      case (true, false) => dateAndTimeFormat.print(event.startTime) + "–" + dateAndTimeFormat.print(event.endTime) + " "
      case (false, true) => ""
      case (false, false) => timeFormat.print(event.startTime) + "–" + timeFormat.print(event.endTime) + " "
    }
    val titleView = new TextView(activity)
    titleView.setId(idGenerator.nextId)
    val params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    titleView.setLayoutParams(params)
    titleView.setTextSize(dimensions.overviewContentTextSize)
    titleView.setText(createTimeRangePrefix + event.title)
    titleView.setBackgroundColor(Color.BLACK)
    titleView.setTextColor(Color.WHITE)

    titleView
  }

  def createDayNameView(dayWithEvents: DayWithEvents, focusDay: LocalDate): TextView = {
    val dayNameView = new TextView(activity)
    dayNameView.setId(idGenerator.nextId)
    val dayNameParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    dayNameView.setLayoutParams(dayNameParams)
    dayNameView.setTextSize(dimensions.overviewContentTextSize)
    val day = dayWithEvents.day
    if (day == focusDay) {
      val box = ui.util.Draw.createBoundingBoxBackground
      dayNameView.setBackground(box)
    }
    dayNameView.setTextColor(dimensions.pavlova)
    dayNameView.setTypeface(null, Typeface.BOLD_ITALIC)
    dayNameView.setText(dateWithWeekdayFormat.print(day))
    dayNameView.setOnLongClickListener {
      view: View =>
        debug("+ createNewEvent from agenda day name")
        val intent = new Intent(activity, classOf[EditEventActivity])
        val chosenDayAsMillis: Long = day
        intent.putExtra(FOCUS_DATE_EPOCH_MILLIS, chosenDayAsMillis)
        intent.putExtra(EVENT_DATE, chosenDayAsMillis + new DateTime().getMillisOfDay)
        activity.startActivityForResult(intent, REQUEST_CODE_EDIT_EVENT)
        true
    }
    dayNameView
  }
}

