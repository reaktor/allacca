package fi.allacca

import android.app.Activity
import android.widget._
import android.view.{Gravity, ViewGroup, View}
import org.joda.time.{Weeks, DateTime, LocalDate}
import android.widget.AbsListView.OnScrollListener
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import fi.allacca.dates.YearAndWeek
import org.joda.time.format.DateTimeFormat
import android.graphics.Color

object Config{
  val howManyWeeksToLoadAtTime = 20
  val initialWeekCount = 104
}

class WeeksView(activity: Activity, adapter: WeeksAdapter2) extends ListView(activity) {
  def start() {
    setAdapter(adapter)
    setOnScrollListener(new OnScrollListener {
      def onScrollStateChanged(view: AbsListView, scrollState: Int) {
        Log.d(TAG + WeeksView.this.getClass.getSimpleName, s"scrollState==$scrollState")
      }

      def onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
        println("WeeksView.onScroll")
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
      }
    })
  }

}

class WeeksAdapter2(activity: Activity, dimensions: ScreenParameters)  extends BaseAdapter {
  private val idGenerator = new IdGenerator
  private val renderer = new WeekViewRenderer(activity, dimensions)  
  private val model = new WeeksModel
  private val loading = new AtomicBoolean(false)

  def loadMorePast() {
    Log.i(TAG, "adapter.loadMorePast")
    model.setFocusDay(model.getStartDay)
    model.setStartDay(model.getStartDay.minusWeeks(Config.howManyWeeksToLoadAtTime))
    notifyDataSetChanged()
  }
  def loadMoreFuture() {
    Log.i(TAG, "adapter.loadMoreFuture")
    model.setStartDay(model.getStartDay.plusWeeks(Config.howManyWeeksToLoadAtTime))
    notifyDataSetChanged()
  }

  def getFocusDayIndex = model.getFocusDayIndex
  def getIndex(yearAndWeek: YearAndWeek) = model.getIndex(yearAndWeek)

  def getCount: Int = model.getCount

  def getItem(position: Int): YearAndWeek = model.getItem(position)

  def getItemId(position: Int): Long = position

  def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val yearAndWeek: YearAndWeek = getItem(position)
    if (convertView == null)
      renderer.createWeekView(model.getChosenDay, yearAndWeek)
    else
      renderer.updateView(model.getChosenDay, yearAndWeek, convertView)
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
  def getChosenDay = chosenDay
  def getFocusDayIndex = Weeks.weeksBetween(startDay, focusDay).getWeeks
  def getIndex(yearAndWeek: YearAndWeek) = Weeks.weeksBetween(startDay, yearAndWeek.firstDay).getWeeks
  def setFocusDay(newFocus: DateTime) { focusDay = newFocus }
  def getItem(position: Int): YearAndWeek = YearAndWeek.from(startDay.plusWeeks(position))
  def startWeek = startDay.weekOfWeekyear()
  def startYear = startDay.year()
}

class WeekViewRenderer(activity: Activity, dimensions: ScreenParameters) {
  val fmt = DateTimeFormat.forPattern("d")

  def updateView(focusDay: DateTime, yearAndWeek: YearAndWeek, convertView: View) = {
    val viewGroup = convertView.asInstanceOf[ViewGroup]
    def getTextView(index: Int, viewGroup: ViewGroup) = viewGroup.getChildAt(index).asInstanceOf[TextView]
    val weekNumberView = getTextView(0, viewGroup)
    weekNumberView.setText(yearAndWeek.week.toString)
    0 to 6 map { index =>
      val dayView = getTextView(index+1, viewGroup)
      val day  = yearAndWeek.days(index)
      setDayValue(day, dayView, focusDay)
    }
    convertView
  }

  def createWeekView(chosenDay: DateTime, yearAndWeek: YearAndWeek) = {
    val wholeLineLayout : LinearLayout = new LinearLayout(activity)
    wholeLineLayout.setOrientation(LinearLayout.HORIZONTAL)
    val weekNumberView = createWeekNumberView(yearAndWeek)
    val dayViews = createDayViews(chosenDay, yearAndWeek)
    wholeLineLayout.addView(weekNumberView)
    dayViews.foreach { dayView =>  wholeLineLayout.addView(dayView) }
    wholeLineLayout.getRootView
  }

  def createDayViews(chosenDay: DateTime, yearAndWeek: YearAndWeek) = {
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
      setDayValue(day, dayView, chosenDay)
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

  private def setDayValue(day: DateTime, dayView: TextView, focusDay: DateTime) {
    val dayNumber = fmt.print(day)
    dayView.setText(dayNumber)
    if (focusDay.withTimeAtStartOfDay == day) dayView.setTextColor(Color.RED) else dayView.setTextColor(Color.WHITE)
  }

}