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
    /*val weekView = new TextView(activity)
    weekView.setId(idGenerator.nextId)
    val text = s"${item.week} ${item.year}"
    weekView.setText(text)
    weekView*/
    renderer.createWeekView(yearAndWeek)
  }
}

class WeeksModel {
  @volatile private var startDay: DateTime = new DateTime().withTimeAtStartOfDay

  def getCount = Config.initialWeekCount
  def getStartDay = startDay
  def getItem(position: Int): YearAndWeek = YearAndWeek.from(startDay.plusWeeks(position))
  def startWeek = startDay.weekOfWeekyear()
  def startYear = startDay.year()

}

class WeekViewRenderer(activity: Activity, dimensions: ScreenParameters) {

  def createWeekView(yearAndWeek: YearAndWeek) = {
    val wholeLineLayout : LinearLayout = new LinearLayout(activity)
    wholeLineLayout.setOrientation(LinearLayout.HORIZONTAL)
    val dayViews = createDayViews(yearAndWeek)
    val weekNumberView = createWeekNumberView(yearAndWeek)
    wholeLineLayout.addView(weekNumberView)
    dayViews.foreach { dayView =>  wholeLineLayout.addView(dayView) }
    wholeLineLayout.getRootView
  }

  def createDayViews(yearAndWeek: YearAndWeek) = {
    yearAndWeek.days map { day =>
      val dayView = new TextView(activity)
      dayView.setPadding(5, 5, 5, 5)
      val fmt = DateTimeFormat.forPattern("d")
      val dayNumber = fmt.print(day)
      dayView.setText(dayNumber)
      dayView.setTextColor(Color.WHITE)
      dayView.setBackgroundColor(Color.BLACK)
      dayView.setTextSize(dimensions.overviewContentTextSize)
      dayView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL)
      dayView.setHeight(dimensions.weekRowHeight)
      dayView.setWidth(dimensions.dayColumnWidth)
      dayView.setId(View.generateViewId)
      dayView
    }
  }

  def createWeekNumberView(yearAndWeek: YearAndWeek) = {
    val weekNumberView = new TextView(activity)
    weekNumberView.setId(View.generateViewId())
    weekNumberView.setWidth(dimensions.weekNumberWidth)
    weekNumberView.setHeight(dimensions.weekRowHeight)
    weekNumberView.setText(yearAndWeek.week.toString)
    weekNumberView.setTextSize(dimensions.overviewContentTextSize)
    weekNumberView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL)
    weekNumberView
  }
}