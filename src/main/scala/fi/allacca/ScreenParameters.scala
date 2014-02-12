package fi.allacca

import android.util.{TypedValue, DisplayMetrics}
import android.graphics.Color

class ScreenParameters(displayMetrics: DisplayMetrics) {
  val calendarScrollWidth = spToPx(500)
  val weekNumberWidth = spToPx(40)
  val dayColumnWidth = spToPx(30)
  val weekRowHeight = spToPx(40)
  val overviewHeaderTextSize = 30
  val overviewContentTextSize = 20

  val weekListWidth = weekNumberWidth + (7 * dayColumnWidth)

  val governorBay = Color.parseColor("#a22fbb")
  val pavlova = Color.parseColor("#bba378")
  val funBlue = Color.parseColor("#2e4d7c")

  private def spToPx(scaledPixels: Int): Int = {
    val floatPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, scaledPixels, displayMetrics)
    (floatPixels + 0.5).asInstanceOf[Int]
  }
}
