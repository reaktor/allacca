package fi.allacca

import java.util.{Calendar, TimeZone, GregorianCalendar}
import android.provider.CalendarContract.Events
import android.content.ContentValues
import android.content.Context
/**
 * Deals with single events, no listing/mass stuff
 */
class CalendarEventService(val calendarId: Long, context: Context) {

  def createEvent(): Long = {
    val cal = new GregorianCalendar(2012, 11, 14)
    cal.setTimeZone(TimeZone.getTimeZone("UTC"))
    cal.set(Calendar.HOUR, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start: Long = cal.getTimeInMillis()
    val values = new ContentValues()
    values.put("dtstart", Long.box(start))
    values.put("dtend", start.asInstanceOf[Double]);
    values.put("rrule", "FREQ=DAILY;COUNT=20;BYDAY=MO,TU,WE,TH,FR;WKST=MO");
    values.put("title", "Some title");
    values.put("eventLocation", "Münster");
    values.put("calendar_id", Long.box(calendarId));
    values.put("eventTimezone", "Europe/Berlin");
    values.put("description", "The agenda or some description of the event")
    values.put("calendar_access_level", Int.box(2)) //2 = private
    values.put("selfAttendeeStatus", Int.box(1))
    values.put("allDay", Int.box(1))
    values.put("organizer", "some.mail@some.address.com")
    values.put("guestsCanInviteOthers", Int.box(1))
    values.put("guestsCanModify", Int.box(1))
    values.put("availability", Int.box(0))
    val uri = context.getContentResolver().insert(Events.CONTENT_URI, values)
    val eventId = uri.getLastPathSegment().toLong
    eventId
  }
}
