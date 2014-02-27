package fi.allacca

import android.app.{Activity, LoaderManager}
import android.database.Cursor
import android.widget._
import scala.Array
import android.os.Bundle
import android.content.{Intent, CursorLoader, ContentUris, Loader}
import android.provider.CalendarContract
import android.util.Log
import android.view.ViewGroup.LayoutParams
import org.joda.time.{DateTime, LocalDate}
import scala.annotation.tailrec
import org.joda.time.format.DateTimeFormat
import android.graphics.Color
import android.view.View
import java.util.concurrent.atomic.AtomicBoolean

class AgendaView(activity: Activity, parent: LinearLayout) extends ScrollView(activity) {
  private val creator = new AgendaCreator(activity, parent)

  override def onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
    val yCoordinateWhenAtBottom = getChildAt(0).getHeight - getHeight
//    Log.d(TAG, s"left == $left , top == $top , oldLeft == $oldLeft, oldTop == $oldTop, getHeight == $getHeight, childHeight = ${getChildAt(0).getHeight}")
    if (top <= 0) {
      creator.onTopReached()
    } else if (top >= (yCoordinateWhenAtBottom - creator.verticalViewportPadding)) {
      creator.onBottomReached(top)
    }
    super.onScrollChanged(left, top, oldLeft, oldTop)
  }
}

object EventsLoaderFactory {
  val columnsToSelect = Array("_id", "title", "begin", "end")

  def createLoader(activity: Activity, start: LocalDate, end: LocalDate): CursorLoader = {
    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon
    ContentUris.appendId(builder, start)
    ContentUris.appendId(builder, end)
    new CursorLoader(activity, builder.build, columnsToSelect, "", null, "begin asc")
  }
}

class AgendaCreator(activity: Activity, parent: LinearLayout) extends LoaderManager.LoaderCallbacks[Cursor] {
  private val ids = new IdGenerator(parent.getId + 100)
  private lazy val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)

  /**
   * When scrolling, how many days to look forward for new events
   */
  private val daysToLoadInAdvance = 15
  private var displayRange: (LocalDate, LocalDate) = (new LocalDate(), new LocalDate().plusDays(daysToLoadInAdvance * 2))
  private val futureEventsLoadingInProgress: AtomicBoolean = new AtomicBoolean(false)
  private val BASIC_LOADER_ID = 0
  private val FUTURE_LOADER_ID = 1

  /**
   * How much hidden content we want to display above and below viewable part
   */
  val verticalViewportPadding = activity.getResources.getDisplayMetrics.heightPixels

  private val DAYVIEW_TAG_ID = R.id.dayViewTagId

  activity.getLoaderManager.initLoader(BASIC_LOADER_ID, null, this)
  activity.getLoaderManager.getLoader(BASIC_LOADER_ID).forceLoad()

  override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    EventsLoaderFactory.createLoader(activity, displayRange._1, displayRange._2)
  }

  override def onLoadFinished(loader: Loader[Cursor], data: Cursor) { appendEvents(data) }

  private def appendEvents(cursor: Cursor) {
    val startedAt = System.currentTimeMillis()
    val dataLoaded = cursor.moveToFirst()
    val events = if (dataLoaded) readEvents(cursor) else Nil
    val eventsByDays: Map[LocalDate, Seq[CalendarEvent]] = events.groupBy {
      e => new DateTime(e.startTime).toLocalDate
    }
    val daysInOrder = eventsByDays.keys.toSeq.sortBy(_.toDateTimeAtCurrentTime.getMillis)
    Log.d(TAG, "daysInOrder == " + daysInOrder)
    daysInOrder.foreach {
      day =>
        val dayNameView = new TextView(activity)
        dayNameView.setId(ids.nextId)
        val dayNameParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        dayNameView.setLayoutParams(dayNameParams)
        dayNameView.setBackgroundColor(dimensions.pavlova)
        dayNameView.setTextColor(Color.BLACK)
        dayNameView.setTextSize(dimensions.overviewContentTextSize)
        val dateFormat = DateTimeFormat.forPattern("d.M.yyyy")
        dayNameView.setText(dateFormat.print(day))
        activity.runOnUiThread({ parent.addView(dayNameView) })
        val eventsOfDay = events.filter { _.isDuring(day.toDateTimeAtStartOfDay) } sortBy { _.startTime }

        val dayWithEvents = DayWithEvents(day, eventsOfDay)
        dayNameView.setTag(DAYVIEW_TAG_ID, dayWithEvents)

        eventsOfDay foreach {
          event =>
            val titleView = new TextView(activity)
            titleView.setId(ids.nextId)
            val params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            titleView.setLayoutParams(params)
            titleView.setTextSize(dimensions.overviewContentTextSize)
            titleView.setText(event.title)
            activity.runOnUiThread({ parent.addView(titleView) })
            val onClick: (View => Unit) = { _ =>
              Log.i(TAG, "event clicked, starting activity")
              val intent = new Intent(activity, classOf[EditEventActivity])
              intent.putExtra(EVENT_ID, event.id.get)
              activity.startActivity(intent)
              Log.i(TAG, "After start activity")
            }
            titleView.setOnClickListener(onClick)
        }
    }
    Log.d(TAG, "Rendering took " + (System.currentTimeMillis() - startedAt) + " milliseconds.")
  }

  def onTopReached() {
    Log.d(TAG, "We have scrolled to top and need to load more things of past")
    displayRange = (displayRange._1.minusDays(20), displayRange._2.minusDays(20))
    parent.removeAllViews()
    activity.getLoaderManager.restartLoader(BASIC_LOADER_ID, null, this)
  }

  def onBottomReached(topCoordinate: Int) {
    val startedAt = System.currentTimeMillis()
    Log.d(TAG, "We have scrolled to bottom and need to load more things of future")
    if (futureEventsLoadingInProgress.getAndSet(true)) {
      Log.d(TAG, "...but load is already in progress, so let's not mess it up.")
      return
    }

    new Thread(new Runnable() {
      override def run() {
        val dayViews: Seq[View] = loopChildren[View](identity).filter { _.getTag(DAYVIEW_TAG_ID) != null }
        def shouldBeRemoved(v: View): Boolean = v.getY < (topCoordinate - verticalViewportPadding)
        val dayViewsThatShouldGo = dayViews.filter(shouldBeRemoved)
        val newRangeStart = if (!dayViewsThatShouldGo.isEmpty) {
          val lastDayToGo = dayViewsThatShouldGo.last.getTag(DAYVIEW_TAG_ID).asInstanceOf[DayWithEvents].day
          lastDayToGo.plusDays(1)
        } else {
          displayRange._1
        }

        if (parent.getChildCount > 0) {
          loopChildren(v => removeInvisiblePast(topCoordinate, v))
        }

        val lastDayCurrentlyDisplayed: Option[LocalDate] = dayViews.lastOption.map { dayView =>
          val dayWithEvents = dayView.getTag(DAYVIEW_TAG_ID).asInstanceOf[DayWithEvents]
          dayWithEvents.day
        }

        val newRangeEnd: LocalDate = lastDayCurrentlyDisplayed match {
          case Some(lastDay) => new LocalDate(Math.max(lastDay.plusDays(daysToLoadInAdvance), displayRange._2.plusDays(daysToLoadInAdvance)))
          case None => displayRange._2.plusDays(daysToLoadInAdvance)
        }

        val dayToStartFutureLoading = lastDayCurrentlyDisplayed match {
          case Some(lastDay) => lastDay.plusDays(1)
          case None => newRangeEnd
        }

        val futureEventsHandler: Cursor => Unit = { cursor =>
          appendEvents(cursor)
          activity.getLoaderManager.destroyLoader(FUTURE_LOADER_ID)
          futureEventsLoadingInProgress.set(false)
          Log.d(TAG, "Loading and rendering took " + (System.currentTimeMillis() - startedAt) + " milliseconds")
        }

        activity.runOnUiThread(new Runnable() { override def run() {
          val futureEventsLoader = new FutureEventsLoader(activity, dayToStartFutureLoading, dayToStartFutureLoading.plusDays(daysToLoadInAdvance), futureEventsHandler)
          activity.getLoaderManager.initLoader(FUTURE_LOADER_ID, null, futureEventsLoader)
          activity.getLoaderManager.getLoader(FUTURE_LOADER_ID).forceLoad()
          displayRange = (newRangeStart, newRangeEnd)
        }})
      }
    }).start()
  }

  private def isTooFarInPast(topCoordinate: Int, v: View): Boolean = v.getY < (topCoordinate - verticalViewportPadding)

  private def removeInvisiblePast(topCoordinate: Int, v: View) {
    if (isTooFarInPast(topCoordinate, v)) {
      val heightLoss = v.getHeight
      val scrollView = parent.getParent.asInstanceOf[ScrollView]
      Log.d(TAG, "removing " + v.asInstanceOf[TextView].getText)
      activity.runOnUiThread(new Runnable() {
        override def run() {
          parent.removeView(v)
          scrollView.setScrollY(scrollView.getScrollY - heightLoss)
        }
      })
    }
  }

  @tailrec
  private def loopChildren[T](operation: (View => T), index: Int = 0, acc: Seq[T] = Nil): Seq[T] = {
    val result = acc :+ operation(parent.getChildAt(index))
    val nextIndex = index + 1
    if (nextIndex >= parent.getChildCount) {
      result
    } else {
      loopChildren(operation, nextIndex, result)
    }
  }

  @tailrec
  private def readEvents(cursor: Cursor, events: Seq[CalendarEvent] = Nil): Seq[CalendarEvent] = {
    val newEvents = events :+ readEventFrom(cursor)
    if (!cursor.moveToNext()) {
      newEvents
    } else readEvents(cursor, newEvents)
  }

  private def readEventFrom(cursor: Cursor): CalendarEvent = {
    new CalendarEvent(id = Some(cursor.getLong(0)), title = cursor.getString(1), startTime = cursor.getLong(2), endTime = cursor.getLong(3))
  }

  override def onLoaderReset(loader: Loader[Cursor]) {}
}

class FutureEventsLoader(activity: Activity, start: LocalDate, end: LocalDate, onFinished: (Cursor => Unit)) extends LoaderManager.LoaderCallbacks[Cursor] {
  override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    Log.d(TAG, "Initiating loading of future events for " + start + " -- " + end)
    EventsLoaderFactory.createLoader(activity, start, end)
  }

  override def onLoadFinished(loader: Loader[Cursor], data: Cursor) {
    onFinished(data)
 }

  override def onLoaderReset(loader: Loader[Cursor]) {}
}