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

    val hourField = addTextField(50, 2, "h", InputType.TYPE_CLASS_NUMBER, (RelativeLayout.BELOW, eventNameField.getId), (RelativeLayout.RIGHT_OF, yearField.getId))
    editLayout.addView(hourField)


    val minuteField = addTextField(50, 2, "m", InputType.TYPE_CLASS_NUMBER, (RelativeLayout.BELOW, eventNameField.getId), (RelativeLayout.RIGHT_OF, hourField.getId))
    editLayout.addView(minuteField)

    setContentView(editLayout)
  }

  private def addTextField(widthDip: Float, inputLength: Int, hint: String, inputType: Int, layoutParamRules: (Int, Int)*) : EditText = {
    val textField = new EditText(this)
    textField.setId(idGenerator.nextId)
    val textFieldParams = new RelativeLayout.LayoutParams(dip2px(50), LayoutParams.WRAP_CONTENT)
    layoutParamRules.foreach { rule =>
      textFieldParams.addRule(rule._1, rule._2)
    }
    textField.setLayoutParams(textFieldParams)
    textField.setHint(hint)
    textField.setFilters(Array[InputFilter](new InputFilter.LengthFilter(inputLength)))
    textField.setInputType(inputType)
    textField
  }

  private def dip2px(dip: Float): Int = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, getResources().getDisplayMetrics()))

}
