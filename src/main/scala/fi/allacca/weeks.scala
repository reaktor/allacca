package fi.allacca

import android.app.Activity
import android.widget._
import android.view.{Gravity, ViewGroup, View}
import org.joda.time.{Weeks, DateTime}
import android.widget.AbsListView.OnScrollListener
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import fi.allacca.dates.YearAndWeek
import org.joda.time.format.DateTimeFormat
import android.graphics.Color
import java.util.Locale

object Config{
  val howManyWeeksToLoadAtTime = 20
  val initialWeekCount = 104
}

class WeeksView(activity: Activity, adapter: WeeksAdapter2, shownMonthsView: ShownMonthsView) extends ListView(activity) {
  def start() {
    setAdapter(adapter)
    setOnScrollListener(new OnScrollListener {
      def onScrollStateChanged(view: AbsListView, scrollState: Int) {
      }

      def onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
        val lastVisibleItem = firstVisibleItem + visibleItemCount
        if (firstVisibleItem == 0) {
          adapter.loadMorePast()
          view.setSelection(adapter.getFocusDayIndex)
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

class WeeksAdapter2(activity: Activity, dimensions: ScreenParameters, onDayClickCallback: DateTime => Unit, onDayLongClickCallback: DateTime => Boolean)  extends BaseAdapter {
  private val renderer = new WeekViewRenderer(activity, dimensions)
  private val model = new WeeksModel
  private val loading = new AtomicBoolean(false)

  def loadMorePast() {
    model.setStartDay(model.getStartDay.minusWeeks(Config.howManyWeeksToLoadAtTime))
    notifyDataSetChanged()
  }
  def loadMoreFuture() {
    model.setStartDay(model.getStartDay.plusWeeks(Config.howManyWeeksToLoadAtTime))
    notifyDataSetChanged()
  }

  def getFocusDayIndex = model.getFocusDayIndex
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

class WeeksModel {
  @volatile private var startDay: DateTime = currentDay
  @volatile private var focusDay: DateTime = new DateTime().withTimeAtStartOfDay
  @volatile private var chosenDay: DateTime = new DateTime().withTimeAtStartOfDay

  def currentDay: DateTime = new DateTime().withTimeAtStartOfDay
  def getCount = Config.initialWeekCount
  def getStartDay = startDay
  def setStartDay(startDay: DateTime) { this.startDay = startDay }
  def getFocusDay = focusDay
  def getChosenDay = { chosenDay }
  def setChosenDay(chosen: DateTime) { chosenDay = chosen }
  def getFocusDayIndex = Weeks.weeksBetween(startDay, focusDay).getWeeks
  def getIndex(yearAndWeek: YearAndWeek) = Weeks.weeksBetween(startDay, yearAndWeek.firstDay).getWeeks
  def setFocusDay(newFocus: DateTime) { focusDay = newFocus }
  def getItem(position: Int): YearAndWeek = YearAndWeek.from(startDay.plusWeeks(position))
  def startWeek = startDay.weekOfWeekyear()
  def startYear = startDay.year()
}

class WeekViewRenderer(activity: Activity, dimensions: ScreenParameters) {
  val fmt = DateTimeFormat.forPattern("d")

  def updateView(chosenDay: DateTime, yearAndWeek: YearAndWeek, convertView: View, onDayClick: DateTime => Unit, onDayLongClick: DateTime => Boolean) = {
    val viewGroup = convertView.asInstanceOf[ViewGroup]
    def getTextView(index: Int, viewGroup: ViewGroup) = viewGroup.getChildAt(index).asInstanceOf[TextView]
    val weekNumberView = getTextView(0, viewGroup)
    weekNumberView.setText(yearAndWeek.week.toString)
    0 to 6 map { index =>
      val dayView = getTextView(index+1, viewGroup)
      val day  = yearAndWeek.days(index)
      initDayView(day, dayView, chosenDay)
      setListeners(dayView, onDayClick, day, onDayLongClick)
    }
    convertView
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
    val weekNumberView = createWeekNumberView(yearAndWeek)
    val dayViews = createDayViews(chosenDay, yearAndWeek, onDayClick, onDayLongClick)
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
      dayView.setId(index+1)
      initDayView(day, dayView, chosenDay)
      setListeners(dayView, onDayClick, day, onDayLongClick)
      dayView
    }
  }

  def createWeekNumberView(yearAndWeek: YearAndWeek) = {
    val weekNumberView = new TextView(activity)
    weekNumberView.setId(0)
    weekNumberView.setWidth(dimensions.weekNumberWidth)
    weekNumberView.setHeight(dimensions.weekRowHeight)
    weekNumberView.setTextSize(dimensions.overviewContentTextSize)
    weekNumberView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL)
    weekNumberView.setText(yearAndWeek.week.toString)
    weekNumberView
  }

  private def initDayView(day: DateTime, dayView: TextView, focusDay: DateTime) {
    val dayNumber = fmt.print(day)
    dayView.setText(dayNumber)
    if (new DateTime().withTimeAtStartOfDay() == day) {
      dayView.setTextColor(Color.YELLOW)
    }
    else if (focusDay.withTimeAtStartOfDay == day) {
      dayView.setTextColor(Color.RED)
    } else {
      dayView.setTextColor(Color.WHITE)
    }
    if ((day.getMonthOfYear % 2) == 0) {
      dayView.setBackgroundColor(dimensions.darkGrey)
    } else {
      dayView.setBackgroundColor(Color.BLACK)
    }
  }
}

class ShownMonthsView(activity: Activity) extends TextView(activity) {
  private val fmt = DateTimeFormat.forPattern("MMM yyyy").withLocale(Locale.ENGLISH)

  def render(first: YearAndWeek, last: YearAndWeek): String = {
    def render(dateTime: DateTime): String = fmt.print(dateTime)
    render(first.firstDay) + "–" + render(last.lastDay)
  }
}