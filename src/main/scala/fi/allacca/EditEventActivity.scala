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
    header.setText("Event name")
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

    val dayField = addTextField(50, 2, "d", InputType.TYPE_CLASS_NUMBER, (RelativeLayout.BELOW, eventNameField.getId))
    editLayout.addView(dayField)

    dayField.addTextChangedListener({ x:String => Log.i(TAG, s"Got string $x"); dayField.setTextColor(Color.RED) })

    val monthField = addTextField(50, 2, "m", InputType.TYPE_CLASS_NUMBER, (RelativeLayout.BELOW, eventNameField.getId), (RelativeLayout.RIGHT_OF, dayField.getId))
    editLayout.addView(monthField)

    val yearField = addTextField(65, 4, "year", InputType.TYPE_CLASS_NUMBER, (RelativeLayout.BELOW, eventNameField.getId), (RelativeLayout.RIGHT_OF, monthField.getId))
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
    val textFieldParams = new RelativeLayout.LayoutParams(dip2px(widthDip), LayoutParams.WRAP_CONTENT)
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
