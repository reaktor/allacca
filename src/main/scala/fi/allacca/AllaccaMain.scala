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

class AllaccaMain extends Activity with TypedViewHolder {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    val textView = new TextView(this)
    textView.setId(1)
    textView.setText("Hello")
    setContentView(textView)
  }
}

