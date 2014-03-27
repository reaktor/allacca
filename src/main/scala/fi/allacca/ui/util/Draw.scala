package fi.allacca.ui.util

import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.graphics.Color
import android.graphics.Paint.Style

object Draw {
  def createBoundingBoxBackground: ShapeDrawable = {
    val rect = new RectShape()
    val rectShapeDrawable = new ShapeDrawable(rect)
    val paint = rectShapeDrawable.getPaint()
    paint.setColor(Color.WHITE)
    paint.setStyle(Style.STROKE)
    paint.setStrokeWidth(5)
    rectShapeDrawable
  }
}
