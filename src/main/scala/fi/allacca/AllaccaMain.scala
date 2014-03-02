package fi.allacca

import android.app._
import android.os.Bundle
import android.widget._
import android.view.{View, ViewGroup}
import android.graphics.{Point, Color}
import android.view.ViewGroup.LayoutParams
import android.util.Log
import java.text.DateFormatSymbols
import java.util.{Locale, Calendar}
import android.content.Intent
import fi.allacca._


class AllaccaMain extends Activity with TypedViewHolder {
  private lazy val dimensions = new ScreenParameters(getResources.getDisplayMetrics)
  private lazy val weeksList = new ListView(this)
  private lazy val weeksAdapter = new WeeksAdapter(this, dimensions)
  private val idGenerator = new IdGenerator

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    val mainLayout = createMainLayout

    val cornerView = createTopLeftCornerView
    mainLayout.addView(cornerView)

    val titles = createDayColumnTitles()
    titles.foreach { mainLayout.addView }

    val weeksList = createWeeksList(cornerView)
    mainLayout.addView(weeksList)

    createAgenda(mainLayout)

    val addEventButton = addAEventButton(mainLayout)
    addGotoNowButton(mainLayout, addEventButton.getId)

    setContentView(mainLayout)
  }


  def createMainLayout: RelativeLayout = {
    val mainLayout = new RelativeLayout(this)
    val mainLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    mainLayout.setLayoutParams(mainLayoutParams)
    mainLayout
  }

  private def createTopLeftCornerView: View = {
    val cornerView = new TextView(this)
    cornerView.setId(idGenerator.nextId)
    cornerView.setText("Hello")
    cornerView.setWidth(dimensions.weekNumberWidth)
    cornerView.setHeight(dimensions.weekRowHeight)
    cornerView
  }

  def createWeeksList(cornerView: View): View = {
    weeksList.setId(idGenerator.nextId)
    weeksList.setAdapter(weeksAdapter)
    weeksList.setSelection(weeksAdapter.positionOfNow)
    val weeksListParams = new RelativeLayout.LayoutParams(dimensions.weekListWidth, LayoutParams.WRAP_CONTENT)
    weeksListParams.addRule(RelativeLayout.BELOW, cornerView.getId)
    weeksListParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
    weeksList.setLayoutParams(weeksListParams)
    weeksList
  }

  private def screenSize: Point = {
    val display = getWindowManager.getDefaultDisplay
    val size = new Point()
    display.getSize(size)
    size
  }

  def createAgenda(mainLayout: RelativeLayout): Unit = {
    val agendaLayout = new LinearLayout(this)
    agendaLayout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    agendaLayout.setOrientation(LinearLayout.VERTICAL)
    agendaLayout.setId(idGenerator.nextId)
    val agendaView = new AgendaView(this, agendaLayout)

    val scrollParams = new RelativeLayout.LayoutParams(screenSize.x - weeksList.getWidth, LayoutParams.MATCH_PARENT)
    scrollParams.addRule(RelativeLayout.RIGHT_OF, weeksList.getId)
    agendaView.setLayoutParams(scrollParams)
    agendaView.setId(idGenerator.nextId)

    agendaView.addView(agendaLayout)
    mainLayout.addView(agendaView)
  }

  private def addAEventButton(layout: ViewGroup): Button = {
    val b = new Button(this)
    b.setId(idGenerator.nextId)
    val params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
    b.setLayoutParams(params)
    b.setText("+")
    b.setTextColor(Color.WHITE)
    b.setOnClickListener(createNewEvent _)
    layout.addView(b)
    b
  }

  def createNewEvent (view: View) {
    Log.d(TAG, "+ createNewEvent")
    val intent = new Intent(this, classOf[EditEventActivity])

    //This will create the new event after ten days:
    //intent.putExtra(EVENT_DATE, new DateTime().plusDays(10).toDate.getTime)

    startActivityForResult(intent, REQUEST_CODE_EDIT_EVENT)
  }

  private def addGotoNowButton(layout: ViewGroup, leftSideId: Int) {
    val b = new Button(this)
    b.setId(idGenerator.nextId)
    val params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
    params.addRule(RelativeLayout.RIGHT_OF, leftSideId)
    b.setLayoutParams(params)
    b.setText("Now")
    b.setTextColor(Color.WHITE)
    b.setOnClickListener(gotoNow _)
    layout.addView(b)
  }

  def gotoNow(view: View) {
    weeksList.smoothScrollToPosition(weeksAdapter.positionOfNow)
  }

  private def createDayColumnTitles(): Seq[View] = {
    val shortWeekDays = new DateFormatSymbols(Locale.ENGLISH).getShortWeekdays
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
      view.setId(idGenerator.nextId)
      view.setWidth(dimensions.dayColumnWidth)
      view.setHeight(dimensions.weekRowHeight)
      val layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
      layoutParams.addRule(RelativeLayout.RIGHT_OF, view.getId - 1)
      view.setLayoutParams(layoutParams)
      view.setTextSize(dimensions.overviewHeaderTextSize)
      view.setText(c)
      view
    }
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    Log.i(TAG, s"onActivityResult requestCode $requestCode resultCode $resultCode")
    if (requestCode == REQUEST_CODE_EDIT_EVENT && resultCode == Activity.RESULT_OK) refresh
  }

  private def refresh {
    Log.i(TAG, "Refreshing main view")
    val intent = getIntent
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    finish()
    startActivity(intent)
  }

}

