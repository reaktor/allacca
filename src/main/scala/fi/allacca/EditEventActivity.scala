package fi.allacca

import android.app.Activity
import android.os.Bundle
import android.util.{TypedValue, Log}
import android.widget._
import android.view.ViewGroup.LayoutParams
import android.text.InputType

class EditEventActivity extends Activity with TypedViewHolder {
  private val idGenerator = new IdGenerator
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "EditEventActivity.onCreate")

    val editLayout = new RelativeLayout(this)
    val mainLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    editLayout.setPadding(dip2px(16), 0, dip2px(16), 0)
    editLayout.setLayoutParams(mainLayoutParams)

    val header = new TextView(this)
    header.setId(idGenerator.nextId)
    header.setText("Event details")
    val params = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    header.setLayoutParams(params)
    editLayout.addView(header)

    val eventNameField = new EditText(this)
    eventNameField.setId(idGenerator.nextId)
    val eventNameLayoutParams: RelativeLayout.LayoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    eventNameLayoutParams.addRule(RelativeLayout.BELOW, header.getId)

    eventNameField.setLayoutParams(eventNameLayoutParams)
    eventNameField.setHint("Event title")
    eventNameField.setInputType(InputType.TYPE_CLASS_TEXT)
    editLayout.addView(eventNameField)

    val dayField = new EditText(this)
    dayField.setId(idGenerator.nextId)
    val dayFieldParams: RelativeLayout.LayoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    dayFieldParams.addRule(RelativeLayout.BELOW, eventNameField.getId)
    dayField.setLayoutParams(dayFieldParams)
    dayField.setHint("day")
    dayField.setInputType(InputType.TYPE_CLASS_NUMBER)
    editLayout.addView(dayField)

    setContentView(editLayout)
  }

  def dip2px(dip: Float): Int = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, getResources().getDisplayMetrics()))
}
