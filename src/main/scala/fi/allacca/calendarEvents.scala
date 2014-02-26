package fi.allacca

import android.provider.CalendarContract.{Calendars, Events}
import android.content.ContentValues
import android.content.Context
import org.joda.time.{Interval, DateTime}
import android.database.Cursor

class Calendar(val id: Long, val name: String) {
  override def toString = name
}

class CalendarEvent(val title: String, val startTime: Long, val endTime: Long, val description: String = "", val location: String = "", val allDay: Boolean = false) {
  def isDuring(day: DateTime): Boolean = {
    val effectiveEnd = if (endTime < startTime) java.lang.Long.MAX_VALUE else endTime
    val intervalOfEvent = new Interval(startTime, effectiveEnd)
    val intervalOfDay = new Interval(day.withTimeAtStartOfDay, day.withTimeAtStartOfDay.plusDays(1))
    intervalOfDay.overlaps(intervalOfEvent)
  }
  override def toString = s"$title ($description) $startTime - $endTime"
}

class CalendarEventService(context: Context) {

  def createEvent(calendarId: Long, event: CalendarEvent): Long = {
    val values = new ContentValues()
    values.put("calendar_id", Long.box(calendarId))
    fillCommonFields(values, event)

    //TODO:
    //Move these to fillCommonFields when it's safe: when edit can prefill and edit these vals
    values.put("eventLocation", event.location)
    values.put("description", event.description)
    //END_TODO

    values.put("eventTimezone", "Europe/Berlin")
    values.put("selfAttendeeStatus", Int.box(1))
    val allDay = if (event.allDay) 1 else 0
    values.put("allDay", Int.box(allDay))
    values.put("organizer", "some.mail@some.address.com")
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

  private def fillCommonFields(values: ContentValues, event: CalendarEvent) {
    values.put("dtstart", Long.box(event.startTime))
    values.put("dtend", Long.box(event.endTime))
    values.put("title", event.title)
  }

  def getCalendars: Array[Calendar] = {
    def getCalendars0(calCursor: Cursor, calendars: Array[Calendar]): Array[Calendar] = {
      val id = calCursor.getLong(0)
      val displayName = calCursor.getString(1)
      val currCalendar = new Calendar(id, displayName)
      val newCalendars = calendars :+ currCalendar
      if (calCursor.moveToNext()) getCalendars0(calCursor, newCalendars) else newCalendars
    }
    val queryCols = Array[String] ("_id", Calendars.NAME)
    val calCursor: Cursor = context.getContentResolver.query(Calendars.CONTENT_URI, queryCols, "visible" + " = 1", null, "_id" + " ASC")
    calCursor.moveToFirst()
    getCalendars0(calCursor, Array())
  }

}
