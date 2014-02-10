package fi.allacca

import android.widget._
import android.content.ContentUris
import org.joda.time.{Weeks, DateTime}
import fi.allacca.dates.YearAndWeek
import android.view.{Gravity, ViewGroup, View}
import org.joda.time.format.DateTimeFormat
import android.graphics.Color
import android.provider.CalendarContract
import android.database.Cursor
import android.app.Activity
import android.os.AsyncTask
import android.util.Log
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class WeeksAdapter(activity: Activity, dimensions: ScreenParameters) extends BaseAdapter {
  private val beginningOfEpoch = new DateTime(0, 1, 1, 0, 0, 0).withTimeAtStartOfDay()

  def positionOfNow: Int = Weeks.weeksBetween(beginningOfEpoch, new DateTime()).getWeeks

  override def getCount: Int = Integer.MAX_VALUE

  override def getItem(position: Int): YearAndWeek = YearAndWeek.from(beginningOfEpoch.plusWeeks(position))

  override def getItemId(position: Int): Long = position

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    Log.d(TAG, "Get view of position " + position)
    if (convertView != null) {
      Log.d(TAG, "Returning convertView of position " + position)
      return convertView
    }

    Log.d(TAG, "Creating new view of position " + position)
    val wholeLineLayout : LinearLayout = new LinearLayout(activity)
    wholeLineLayout.setOrientation(LinearLayout.HORIZONTAL)

    val yearAndWeek = getItem(position)
    val weeksEventsFuture = Future { loadEventsOf(yearAndWeek) }
    weeksEventsFuture.onComplete {
      case Success(events) => render(events)
      case Failure(t) => Log.e(TAG, "Error when loading", t)
    }

    def render(eventsOfWeek: Seq[CalendarEvent]): Unit = {
      Log.d(TAG, "Rendering " + eventsOfWeek.size)
      val days = yearAndWeek.days

      var id = 1
      val dayViews = days.map { d =>
        val hasEvents = eventsOfWeek.exists { _.isDuring(d) }
        val dayView = new TextView(activity)
        dayView.setPadding(5, 5, 5, 5)
        val fmt = DateTimeFormat.forPattern("d")
        val dayNumber = fmt.print(d)
        dayView.setText(dayNumber)
        dayView.setTextColor(Color.WHITE)
        dayView.setBackgroundColor(if (hasEvents) dimensions.governorBay else Color.BLACK)
        dayView.setTextSize(dimensions.overviewContentTextSize)
        dayView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL)
        dayView.setHeight(dimensions.weekRowHeight)
        dayView.setWidth(dimensions.dayColumnWidth)

        dayView.setId(id)
        id = id + 1
        dayView
      }

      val weekNumberView = new TextView(activity)
      weekNumberView.setId(id)
      weekNumberView.setWidth(dimensions.weekNumberWidth)
      weekNumberView.setHeight(dimensions.weekRowHeight)
      weekNumberView.setText(yearAndWeek.week.toString)
      weekNumberView.setTextSize(dimensions.overviewContentTextSize)
      weekNumberView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL)

      wholeLineLayout.post(new Runnable {
        def run(): Unit = {
          wholeLineLayout.addView(weekNumberView)
          dayViews.foreach { dayView => wholeLineLayout.addView(dayView) }
        }
      })
    }
    wholeLineLayout.getRootView
  }

  private def loadEventsOf(week: YearAndWeek): Seq[CalendarEvent] = {
    val projection = Array[String] (
      "_id",
      "title",
      "dtstart",
      "dtend",
      "description"
    )

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon
    ContentUris.appendId(builder, week.firstDay.withTimeAtStartOfDay.getMillis)
    ContentUris.appendId(builder, week.lastDay.withTimeAtStartOfDay.getMillis)
    val cursor: Cursor = activity.getContentResolver.query(builder.build(), projection, "", Array(), "")
    val results = new collection.mutable.MutableList[CalendarEvent]
    while (cursor.moveToNext()) {
        val title = cursor.getString(1)
        val start = cursor.getLong(2)
        val end = cursor.getLong(3)
        val description = cursor.getString(4)
        results += new CalendarEvent(title, start, end, description)
    }
    cursor.close()
    results
  }
}
