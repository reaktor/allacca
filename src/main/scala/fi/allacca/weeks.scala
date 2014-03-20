package fi.allacca

import android.app.Activity
import android.widget._
import android.view.{Gravity, ViewGroup, View}
import org.joda.time.{DateTime, LocalDate}
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
        }
        if (lastVisibleItem > (adapter.getCount - Config.howManyWeeksToLoadAtTime)) {
          adapter.loadMoreFuture()
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

  //focusDay will always be the first day to load
  def firstDayToLoad = model.getStartDay
  def lastDayToLoad = model.getStartDay.plusWeeks(Config.howManyWeeksToLoadAtTime)

  def loadMorePast() {
    println("adapter.loadMorePast")
  }
  def loadMoreFuture() {
    println("adapter.loadMoreFuture")
  }

  def getCount: Int = model.getCount

  def getItem(position: Int): YearAndWeek = model.getItem(position)

  def getItemId(position: Int): Long = position

  def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val yearAndWeek: YearAndWeek = getItem(position)
    if (convertView == null)
      renderer.createWeekView(model.getFocusDay, yearAndWeek)
    else
      renderer.updateView(model.getFocusDay, yearAndWeek, convertView)
  }
}

class WeeksModel {
  @volatile private var startDay: DateTime = new DateTime().withTimeAtStartOfDay
  @volatile private var focusDay: DateTime = new DateTime().withTimeAtStartOfDay

  def getCount = Config.initialWeekCount
  def getStartDay = startDay
  def getFocusDay = focusDay
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

  def createWeekView(focusDay: DateTime, yearAndWeek: YearAndWeek) = {
    val wholeLineLayout : LinearLayout = new LinearLayout(activity)
    wholeLineLayout.setOrientation(LinearLayout.HORIZONTAL)
    val weekNumberView = createWeekNumberView(yearAndWeek)
    val dayViews = createDayViews(focusDay, yearAndWeek)
    wholeLineLayout.addView(weekNumberView)
    dayViews.foreach { dayView =>  wholeLineLayout.addView(dayView) }
    wholeLineLayout.getRootView
  }

  def createDayViews(focusDay: DateTime, yearAndWeek: YearAndWeek) = {
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
      setDayValue(day, dayView, focusDay)
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