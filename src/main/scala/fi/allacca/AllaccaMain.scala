package fi.allacca

import android.app._
import android.os.Bundle
import android.widget._
import android.view.{View, ViewGroup}
import android.graphics.Color
import android.view.ViewGroup.LayoutParams
import android.util.Log
import java.text.DateFormatSymbols
import java.util.Calendar
import android.content.Intent


class AllaccaMain extends Activity with TypedViewHolder {
  private lazy val dimensions = new Dimensions(getResources.getDisplayMetrics)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    val cornerView = new TextView(this)
    cornerView.setId(1)
    cornerView.setText("Hello")
    cornerView.setWidth(dimensions.weekNumberWidth)
    cornerView.setHeight(dimensions.weekRowHeight)

    val mainLayout = new RelativeLayout(this)
    val mainLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    mainLayout.setLayoutParams(mainLayoutParams)
    mainLayout.addView(cornerView)

    val titles = createDayColumnTitles(cornerView.getId + 1)
    titles.foreach { mainLayout.addView }

    addAEventButton(mainLayout)

    setContentView(mainLayout)
  }

  private def addAEventButton(layout: ViewGroup) {
    val b = new Button(this)
    val params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
    b.setLayoutParams(params)
    b.setText("+")
    b.setTextColor(Color.WHITE)
    b.setOnClickListener(createNewEvent _)
    layout.addView(b)
  }

  def createNewEvent (view: View) {
    Log.d(TAG, "+ createNewEvent")
    val intent = new Intent(this, classOf[EditEventActivity])
    intent.putExtra(EVENT_ID, None) //We're creating a new event -> no ID yet
    startActivity(intent)
  }

  private def createDayColumnTitles(firstId: Int): Seq[View] = {
    var id = firstId
    val shortWeekDays = new DateFormatSymbols().getShortWeekdays
    val weekDayInitials = List(
      shortWeekDays(Calendar.MONDAY),
      shortWeekDays(Calendar.TUESDAY),
      shortWeekDays(Calendar.WEDNESDAY),
      shortWeekDays(Calendar.THURSDAY),
      shortWeekDays(Calendar.FRIDAY),
      shortWeekDays(Calendar.SATURDAY),
      shortWeekDays(Calendar.SUNDAY)
    ).map { _.charAt(0).toString }
    weekDayInitials.map { c =>
      val view = new TextView(this)
      view.setId(id)
      id = id + 1
      view.setWidth(dimensions.dayColumnWidth)
      view.setHeight(dimensions.weekRowHeight)
      val layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
      layoutParams.addRule(RelativeLayout.RIGHT_OF, view.getId - 1)
      view.setLayoutParams(layoutParams)
      view.setTextSize(30)
      view.setText(c)
      view
    }
  }
}

