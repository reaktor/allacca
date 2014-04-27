package fi.allacca

import android.app.Activity
import android.widget._
import android.view.{Gravity, ViewGroup, View}
import org.joda.time.{Days, Weeks, DateTime}
import android.widget.AbsListView.OnScrollListener
import java.util.concurrent.atomic.AtomicBoolean
import fi.allacca.dates.YearAndWeek
import org.joda.time.format.DateTimeFormat
import android.graphics.{Paint, Typeface, Color}
import java.util.{Calendar, Locale}
import java.text.DateFormatSymbols
import fi.allacca.Logger._
import android.graphics.drawable.ShapeDrawable
import android.app.LoaderManager.LoaderCallbacks
import android.os.Bundle
import android.content.{ContentUris, Loader}
import android.database.Cursor
import android.provider.CalendarContract
import scala.concurrent.Future
import scala.Predef._
import scala.Some
import scala.concurrent.ExecutionContext.Implicits.global

object Config {
  def eventLoadWindow(day: DateTime): (DateTime, DateTime) = {
    (day.minusDays(loadEventsForDaysInOneDirection), day.plusDays(loadEventsForDaysInOneDirection))
  }
  val loadEventsForDaysInOneDirection = 240
  val eventLoadingThresholdDays = 30
  val howManyWeeksToLoadAtTime = 20
  val initialWeekCount = 104
}

class WeeksView(activity: Activity, adapter: WeeksAdapter, shownMonthsView: ShownMonthsView) extends ListView(activity) {
  def start() {
    setAdapter(adapter)
    setOnScrollListener(new OnScrollListener {
      def onScrollStateChanged(view: AbsListView, scrollState: Int) {
        Logger.info(s"${WeeksView.this.getClass.getSimpleName} scrollState==$scrollState")
        if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
          adapter.maybeLoadEvents(view.getFirstVisiblePosition, view.getLastVisiblePosition)
        }
      }

      def onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
        info("WeeksView.onScroll") //Let's keep this still, there was some bug which caused this to be constantly called. Doesn't occur all the time.
        val lastVisibleItem = firstVisibleItem + visibleItemCount
        if (firstVisibleItem == 0) {
          adapter.loadMorePast()
          view.setSelection(adapter.getStartDayIndex)
        }
        if (lastVisibleItem > (adapter.getCount - Config.howManyWeeksToLoadAtTime)) {
          val firstVisibleYearAndWeek = adapter.getItem(firstVisibleItem)
          adapter.loadMoreFuture()
          val indexOfFirstVisibleBeforeLoading = adapter.getIndex(firstVisibleYearAndWeek)
          view.setSelection(indexOfFirstVisibleBeforeLoading)
        }
        val shownMonths = shownMonthsView.render(adapter.getItem(firstVisibleItem), adapter.getItem(lastVisibleItem))
        shownMonthsView.setText(shownMonths)
      }
    })
  }
}

class WeeksAdapter(activity: Activity, dimensions: ScreenParameters, onDayClickCallback: DateTime => Unit, onDayLongClickCallback: DateTime => Boolean)  extends BaseAdapter {
  private val model = new WeeksModel
  private val renderer = new WeekViewRenderer(activity, model, dimensions)
  private val loading = new AtomicBoolean(false)
  private val eventLoaderController = new EventLoaderController(activity, model, refresh)

  def loadMorePast() {
    info("adapter.loadMorePast")
    model.setStartDay(model.getStartDay.minusWeeks(Config.howManyWeeksToLoadAtTime))
    notifyDataSetChanged()
  }
  def loadMoreFuture() {
    info("adapter.loadMoreFuture")
    model.setStartDay(model.getStartDay.plusWeeks(Config.howManyWeeksToLoadAtTime))
    notifyDataSetChanged()
  }

  def maybeLoadEvents(firstVisibleItemPosition: Int, lastVisibleItemPosition: Int) {
    val firstShownDay = model.getItem(firstVisibleItemPosition).firstDay
    val lastShownDay = model.getItem(lastVisibleItemPosition).lastDay
    if (shouldLoadEvents(firstShownDay, lastShownDay)) {
      val between = Days.daysBetween(firstShownDay, lastShownDay)
      val dayBetween = firstShownDay.plusDays(between.getDays / 2)
      val (start, end) = Config.eventLoadWindow(dayBetween)
      eventLoaderController.loadEventsBetween(start, end)
    } else {
      info("No need to load events yet")
    }
  }

  private def shouldLoadEvents(firstShownDay: DateTime, lastShownDay: DateTime): Boolean = {
    val (loadedStartMillis, loadedEndMillis) = model.getRangeOfLoadedEvents
    val loadedStart = new DateTime(loadedStartMillis)
    val loadedEnd = new DateTime(loadedEndMillis)

    def startDayThresholdExceeded = {
      firstShownDay.isBefore(loadedStart) || math.abs(Days.daysBetween(firstShownDay, loadedStart).getDays) < Config.eventLoadingThresholdDays
    }
    def endDayThresholdExceeded = {
      lastShownDay.isAfter(loadedEnd) || math.abs(Days.daysBetween(lastShownDay, loadedEnd).getDays) < Config.eventLoadingThresholdDays
    }
    startDayThresholdExceeded || endDayThresholdExceeded
  }

  def refresh() {
    notifyDataSetChanged()
  }

  /* returns the index of the date/week to be selected in view */
  def rollToDate(day: DateTime): Int = {
    model.setStartDay(day.minusWeeks(Config.howManyWeeksToLoadAtTime))
    notifyDataSetChanged()
    val week = YearAndWeek.from(day)
    getIndex(week)
  }

  def getStartDayIndex = model.getStartDayIndex

  def getIndex(yearAndWeek: YearAndWeek) = time ( { model.getIndex(yearAndWeek) }, "weeks.getIndex")

  def getCount: Int = time ( { model.getCount } , "weeks.getCount")

  def getItem(position: Int): YearAndWeek = time ( { model.getItem(position) } , "weeks.getItem")

  def getItemId(position: Int): Long = position

  def onDayClick(day: DateTime) {
    onDayClickCallback(day)
    model.setChosenDay(day)
    val (start, end) = Config.eventLoadWindow(day)
    eventLoaderController.loadEventsBetween(start, end)
    notifyDataSetChanged()
  }

  def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val yearAndWeek: YearAndWeek = getItem(position)
    if (convertView == null)
      time ( { renderer.createWeekView(model.getChosenDay, yearAndWeek, onDayClick, onDayLongClickCallback) }, "weeks.createWeekView" )
    else
      time ( { renderer.updateView(model.getChosenDay, yearAndWeek, convertView, onDayClick, onDayLongClickCallback) }, "weeks.updateView" )
  }

}

class EventLoaderController(activity: Activity, model: WeeksModel, refreshCallback: () => Unit) extends LoaderCallbacks[Cursor] {
  private val LOADER_ID = 100
  private lazy val service = new CalendarEventService(activity)

  def loadEventsBetween(start: DateTime, end: DateTime) {
    Logger.info(s"Loading events between $start and $end")
    val args = new Bundle
    args.putLong("start", start.getMillis)
    args.putLong("end", end.getMillis)
    debug("Initing loading with " + start + "--" + end)
    activity.getLoaderManager.restartLoader(LOADER_ID, args, this)
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
    val f: Future[LoadedEvents] = Future {
      processLoadedEvents(cursor)
    }
    f onSuccess {
      case newEvents: LoadedEvents => {
        updateUiWithNewEvents(newEvents)
      }
    }
    f onFailure {
      case t => Logger.info("ERROR: Could not process file: " + t.getMessage)
    }
  }

  private def processLoadedEvents(cursor: Cursor): LoadedEvents = {
    val (_, daysWithEvents) = service.readEventsByDays(cursor)
    cursor.close()
    val daysWithEventsMap = (daysWithEvents map {
      dayWithEvents: DayWithEvents =>
        (dayWithEvents.id, dayWithEvents)
    }).toMap
    val daysAsMillis = daysWithEventsMap.keys
    val start = daysAsMillis.min
    val end = daysAsMillis.max
    LoadedEvents(start, end, daysWithEventsMap)
  }

  private def updateUiWithNewEvents(newEvents: LoadedEvents) {
    activity.runOnUiThread {
      () =>
        model.updateEvents(newEvents)
        refreshCallback()
    }
  }

  override def onLoaderReset(loader: Loader[Cursor]) {}
}

case class LoadedEvents(start: Long, end: Long, daysWithEvents: Map[Long, DayWithEvents])

class WeeksModel {
  @volatile private var startDay: DateTime = currentDay
  @volatile private var chosenDay: DateTime = new DateTime().withTimeAtStartOfDay
  @volatile private var events: LoadedEvents = LoadedEvents(Long.MinValue, Long.MinValue, Map())

  def currentDay: DateTime = new DateTime().withTimeAtStartOfDay
  def getCount = Config.initialWeekCount
  def getStartDay = startDay
  def setStartDay(startDay: DateTime) { this.startDay = startDay }
  def getChosenDay = { chosenDay }
  def setChosenDay(chosen: DateTime) { chosenDay = chosen }
  def getStartDayIndex = Config.howManyWeeksToLoadAtTime
  def getIndex(yearAndWeek: YearAndWeek) = Weeks.weeksBetween(startDay, yearAndWeek.firstDay).getWeeks
  def getItem(position: Int): YearAndWeek = YearAndWeek.from(startDay.plusWeeks(position))
  def getEventCount(day: DateTime): Int = {
    events.daysWithEvents.get(day.getMillis) match {
      case Some(dayWithEvents) => dayWithEvents.events.size
      case _ => 0
    }
  }
  def getRangeOfLoadedEvents: (Long, Long) = (events.start, events.end)
  def hasEvents(day: DateTime) = getEventCount(day) > 0
  def updateEvents(newEvents: LoadedEvents) {
    events = newEvents
  }
}

class WeekViewRenderer(activity: Activity, model: WeeksModel, dimensions: ScreenParameters) {
  val fmt = DateTimeFormat.forPattern("d")
  val shortMonths = new DateFormatSymbols(Locale.ENGLISH).getShortMonths

  def updateView(chosenDay: DateTime, yearAndWeek: YearAndWeek, convertView: View, onDayClick: DateTime => Unit, onDayLongClick: DateTime => Boolean) = {
    val viewGroup = convertView.asInstanceOf[ViewGroup]
    def getTextView(index: Int, viewGroup: ViewGroup) = viewGroup.getChildAt(index).asInstanceOf[TextView]
    val monthLetterView = getTextView(0, viewGroup)
    initMonthLetterView(yearAndWeek, monthLetterView)
    val weekNumberView = getTextView(1, viewGroup)
    initWeekNumberView(yearAndWeek, weekNumberView)
    0 to 6 map { index =>
      val dayView = getTextView(index+2, viewGroup)
      val day  = yearAndWeek.days(index)
      initDayView(day, dayView, chosenDay)
      setListeners(dayView, onDayClick, day, onDayLongClick)
    }
    convertView
  }

  def initWeekNumberView(yearAndWeek: YearAndWeek, weekNumberView: TextView) {
    weekNumberView.setText(yearAndWeek.week.toString)
  }

  def initMonthLetterView(yearAndWeek: YearAndWeek, view: TextView) {
    val monthLetterForThisWeek = getMonthLetterForWeek(yearAndWeek)
    view.setText(monthLetterForThisWeek)
  }

  private def getMonthLetterForWeek(yearAndWeek: YearAndWeek): String = {
    val day =  yearAndWeek.firstDay
    val currentDayCalendar = day.toCalendar(Locale.getDefault())
    val weekOfMonth = currentDayCalendar.get(Calendar.WEEK_OF_MONTH)
    if (weekOfMonth < 5) {
      val weekOfMonthIndex = weekOfMonth - 2
      if (weekOfMonthIndex < 0) "" else (shortMonths(day.getMonthOfYear - 1)(weekOfMonthIndex) + "").toUpperCase
    } else { "" }
  }

  def setListeners(dayView: TextView, onDayClick: (DateTime) => Unit, day: DateTime, onDayLongClick: (DateTime) => Boolean) {
    dayView.setOnClickListener {
      view: View =>
        onDayClick(day)
    }
    dayView.setOnLongClickListener {
      view: View =>
        onDayLongClick(day)
    }
  }

  def createWeekView(chosenDay: DateTime, yearAndWeek: YearAndWeek, onDayClick: DateTime => Unit, onDayLongClick: DateTime => Boolean) = {
    val wholeLineLayout : LinearLayout = new LinearLayout(activity)
    wholeLineLayout.setOrientation(LinearLayout.HORIZONTAL)
    val monthLetterView = createMonthLetterView(yearAndWeek)
    val weekNumberView = createWeekNumberView(yearAndWeek)
    val dayViews = createDayViews(chosenDay, yearAndWeek, onDayClick, onDayLongClick)
    wholeLineLayout.addView(monthLetterView)
    wholeLineLayout.addView(weekNumberView)
    dayViews.foreach { dayView =>  wholeLineLayout.addView(dayView) }
    wholeLineLayout.getRootView
  }

  def createDayViews(chosenDay: DateTime, yearAndWeek: YearAndWeek, onDayClick: DateTime => Unit, onDayLongClick: DateTime => Boolean) = {
    0 to 6 map { index =>
      val day = yearAndWeek.days(index)
      val dayView = new TextView(activity)
      dayView.setPadding(5, 5, 5, 5)
      dayView.setBackgroundColor(Color.BLACK)
      dayView.setTextSize(dimensions.overviewContentTextSize)
      dayView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL)
      dayView.setHeight(dimensions.weekRowHeight)
      dayView.setWidth(dimensions.dayColumnWidth)
      dayView.setId(index+2)
      initDayView(day, dayView, chosenDay)
      setListeners(dayView, onDayClick, day, onDayLongClick)
      dayView
    }
  }

  def createWeekNumberView(yearAndWeek: YearAndWeek) = {
    val weekNumberView = new TextView(activity)
    weekNumberView.setId(1)
    weekNumberView.setWidth(dimensions.weekNumberWidth)
    weekNumberView.setHeight(dimensions.weekRowHeight)
    weekNumberView.setTextSize(dimensions.overviewContentTextSize)
    weekNumberView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL)
    weekNumberView.setTextColor(dimensions.pavlova)
    initWeekNumberView(yearAndWeek, weekNumberView)
    weekNumberView
  }

  def createMonthLetterView(yearAndWeek: YearAndWeek) = {
    val monthLetterView = new TextView(activity)
    monthLetterView.setId(0)
    monthLetterView.setWidth(dimensions.monthLetterWidth)
    monthLetterView.setHeight(dimensions.weekRowHeight)
    monthLetterView.setTextSize(dimensions.overviewContentTextSize)
    monthLetterView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL)
    monthLetterView.setTextColor(dimensions.pavlova)
    monthLetterView.setTypeface(null, Typeface.BOLD)
    initMonthLetterView(yearAndWeek, monthLetterView)
    monthLetterView
  }

  private def initDayView(day: DateTime, dayView: TextView, focusDay: DateTime) {
    val dayNumber = fmt.print(day)
    dayView.setText(dayNumber)
    val isFocus = focusDay.withTimeAtStartOfDay == day
    val textColor = if (new DateTime().withTimeAtStartOfDay() == day) {
      Color.YELLOW
    } else {
      if (day.getDayOfWeek >= 6) dimensions.weekendDayColor else { dimensions.weekDayColor }
    }
    dayView.setTextColor(textColor)
    var flags = dayView.getPaintFlags
    if (model.hasEvents(day)) flags |= Paint.UNDERLINE_TEXT_FLAG else flags &= ~Paint.UNDERLINE_TEXT_FLAG
    dayView.setPaintFlags(flags)
    val backgroundColor =
    if ((day.getMonthOfYear % 2) == 0) {
      dimensions.funBlue
    } else {
      Color.BLACK
    }
    dayView.setBackgroundColor(backgroundColor)
    if (isFocus) {
      val rectShapeDrawable: ShapeDrawable = ui.util.Draw.createBoundingBoxBackground
      dayView.setBackgroundDrawable(rectShapeDrawable)
      //The above deprecation is necessary for android 4.0.4 support. Newer versions can/should use this:
      //dayView.setBackground(rectShapeDrawable)
    }
  }
}

class ShownMonthsView(activity: Activity, dimensions: ScreenParameters) extends TextView(activity) {
  private val fmt = DateTimeFormat.forPattern("MMM yyyy").withLocale(Locale.ENGLISH)
  setTextColor(dimensions.pavlova)
  setTypeface(null, Typeface.BOLD_ITALIC)

  def render(first: YearAndWeek, last: YearAndWeek): String = {
    def render(dateTime: DateTime): String = fmt.print(dateTime)
    render(first.firstDay) + "–" + render(last.lastDay)
  }
}