package fi.allacca

import android.app.{Activity, LoaderManager}
import android.database.Cursor
import android.widget._
import scala.Array
import android.os.Bundle
import android.content.{CursorLoader, ContentUris, Loader}
import android.provider.CalendarContract
import android.util.Log
import android.view.ViewGroup.LayoutParams
import org.joda.time.{DateTime, LocalDate}
import scala.annotation.tailrec
import org.joda.time.format.DateTimeFormat
import android.graphics.Color

class AgendaCreator(activity: Activity, parent: RelativeLayout) extends LoaderManager.LoaderCallbacks[Cursor] {
  private val ids = new IdGenerator(parent.getId + 1)
  private lazy val dimensions = new ScreenParameters(activity.getResources.getDisplayMetrics)
  private var displayRange: (LocalDate, LocalDate) = (new LocalDate(), new LocalDate().plusDays(20))

  activity.getLoaderManager.initLoader(0, null, this)
  activity.getLoaderManager.getLoader(0).forceLoad()

  override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon
    ContentUris.appendId(builder, displayRange._1)
    ContentUris.appendId(builder, displayRange._2)
    new CursorLoader(activity, builder.build, Array("_id", "title", "begin", "end"), "", null, "begin asc")
  }

  override def onLoadFinished(loader: Loader[Cursor], data: Cursor) {
    val dataLoaded = data.moveToFirst()
    val events = if (dataLoaded) readEvents(data) else Nil
    val eventsByDays: Map[LocalDate, Seq[CalendarEvent]] = events.groupBy { e => new DateTime(e.startTime).toLocalDate }
    val daysInOrder = eventsByDays.keys.toSeq.sortBy(_.toDateTimeAtCurrentTime.getMillis)
    Log.d(TAG, "daysInOrder == " + daysInOrder)
    daysInOrder.foreach { day =>
      val dayNameView = new TextView(activity)
      dayNameView.setId(ids.nextId)
      val dayNameParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
      dayNameParams.addRule(RelativeLayout.BELOW, dayNameView.getId - 1)
      dayNameView.setLayoutParams(dayNameParams)
      dayNameView.setBackgroundColor(dimensions.pavlova)
      dayNameView.setTextColor(Color.BLACK)
      dayNameView.setTextSize(dimensions.overviewContentTextSize)
      val dateFormat = DateTimeFormat.forPattern("d.M.yyyy")
      dayNameView.setText(dateFormat.print(day))
      parent.addView(dayNameView)
      events.filter { _.isDuring(day.toDateTimeAtStartOfDay) } sortBy { _.startTime } foreach { event =>
        Log.d(TAG, "Rendering " + event)
        val titleView = new TextView(activity)
        titleView.setId(ids.nextId)
        val params = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        params.addRule(RelativeLayout.BELOW, titleView.getId - 1)
        titleView.setLayoutParams(params)
        titleView.setTextSize(dimensions.overviewContentTextSize)
        titleView.setText(event.title)
        parent.addView(titleView)
      }
    }
  }

  def onTopReached() {
    Log.d(TAG, "We have scrolled to top and need to load more things of past")
    displayRange = (displayRange._1.minusDays(20), displayRange._2.minusDays(20))
    parent.removeAllViews()
    activity.getLoaderManager.restartLoader(0, null, this)

  }

  def onBottomReached() {
    Log.d(TAG, "We have scrolled to bottom and need to load more things of future")
    displayRange = (displayRange._1.plusDays(10), displayRange._2.plusDays(10))
    activity.getLoaderManager.restartLoader(0, null, this)
  }

  @tailrec
  private def readEvents(cursor: Cursor, events: Seq[CalendarEvent] = Nil): Seq[CalendarEvent] = {
    val newEvents = events :+ readEventFrom(cursor)
    if (!cursor.moveToNext()) {
      newEvents
    } else readEvents(cursor, newEvents)
  }

  private def readEventFrom(cursor: Cursor): CalendarEvent = {
    new CalendarEvent(title = cursor.getString(1), startTime = cursor.getLong(2), endTime = cursor.getLong(3))
  }

  override def onLoaderReset(loader: Loader[Cursor]) {}
}
