package fi.allacca

import android.app._
import android.content.Context
import android.os.Bundle
import android.widget._
import android.view.{ViewGroup, View}
import android.util.{AttributeSet, Log}
import android.widget.AbsListView.OnScrollListener
import android.view.ViewTreeObserver.OnScrollChangedListener
import fi.allacca.dates.YearAndWeek
import org.joda.time.{Weeks, DateTime}

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

    override def getItem(position: Int): YearAndWeek = {
      YearAndWeek.from(beginningOfEpoch.plusWeeks(position))
    }

    override def getItemId(position: Int): Long = getItem(position).hashCode

    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val view = new TextView(activity)
      view.setText(getItem(position).toString)
      view
    }
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
    getActivity.findViewById(R.id.obd_selected).asInstanceOf[TextView].setText(clickedThing.toString + clickedThing.firstDay + " - " + clickedThing.lastDay)
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
