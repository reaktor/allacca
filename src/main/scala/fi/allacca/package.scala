package fi

import android.view.View
import android.view.View.{OnLongClickListener, OnFocusChangeListener, OnClickListener}
import org.joda.time.LocalDate
import android.util.Log

/*
 * When your code is not in allacca-package, but you need these definitions, do this import:
 * import fi.allacca._
 */
package object allacca {
  //For logging
  val TAG = "ALLACCA"

  //Intent keys
  val EVENT_ID = "fi.allacca.eventID"
  val EVENT_DATE = "fi.allacca.eventDate"
  val FOCUS_DATE_EPOCH_MILLIS = "fi.allacca.focusEpochMilliseconds"
  val NULL_VALUE = -1L

  val REQUEST_CODE_EDIT_EVENT = 1

  implicit def func2OnClickListener(f: View => Unit) = new OnClickListener() {
    def onClick(evt: View) { f(evt) }
  }

  implicit def func2OnLongClickListener(f: View => Boolean) = new OnLongClickListener() {
    def onLongClick(evt: View): Boolean = { f(evt) }
  }

  implicit def func2OnFocusChangeListener(f: (View, Boolean) => Unit) = new OnFocusChangeListener {
    def onFocusChange(view: View, focus: Boolean) { f(view, focus) }
  }

  implicit def localDateToEpochMillis(localDate: LocalDate): Long = localDate.toDate.getTime

  implicit def func2Runnable(f: => Unit) = new Runnable() { def run() { f }}

  /**
   * Timing utility from http://stackoverflow.com/a/9160068
   */
  def time[R](block: => R, name: String = ""): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    val nanoSeconds = t1 - t0
    val milliSeconds = nanoSeconds / 1000000f
    Logger.debug(s"elapsed time $name: $nanoSeconds ns ($milliSeconds ms)")
    result
  }
}
