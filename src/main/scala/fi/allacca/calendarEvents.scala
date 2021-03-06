package fi.allacca

import android.provider.CalendarContract.{Instances, Calendars, Events}
import android.content.{CursorLoader, ContentValues, Context}
import org.joda.time._
import android.database.Cursor
import org.joda.time.format.DateTimeFormat
import java.util.TimeZone
import scala.annotation.tailrec
import android.app.Activity
import scala.collection.mutable

class UserCalendar(val id: Long, val name: String) {
  override def toString = name
}

class CalendarEvent(val id: Option[Long], val title: String,
                    val startTime: DateTime, val endTime: DateTime, val description: String = "",
                    val location: String = "", val allDay: Boolean = false) {
  def isDuring(day: DateTime): Boolean = {
    val effectiveEnd = if (endTime.isBefore(startTime)) new DateTime(java.lang.Long.MAX_VALUE) else endTime
    val intervalOfEvent = new Interval(startTime, effectiveEnd)
    val intervalOfDay = new Interval(day.withTimeAtStartOfDay, day.withTimeAtStartOfDay.plusDays(1))
    intervalOfDay.overlaps(intervalOfEvent)
  }

  def days: Seq[LocalDate] = {
    if (spansMultipleDays) dayRange else List(toDayStart(startTime))
  }

  private def dayRange: Seq[LocalDate] = {
    val startDay = toDayStart(startTime)
    val endDay = toDayStart(endTime)
    val rangeLength = Days.daysBetween(startDay, endDay).getDays
    val daysToSubtract = if (allDay) 1 else 0
    val days = 0.to(rangeLength - daysToSubtract).map { startDay.plusDays }
    Logger.debug(s"$this days == $days")
    days
  }

  private def toDayStart(d: DateTime): LocalDate = d.withTimeAtStartOfDay.withZone(DateTimeZone.getDefault).toLocalDate

  def spansMultipleDays: Boolean = {
    val realEndDay = if (allDay) endTime.minusDays(1) else endTime
    startTime.withTimeAtStartOfDay != realEndDay.withTimeAtStartOfDay
  }

  override def toString = s"$title ($description) ${format(startTime)} - ${format(endTime)}"

  def detailedToString = {
    def detailFormat(d: DateTime) = format(d, "d.M.yyyy HH:mm:SSS Z")
    s"$title ${detailFormat(startTime)} - ${detailFormat(endTime)}"
  }

  private def format(dateTime: DateTime, formatPattern: String = "d.M.yyyy HH:mm"): String = DateTimeFormat.forPattern(formatPattern).print(dateTime)
}

case class DayWithEvents(day: LocalDate, events: Seq[CalendarEvent]) {
  val id: Long = day
}

object DayWithEvents {
  implicit val DayWithEventsOrdering = Ordering.by { dwe: DayWithEvents => dwe.id }
}

class CalendarEventService(context: Context) {
  private val instanceColumnsToSelect = Array(Instances.EVENT_ID, "title", "begin", "end", "allDay")
  private val eventColumnsToSelect = Array("dtstart", "dtend", "title", "eventLocation", "description", "allDay")

  def createEvent(calendarId: Long, event: CalendarEvent): Long = {
    val values = new ContentValues()
    values.put("calendar_id", Long.box(calendarId))
    fillCommonFields(values, event)
    values.put("eventTimezone", TimeZone.getDefault.getID)
    values.put("selfAttendeeStatus", Int.box(1))
    values.put("guestsCanInviteOthers", Int.box(1))
    values.put("guestsCanModify", Int.box(1))
    values.put("availability", Int.box(0))
    val uri = context.getContentResolver.insert(Events.CONTENT_URI, values)
    val eventId = uri.getLastPathSegment.toLong
    eventId
  }

  def saveEvent(eventId: Long, event: CalendarEvent): Int = {
    val values = new ContentValues()
    fillCommonFields(values, event)
    context.getContentResolver.update(Events.CONTENT_URI, values, "_id =? ", Array(eventId.toString))
  }

  def deleteEvent(eventId: Long): Int = {
    context.getContentResolver.delete(Events.CONTENT_URI, "_id =? ", Array(eventId.toString))
  }

  def getEvent(eventId: Long): Option[CalendarEvent] = {
    Logger.debug(s"Loading $eventId")
    val cursor = context.getContentResolver.query(Events.CONTENT_URI, eventColumnsToSelect, "_id =? ", Array(eventId.toString), null)
    if (cursor.moveToFirst()) {
      val startTime = cursor.getLong(0)
      val endTime = cursor.getLong(1)
      val title = cursor.getString(2)
      val location = cursor.getString(3)
      val description = cursor.getString(4)
      val allDay = cursor.getInt(5) == 1
      val timeZone = timeZoneForEvent(allDay)
      val event = new CalendarEvent(id = Some(eventId),
        title = title,
        startTime = new DateTime(startTime).withZone(timeZone),
        endTime = new DateTime(endTime).withZone(timeZone),
        location = location,
        description = description,
        allDay = allDay)
      Logger.debug(s"Found event $event (${event.detailedToString}})")
      Some(event)
    } else { None }
  }

  private def fillCommonFields(values: ContentValues, event: CalendarEvent) {
    values.put("dtstart", Long.box(event.startTime))
    values.put("dtend", Long.box(event.endTime))
    values.put("title", event.title)
    values.put("eventLocation", event.location)
    values.put("description", event.description)
    val allDay = if (event.allDay) 1 else 0
    values.put("allDay", Int.box(allDay))
  }

  def createInstanceLoader(activity: Activity): CursorLoader = {
    val loader = new CursorLoader(activity)
    loader.setProjection(instanceColumnsToSelect)
    loader.setSelection("")
    loader.setSelectionArgs(null)
    loader.setSortOrder("begin asc")
    loader
  }

  def readEventsByDays(cursor: Cursor): (Set[LocalDate], Set[DayWithEvents]) = {
    val events = time({ readEventsFromInstances(cursor) }, "readEvents")
    val eventsByDays: mutable.Map[LocalDate, Seq[CalendarEvent]] = new mutable.HashMap[LocalDate, Seq[CalendarEvent]]()
    time({
      events.foreach {
        e =>
          if (e != null) {
            val daysOfEvent: Seq[LocalDate] = e.days
            daysOfEvent.foreach { day =>
              val daysEventsOption: Option[Seq[CalendarEvent]] = eventsByDays.get(day)
              daysEventsOption match {
                case Some(eventList) => eventsByDays.put(day, eventList.+:(e))
                case None => eventsByDays.put(day, List(e))
              }
            }
          }
      }
    }, "groupBy")

    val days: Set[LocalDate] = time({ eventsByDays.keys.toSet }, "getDays")

    val daysWithEvents: Set[DayWithEvents] = time({
      days.map {
        day =>
          val eventsOfDay = eventsByDays.get(day).getOrElse(Nil).sortBy { _.startTime.getMillis }
          DayWithEvents(day, eventsOfDay)
      }
    }, "create daysWithEventsMap")
    (days, daysWithEvents)
  }

  def readEventsFromInstances(cursor: Cursor): Seq[CalendarEvent] = {
    def readSingleEvent: CalendarEvent = {
      val id = cursor.getLong(0)
      val title = cursor.getString(1)
      val startTime = cursor.getLong(2)
      val endTime = cursor.getLong(3)
      val allDayFromDb = cursor.getInt(4)
      val allDay = allDayFromDb == 1
      new CalendarEvent(id = Some(id), title = title,
        startTime = toDateTime(startTime, allDay), endTime = toDateTime(endTime, allDay), allDay = allDay)
    }

    val result = new Array[CalendarEvent](cursor.getCount)
    var i = 0
    while (cursor.moveToNext()) {
      result.update(i, readSingleEvent)
      i = i + 1
    }
    result.toSeq
  }

  private def toDateTime(epochMillis: Long, allDay: Boolean): DateTime = {
    val timeZone = timeZoneForEvent(allDay)
    val timeZoneDifference = if (timeZone != DateTimeZone.getDefault) TimeZone.getDefault.getOffset(epochMillis) else 0
    new DateTime(epochMillis + timeZoneDifference, timeZone)
  }

  def getCalendars: Array[UserCalendar] = {
    @tailrec
    def getCalendars0(calCursor: Cursor, calendars: Array[UserCalendar]): Array[UserCalendar] = {
      val id = calCursor.getLong(0)
      val displayName = calCursor.getString(1)
      val currCalendar = new UserCalendar(id, displayName)
      val newCalendars = calendars :+ currCalendar
      if (calCursor.moveToNext()) getCalendars0(calCursor, newCalendars) else newCalendars
    }
    val queryCols = Array[String] ("_id", Calendars.NAME)
    val calCursor: Cursor = context.getContentResolver.query(Calendars.CONTENT_URI, queryCols, "visible" + " = 1", null, "_id" + " ASC")
    calCursor.moveToFirst()
    getCalendars0(calCursor, Array())
  }
}
