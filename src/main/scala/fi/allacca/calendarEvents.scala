package fi.allacca

import android.provider.CalendarContract.{Calendars, Events}
import android.content.ContentValues
import android.content.Context
import org.joda.time.{LocalDate, Interval, DateTime}
import android.database.Cursor
import org.joda.time.format.DateTimeFormat
import android.util.Log
import java.util.TimeZone

class UserCalendar(val id: Long, val name: String) {
  override def toString = name
}

class CalendarEvent(val id: Option[Long], val title: String, val startTime: Long, val endTime: Long, val description: String = "", val location: String = "", val allDay: Boolean = false) {
  def isDuring(day: DateTime): Boolean = {
    val effectiveEnd = if (endTime < startTime) java.lang.Long.MAX_VALUE else endTime
    val intervalOfEvent = new Interval(startTime, effectiveEnd)
    val intervalOfDay = new Interval(day.withTimeAtStartOfDay, day.withTimeAtStartOfDay.plusDays(1))
    intervalOfDay.overlaps(intervalOfEvent)
  }
  override def toString = s"$title ($description) ${formatEpoch(startTime)} - ${formatEpoch(endTime)}"

  private def formatEpoch(epochMillis: Long): String = DateTimeFormat.forPattern("d.M.yyyy HH:mm").print(epochMillis)
}

case class DayWithEvents(day: LocalDate, events: Seq[CalendarEvent])

class CalendarEventService(context: Context) {

  def createEvent(calendarId: Long, event: CalendarEvent): Long = {
    val values = new ContentValues()
    values.put("calendar_id", Long.box(calendarId))
    fillCommonFields(values, event)
    values.put("eventTimezone", TimeZone.getDefault.getID)
    values.put("selfAttendeeStatus", Int.box(1))
    val allDay = if (event.allDay) 1 else 0
    values.put("allDay", Int.box(allDay))
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
    context.getContentResolver().update(Events.CONTENT_URI, values, "_id =? ", Array(eventId.toString))
  }

  def deleteEvent(eventId: Long): Int = {
    context.getContentResolver().delete(Events.CONTENT_URI, "_id =? ", Array(eventId.toString))
  }

  def getEvent(eventId: Long): Option[CalendarEvent] = {
    val projection = Array("dtstart", "dtend", "title", "eventLocation", "description")
    val cursor = context.getContentResolver().query(Events.CONTENT_URI, projection, "_id =? ", Array(eventId.toString), null)
    if (cursor.moveToFirst()) {
      val startTime = cursor.getLong(0)
      val endTime = cursor.getLong(1)
      val title = cursor.getString(2)
      val location = cursor.getString(3)
      val description = cursor.getString(4)
      Some(new CalendarEvent(id = Some(eventId), 
                             title = title, 
                             startTime = startTime, 
                             endTime = endTime, 
                             location = location, 
                             description = description))
    } else { None }
  }

  private def fillCommonFields(values: ContentValues, event: CalendarEvent) {
    values.put("dtstart", Long.box(event.startTime))
    values.put("dtend", Long.box(event.endTime))
    values.put("title", event.title)
    values.put("eventLocation", event.location)
    values.put("description", event.description)
  }

  def getCalendars: Array[UserCalendar] = {
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
