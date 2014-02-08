package fi.allacca

import android.app._
import android.content.{ContentUris, Context}
import android.os.Bundle
import android.widget._
import android.view.{ViewGroup, View}
import android.util.{TypedValue, AttributeSet, Log}
import android.widget.AbsListView.OnScrollListener
import android.view.ViewTreeObserver.OnScrollChangedListener
import fi.allacca.dates.YearAndWeek
import org.joda.time.{Weeks, DateTime}
import android.provider.CalendarContract
import android.database.Cursor
import scala.collection
import org.joda.time.format.DateTimeFormat
import android.graphics.Color

class ObdActivity extends Activity with TypedViewHolder {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.obd)
    val listView = findViewById(R.id.infinite_events_list)
    listView.requestFocus()
  }
}

class InfiniteEventsListFragment extends ListFragment with OnScrollListener with OnScrollChangedListener {
  private lazy val activity = getActivity
  private lazy val adapter = new WeeksAdapter

  class WeeksAdapter extends BaseAdapter {
    private val beginningOfEpoch = new DateTime(1970, 1, 1, 0, 0, 0).withTimeAtStartOfDay()

    def positionOfNow: Int = Weeks.weeksBetween(beginningOfEpoch, new DateTime()).getWeeks

    override def getCount: Int = Integer.MAX_VALUE

    override def getItem(position: Int): (YearAndWeek, Seq[CalendarEvent]) = {
      val week = YearAndWeek.from(beginningOfEpoch.plusWeeks(position))
      val eventsOfWeek = loadEventsOf(week)
      (week, eventsOfWeek)
    }

    override def getItemId(position: Int): Long = getItem(position).hashCode

    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val wholeLineLayout : LinearLayout = new LinearLayout(activity)
      wholeLineLayout.setOrientation(LinearLayout.HORIZONTAL)

      val weekAndEvents = getItem(position)
      val yearAndWeek = weekAndEvents._1
      val eventsOfWeek = weekAndEvents._2
      val days = yearAndWeek.days

      var id = 1
      val dayViews = days.map { d =>
        val hasEvents = eventsOfWeek.exists { _.isDuring(d) }
        val dayView = new TextView(activity)
        dayView.setPadding(5, 5, 5, 5)
        val fmt = DateTimeFormat.forPattern("E")
        val weekDayNameInitial = fmt.print(d).substring(0, 1)
        dayView.setText(weekDayNameInitial)
        dayView.setTextColor(Color.WHITE)
        dayView.setBackgroundColor(if (hasEvents) Color.LTGRAY else Color.DKGRAY)
        dayView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20)

        dayView.setId(id)
        id = id + 1
        dayView
      }

      val weekNumberView = new TextView(activity)
      weekNumberView.setId(id)
      weekNumberView.setPadding(5, 5, 5, 5)
      weekNumberView.setText(yearAndWeek.week + " / " + yearAndWeek.year)
      weekNumberView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20)
      wholeLineLayout.addView(weekNumberView)

      dayViews.foreach { dayView => wholeLineLayout.addView(dayView) }

      wholeLineLayout.getRootView
    }

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
    if (cursor.moveToFirst()) {
      do {
        val title = cursor.getString(1)
        val start = cursor.getLong(2)
        val end = cursor.getLong(3)
        val description = cursor.getString(4)
        results += new CalendarEvent(title, start, end, description)
      } while (cursor.moveToNext())
      results
    } else Nil
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    setListAdapter(adapter)
  }

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)
    getListView.setOnScrollListener(this)
    setSelection(adapter.positionOfNow)
  }

  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    Log.d(AllaccaSpike.TAG, getClass.getSimpleName + " got a click with position == " + position + " , id == " + id)
    val clickedThing = adapter.getItem(position)
    val content: String = clickedThing._1 + "\n\t" + clickedThing._2.mkString("\n\t")
    getActivity.findViewById(R.id.obd_selected).asInstanceOf[TextView].setText(content)
  }

  def onScrollChanged(): Unit = Log.d(AllaccaSpike.TAG, getClass.getSimpleName + " onScrollChanged")

  def onScrollStateChanged(p1: AbsListView, p2: Int): Unit = Log.d(AllaccaSpike.TAG, getClass.getSimpleName + s" onScrollStateChanged $p2 $p1")

  def onScroll(p1: AbsListView, p2: Int, p3: Int, p4: Int): Unit = Log.d(AllaccaSpike.TAG, getClass.getSimpleName + s" onScroll $p2 $p3 $p4 $p1")
}

class EventsScrollView(context: Context, attrs: AttributeSet) extends ScrollView(context, attrs) {
  override protected def onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int): Unit = {
    super.onScrollChanged(l, t, oldl, oldt)
    Log.d(AllaccaSpike.TAG, getClass.getSimpleName + s" scrolling with $l $t $oldl $oldt")
  }
}
