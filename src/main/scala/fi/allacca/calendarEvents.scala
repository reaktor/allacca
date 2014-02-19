package fi.allacca

import android.provider.CalendarContract.Events
import android.content.ContentValues
import android.content.Context
import org.joda.time.{Interval, DateTime}

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
    values.put("dtstart", Long.box(event.startTime))
    values.put("dtend", Long.box(event.endTime))
    //values.put("rrule", "FREQ=DAILY;COUNT=20;BYDAY=MO,TU,WE,TH,FR;WKST=MO")
//    values.put("rrule", "FREQ=DAILY;COUNT=1;BYDAY=MO,TU,WE,TH,FR;WKST=MO")
    values.put("title", event.title)
    values.put("eventLocation", event.location)
    values.put("calendar_id", Long.box(calendarId))
    values.put("eventTimezone", "Europe/Berlin")
    values.put("description", event.description)
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
}
