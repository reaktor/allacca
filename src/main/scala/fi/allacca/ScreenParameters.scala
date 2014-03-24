package fi.allacca

import android.util.{TypedValue, DisplayMetrics}
import android.graphics.Color

class ScreenParameters(displayMetrics: DisplayMetrics) {
  val calendarScrollWidth = spToPx(500)
  val weekNumberWidth = spToPx(25)
  val weekRowHeight = spToPx(40)
  val monthLetterWidth = spToPx(25)
  val dayColumnWidth = spToPx(30)
  val overviewHeaderTextSize = 25
  val overviewContentTextSize = 20

  val weekListWidth = monthLetterWidth + weekNumberWidth + (7 * dayColumnWidth)
  val weekListRightMargin = spToPx(3)

  val governorBay = Color.parseColor("#a22fbb")
  val pavlova = Color.parseColor("#bba378")
  val funBlue = Color.parseColor("#2e4d7c")
  val darkGrey = Color.parseColor("#2B2B2B")

  private def spToPx(scaledPixels: Int): Int = {
    val floatPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, scaledPixels, displayMetrics)
    (floatPixels + 0.5).asInstanceOf[Int]
  }
}
