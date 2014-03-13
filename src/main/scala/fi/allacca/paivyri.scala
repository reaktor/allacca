package fi.allacca

import android.app.{ProgressDialog, Activity}
import android.database.Cursor
import android.widget._
import scala.Array
import android.os.Bundle
import android.content.{Intent, CursorLoader, ContentUris, Loader}
import android.provider.CalendarContract
import android.util.Log
import android.view.ViewGroup.LayoutParams
import org.joda.time.{Days, Years, DateTime, LocalDate}
import scala.annotation.tailrec
import org.joda.time.format.DateTimeFormat
import android.graphics.Color
import android.view.{ViewGroup, View}
import android.provider.CalendarContract.Instances
import android.app.LoaderManager.LoaderCallbacks
import scala.Some
import scala.collection.mutable
import java.util.Date
import android.widget.AbsListView.OnScrollListener
import android.graphics.drawable.GradientDrawable
import java.util.concurrent.atomic.AtomicBoolean

class PaivyriView(activity: Activity, statusTextView: TextView) extends ListView(activity) {
  /**
   * Approximation of how many items (day + events) can fit on the screen.
   */
  private lazy val rowsVisibleAtTime: Int = (activity.getResources.getDisplayMetrics.heightPixels.toFloat / dimensions.overviewHeaderTextSize).toInt
  /**
   * How much off-screen content we want to maintain loaded to facilitate scrolling
   */
  private lazy val verticalViewPortPadding: Int = rowsVisibleAtTime / 2

  private lazy val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)
  private val adapter = new PaivyriAdapter(activity, this, statusTextView)

  def start() {
    setAdapter(adapter)
    focusOn(new LocalDate)
    setOnScrollListener(new OnScrollListener {
      def onScrollStateChanged(view: AbsListView, scrollState: Int) {
        Log.d(TAG + PaivyriView.this.getClass.getSimpleName, s"scrollState==$scrollState")
      }

      def onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
        Log.d(TAG + PaivyriView.this.getClass.getSimpleName, s"onScroll: $firstVisibleItem , $visibleItemCount, $totalItemCount")
        if (firstVisibleItem == 0) {
          adapter.loadMorePast()
        }
        val lastVisibleItem = firstVisibleItem + visibleItemCount
        if (lastVisibleItem >= (adapter.getCount - rowsVisibleAtTime)) {
          adapter.loadMoreFuture()
        }
      }
    })
  }

  def focusOn(day: LocalDate) {
    adapter.focusOn(day)
  }
}

class PaivyriAdapter(activity: Activity, listView: PaivyriView, statusTextView: TextView) extends BaseAdapter with LoaderCallbacks[Cursor] {
  private val loadWindowLock = new Object {
    def foobarize[A,B](foo: A => B)(a: A): B = foo(a)
  }
  private val DAYVIEW_TAG_ID = R.id.dayViewTagId
  private val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)
  private val renderer = new PaivyriRenderer(activity)
  private val model = new PaivyriModel

  private val howManyDaysToLoadAtTime = 30

  private var loading = new AtomicBoolean(false)
  @volatile private var focusDay = new LocalDate
  @volatile private var firstDayToLoad = focusDay.minusDays(howManyDaysToLoadAtTime)
  @volatile private var lastDayToLoad = focusDay.plusDays(howManyDaysToLoadAtTime)
  @volatile private var setSelectionToFocusDayAfterLoading = false

  override def getCount: Int = model.size

  override def getItem(position: Int): Option[DayWithEvents] = model.getItemFromContents(position)

  override def getItemId(position: Int): Long = getItem(position).map { _.id }.getOrElse(-1)

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val item = getItem(position)
    if (convertView != null && item.isDefined && convertView.getTag(DAYVIEW_TAG_ID).asInstanceOf[Long] == item.get.id) {
      convertView
    } else {
      renderer.createLoadingOrRealViewFor(item)
    }
  }

  def focusOn(day: LocalDate) {
    resetLoadingWindowTo(day)
    setSelectionToFocusDayAfterLoading = true
    triggerLoading()
  }

  private def resetLoadingWindowTo(day: LocalDate) {
    loadWindowLock.synchronized {
      focusDay = day
      firstDayToLoad = focusDay.minusDays(howManyDaysToLoadAtTime)
      lastDayToLoad = focusDay.plusDays(howManyDaysToLoadAtTime)
    }
  }

  private def triggerLoading() {
    if (loading.getAndSet(true)) {
      return
    }
    statusTextView.setText("Loading...")
    val args = new Bundle
    args.putLong("start", firstDayToLoad)
    args.putLong("end", lastDayToLoad)
    activity.getLoaderManager.initLoader(19, args, this)
  }

  def loadMorePast() {
    if (focusDay == model.firstDay) {
      firstDayToLoad = firstDayToLoad.minusDays(howManyDaysToLoadAtTime)
      setSelectionToFocusDayAfterLoading = true
      triggerLoading()
    } else {
      focusOn(model.firstDay)
    }
  }

  def loadMoreFuture() {
    Log.d(TAG, "Gotta load more future")
  }

  override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon
    ContentUris.appendId(uriBuilder, args.get("start").asInstanceOf[Long])
    ContentUris.appendId(uriBuilder, args.get("end").asInstanceOf[Long])
    val loader = EventsLoaderFactory.createLoader(activity)
    loader.setUri(uriBuilder.build)
    loader
  }

  override def onLoadFinished(loader: Loader[Cursor], cursor: Cursor) {
    Log.d(TAG, "Finished")

    val events = EventsLoaderFactory.readEvents(cursor)
    val eventsByDays = events.groupBy { e => new DateTime(e.startTime).withTimeAtStartOfDay.toLocalDate }
    val days = (eventsByDays.keys.toSet + focusDay).toList.sortBy { _.toDate }
    days.foreach { day =>
      val eventsOfDay = events.filter { _.isDuring(day.toDateTimeAtStartOfDay) } sortBy { _.startTime }
      val dayWithEvents = DayWithEvents(day, eventsOfDay)
      model.addOrUpdate(dayWithEvents)
    }
    notifyDataSetChanged()
    statusTextView.setText("")
    loadWindowLock.synchronized {
      if (setSelectionToFocusDayAfterLoading) {
        listView.setSelection(model.indexOf(focusDay))
        setSelectionToFocusDayAfterLoading = false
      }
    }
    loading.set(false)
    activity.getLoaderManager.destroyLoader(19) // This makes onCreateLoader run again and use fresh search URI
/*
    model.rollWindow()
    if (!model.hasEnoughContentCountingFrom(focusDay)) {
      Log.d(TAG, "Got to load more")
      progressDialog.setMessage(model.currentRange.toString().replace(",", " -- "))
      loadBatch()
    } else {
      loading = false
      progressDialog.dismiss()
      onFinished(Unit)
    }*/
  }

  override def onLoaderReset(loader: Loader[Cursor]) {}
}

class PaivyriRenderer(activity: Activity) {
  private val DAYVIEW_TAG_ID = R.id.dayViewTagId
  private val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)

  def createLoadingOrRealViewFor(content: Option[DayWithEvents]): View = {
    val view: View = content match {
      case None =>
        val pendingView = new TextView(activity)
        pendingView.setText("Loading")
        pendingView
      case Some(dayWithEvents) => createDayView(dayWithEvents)
    }
    view.setId(View.generateViewId())
    val dayViewParams = new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    view.setLayoutParams(dayViewParams)
    view
  }

  private def createDayView(dayWithEvents: DayWithEvents): View = {
    Log.d(TAG, dayWithEvents.toString)
    val dayView = new LinearLayout(activity)
    dayView.setOrientation(LinearLayout.VERTICAL)

    val dayNameView = new TextView(activity)
    dayNameView.setId(View.generateViewId())
    val dayNameParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    dayNameView.setLayoutParams(dayNameParams)
    dayNameView.setTextSize(dimensions.overviewContentTextSize)
    val dateFormat = DateTimeFormat.forPattern("d.M.yyyy")
    val day = dayWithEvents.day
    dayNameView.setText(dateFormat.print(day))
    dayView.addView(dayNameView)

    val eventsOfDay = dayWithEvents.events

    eventsOfDay foreach {
      event =>
        val titleView = new TextView(activity)
        titleView.setId(View.generateViewId())
        val params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        titleView.setLayoutParams(params)
        titleView.setTextSize(dimensions.overviewContentTextSize)
        titleView.setText(event.title)
        titleView.setBackgroundColor(dimensions.pavlova)
        titleView.setTextColor(Color.BLACK)
        activity.runOnUiThread(dayView.addView(titleView))
        val onClick: (View => Unit) = {
          _ =>
            Log.i(TAG, "event clicked, starting activity")
            val intent = new Intent(activity, classOf[EditEventActivity])
            intent.putExtra(EVENT_ID, event.id.get)
            activity.startActivityForResult(intent, REQUEST_CODE_EDIT_EVENT)
            Log.i(TAG, "After start activity")
        }
        titleView.setOnClickListener(onClick)
    }
    dayView.setTag(DAYVIEW_TAG_ID, dayWithEvents.id)
    dayView
  }
}

class PaivyriModel {
  @volatile
  private var contents = mutable.ListBuffer[DayWithEvents]()

  def size = synchronized { contents.size }

  def firstDay: LocalDate = synchronized { contents.headOption.map { _.day }.getOrElse(new LocalDate) }
  def lastDay: LocalDate = synchronized { contents.lastOption.map { _.day }.getOrElse(new LocalDate) }

  def getItemFromContents(index: Int): Option[DayWithEvents] = synchronized { contents.lift(index) }

  def indexOf(day: LocalDate) = synchronized {
    findFromContents { _.day == day } match {
      case Some(dayWithEvents) => contents.indexOf(dayWithEvents)
      case None => -1
    }
  }

  def addOrUpdate(dwe: DayWithEvents) {
    findFromContents { _.day == dwe.day } match {
      case None => add(dwe)
      case Some(oldDwe) =>
        if (oldDwe.events != dwe.events) {
          removeFromContents(oldDwe)
          add(dwe)
        }
    }
  }

  private def add(dwe: DayWithEvents) {
    synchronized {
      contents.append(dwe)
      contents = contents.sortBy { dwe => dwe.day.toDate }
    }
  }

  def removeFromContents(oldDwe: DayWithEvents) { synchronized { contents -= oldDwe } }

  def findFromContents(p: DayWithEvents => Boolean): Option[DayWithEvents] = synchronized { contents.find(p) }
}
