package fi.allacca

import android.util.TypedValue._
import android.util.{TypedValue, DisplayMetrics}

class Dimensions(displayMetrics: DisplayMetrics) {
  val calendarScrollWidth = spToPx(500)
  val weekNumberWidth = spToPx(40)
  val dayColumnWidth = spToPx(30)
  val weekRowHeight = spToPx(40)

  private def spToPx(scaledPixels: Int): Int = {
    val floatPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, scaledPixels, displayMetrics)
    (floatPixels + 0.5).asInstanceOf[Int]
  }
}
