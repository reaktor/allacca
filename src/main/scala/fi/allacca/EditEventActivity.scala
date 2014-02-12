package fi.allacca

import android.app.Activity
import android.os.Bundle
import android.util.{TypedValue, Log}
import android.widget._
import android.view.ViewGroup.LayoutParams
import android.text.{InputFilter, InputType}
import fi.allacca.ui.util.TextChangeListener.func2TextChangeListener
import android.graphics.Color

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
    val dayFieldParams = new RelativeLayout.LayoutParams(dip2px(50), LayoutParams.WRAP_CONTENT)
    dayFieldParams.addRule(RelativeLayout.BELOW, eventNameField.getId)
    dayField.setLayoutParams(dayFieldParams)
    dayField.setHint("d")
    dayField.setFilters(Array[InputFilter](new InputFilter.LengthFilter(2)))
    dayField.setInputType(InputType.TYPE_CLASS_NUMBER)

    dayField.addTextChangedListener({ x:String => Log.i(TAG, s"Got string $x"); dayField.setTextColor(Color.RED) })

    editLayout.addView(dayField)

    val monthField = new EditText(this)
    monthField.setId(idGenerator.nextId)
    val monthFieldParams = new RelativeLayout.LayoutParams(dip2px(50), LayoutParams.WRAP_CONTENT)
    monthFieldParams.addRule(RelativeLayout.BELOW, eventNameField.getId)
    monthFieldParams.addRule(RelativeLayout.RIGHT_OF, dayField.getId)
    monthField.setLayoutParams(monthFieldParams)
    monthField.setHint("m")
    monthField.setFilters(Array[InputFilter](new InputFilter.LengthFilter(2)))
    monthField.setInputType(InputType.TYPE_CLASS_NUMBER)
    editLayout.addView(monthField)

    val yearField = new EditText(this)
    yearField.setId(idGenerator.nextId)
    val yearFieldParams = new RelativeLayout.LayoutParams(dip2px(65), LayoutParams.WRAP_CONTENT)
    yearFieldParams.addRule(RelativeLayout.BELOW, eventNameField.getId)
    yearFieldParams.addRule(RelativeLayout.RIGHT_OF, monthField.getId)
    yearField.setLayoutParams(yearFieldParams)
    yearField.setHint("year")
    yearField.setFilters(Array[InputFilter](new InputFilter.LengthFilter(4)))
    yearField.setInputType(InputType.TYPE_CLASS_NUMBER)
    editLayout.addView(yearField)

    val hourField = new EditText(this)
    hourField.setId(idGenerator.nextId)
    val hourFieldParams = new RelativeLayout.LayoutParams(dip2px(50), LayoutParams.WRAP_CONTENT)
    hourFieldParams.addRule(RelativeLayout.BELOW, eventNameField.getId)
    hourFieldParams.addRule(RelativeLayout.RIGHT_OF, yearField.getId)
    hourField.setLayoutParams(hourFieldParams)
    hourField.setHint("h")
    hourField.setFilters(Array[InputFilter](new InputFilter.LengthFilter(2)))
    hourField.setInputType(InputType.TYPE_CLASS_NUMBER)
    editLayout.addView(hourField)

    val minuteField = new EditText(this)
    minuteField.setId(idGenerator.nextId)
    val minuteFieldParams = new RelativeLayout.LayoutParams(dip2px(50), LayoutParams.WRAP_CONTENT)
    minuteFieldParams.addRule(RelativeLayout.BELOW, eventNameField.getId)
    minuteFieldParams.addRule(RelativeLayout.RIGHT_OF, hourField.getId)
    minuteField.setLayoutParams(minuteFieldParams)
    minuteField.setHint("m")
    minuteField.setFilters(Array[InputFilter](new InputFilter.LengthFilter(2)))
    minuteField.setInputType(InputType.TYPE_CLASS_NUMBER)
    editLayout.addView(minuteField)

    setContentView(editLayout)
  }

  def dip2px(dip: Float): Int = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, getResources().getDisplayMetrics()))

}
