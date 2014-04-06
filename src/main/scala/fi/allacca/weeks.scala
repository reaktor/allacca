package fi.allacca

import android.app.Activity
import android.widget._
import android.view.{Gravity, ViewGroup, View}
import org.joda.time.{LocalDate, Weeks, DateTime}
import android.widget.AbsListView.OnScrollListener
import java.util.concurrent.atomic.AtomicBoolean
import fi.allacca.dates.YearAndWeek
import org.joda.time.format.DateTimeFormat
import android.graphics.{Typeface, Color}
import java.util.{Calendar, Locale}
import java.text.DateFormatSymbols
import fi.allacca.Logger._
import android.graphics.drawable.shapes.RectShape
import android.graphics.drawable.ShapeDrawable
import android.graphics.Paint.Style
import android.app.LoaderManager.LoaderCallbacks
import android.os.Bundle
import android.content.{ContentUris, Loader}
import android.database.Cursor
import android.provider.CalendarContract

object Config{
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
          adapter.loadEvents(view.getFirstVisiblePosition, view.getLastVisiblePosition)
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

  def loadEvents(firstVisibleItemPosition: Int, lastVisibleItemPosition: Int) {
    val startWeek = model.getItem(firstVisibleItemPosition)
    val endWeek = model.getItem(lastVisibleItemPosition)
    eventLoaderController.loadEventsBetween(startWeek.firstDay, endWeek.lastDay)
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

  def getIndex(yearAndWeek: YearAndWeek) = model.getIndex(yearAndWeek)

  def getCount: Int = model.getCount

  def getItem(position: Int): YearAndWeek = model.getItem(position)

  def getItemId(position: Int): Long = position

  def onDayClick(day: DateTime) {
    onDayClickCallback(day)
    model.setChosenDay(day)
    notifyDataSetChanged()
  }

  def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val yearAndWeek: YearAndWeek = getItem(position)
    if (convertView == null)
      renderer.createWeekView(model.getChosenDay, yearAndWeek, onDayClick, onDayLongClickCallback)
    else
      renderer.updateView(model.getChosenDay, yearAndWeek, convertView, onDayClick, onDayLongClickCallback)
  }

}

class EventLoaderController(activity: Activity, model: WeeksModel, refreshCallback: () => Unit) extends LoaderCallbacks[Cursor] {
  private val LOADER_ID = 100
  private lazy val service = new CalendarEventService(activity)

  def loadEventsBetween(start: DateTime, end: DateTime) {
    println(s"Loading events between $start and $end")
    val args = new Bundle
    args.putLong("start", start.getMillis)
    args.putLong("end", end.getMillis)
    debug("Initing loading with " + start + "--" + end)
    activity.getLoaderManager.initLoader(LOADER_ID, args, this)
  }

  override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    println(s"onCreateLoader")
    val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon
    ContentUris.appendId(uriBuilder, args.get("start").asInstanceOf[Long])
    ContentUris.appendId(uriBuilder, args.get("end").asInstanceOf[Long])
    val loader = service.createInstanceLoader(activity)
    loader.setUri(uriBuilder.build)
    loader
  }

  override def onLoadFinished(loader: Loader[Cursor], cursor: Cursor) {
    val (_, daysWithEvents) = service.readEventsByDays(cursor)
    println(s"onLoadFinished $daysWithEvents")
    activity.runOnUiThread { () =>
      model.addOrUpdate(daysWithEvents)
      refreshCallback()
    }
    activity.getLoaderManager.destroyLoader(LOADER_ID)
  }

  override def onLoaderReset(loader: Loader[Cursor]) {}
}

class WeeksModel {
  @volatile private var startDay: DateTime = currentDay
  @volatile private var chosenDay: DateTime = new DateTime().withTimeAtStartOfDay
  @volatile private var events: Map[Long, DayWithEvents] = Map()

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
    events.get(day.getMillis) match {
      case Some(dayWithEvents) => dayWithEvents.events.size
      case _ => 0
    }
  }
  def hasEvents(day: DateTime) = getEventCount(day) > 0
  def addOrUpdate(daysWithEvents: Set[DayWithEvents]) {
    daysWithEvents foreach { dayWithEvents =>
      events += dayWithEvents.id -> dayWithEvents
    }
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
    val backgroundColor = if (model.hasEvents(day)) {
      dimensions.governorBay
    } else if ((day.getMonthOfYear % 2) == 0) {
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