package fi.allacca

import android.app.Activity
import android.widget._
import android.view.{ViewGroup, View}
import org.joda.time.{DateTime, LocalDate}
import android.widget.AbsListView.OnScrollListener
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import fi.allacca.dates.YearAndWeek

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

class WeeksAdapter2(activity: Activity)  extends BaseAdapter {
  private val idGenerator = new IdGenerator

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
    val item: YearAndWeek = getItem(position)
    val weekView = new TextView(activity)
    weekView.setId(idGenerator.nextId)
    val text = s"${item.week} ${item.year}"
    weekView.setText(text)
    weekView
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