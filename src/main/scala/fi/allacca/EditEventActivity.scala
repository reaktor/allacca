package fi.allacca

import android.app.Activity
import android.os.Bundle
import android.util.{TypedValue, Log}
import android.widget._
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.text.InputFilter
import android.text.InputType.TYPE_CLASS_NUMBER
import android.text.InputType.TYPE_CLASS_TEXT
import android.widget.RelativeLayout.BELOW
import android.widget.RelativeLayout.RIGHT_OF
import fi.allacca.ui.util.TextChangeListener.func2TextChangeListener
import android.graphics.Color
import android.content.Context

class EditEventActivity extends Activity with TypedViewHolder {
  import EditEventActivity._

  private lazy val eventNameField = createEventNameField()
  private lazy val header = createHeader()

  private lazy val startDateTimeField = new DateTimeField(eventNameField.getId, this)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "EditEventActivity.onCreate")

    val editLayout = new RelativeLayout(this)
    val mainLayoutParams = new RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    editLayout.setPadding(dip2px(16, this), 0, dip2px(16, this), 0)
    editLayout.setLayoutParams(mainLayoutParams)

    startDateTimeField.init(editLayout)

    editLayout.addView(header)
    editLayout.addView(eventNameField)

    setContentView(editLayout)
  }

  private def createHeader() = {
    val header = new TextView(this)
    header.setId(idGenerator.nextId)
    header.setText("Event name")
    val params = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    header.setLayoutParams(params)
    header
  }

  private def createEventNameField() = {
    val eventNameField = new EditText(this)
    eventNameField.setId(idGenerator.nextId)
    val eventNameLayoutParams: RelativeLayout.LayoutParams = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    eventNameLayoutParams.addRule(BELOW, header.getId)

    eventNameField.setLayoutParams(eventNameLayoutParams)
    eventNameField.setHint("Event title")
    eventNameField.setInputType(TYPE_CLASS_TEXT)
    eventNameField
  }
}

object EditEventActivity {
  val idGenerator = new IdGenerator
  def addTextField(context: Context, widthDip: Float, inputLength: Int, hint: String, inputType: Int, layoutParamRules: (Int, Int)*) : EditText = {
    val textField = new EditText(context)
    textField.setId(idGenerator.nextId)
    val textFieldParams = new RelativeLayout.LayoutParams(dip2px(widthDip, context), WRAP_CONTENT)
    layoutParamRules.foreach { rule =>
      textFieldParams.addRule(rule._1, rule._2)
    }
    textField.setLayoutParams(textFieldParams)
    textField.setHint(hint)
    textField.setFilters(Array[InputFilter](new InputFilter.LengthFilter(inputLength)))
    textField.setInputType(inputType)
    textField
  }
  def dip2px(dip: Float, context: Context): Int = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, context.getResources().getDisplayMetrics()))
}

class DateTimeField(placeBelowFieldId: Int, val context: Context) {
  val dayField: EditText = EditEventActivity.addTextField(context, 50, 2, "d", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId/*eventNameField.getId*/))
  val monthField: EditText = EditEventActivity.addTextField(context, 50, 2, "m", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId), (RIGHT_OF, dayField.getId))
  val yearField: EditText = EditEventActivity.addTextField(context, 65, 4, "year", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId), (RIGHT_OF, monthField.getId))
  val hourField: EditText = EditEventActivity.addTextField(context, 50, 2, "h", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId), (RIGHT_OF, yearField.getId))
  val minuteField: EditText = EditEventActivity.addTextField(context, 50, 2, "m", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId), (RIGHT_OF, hourField.getId))

  def init(editLayout: RelativeLayout) {
    def startDateValidator(x: String) {
      println("start date validator")
      Log.i(TAG, s"start date validator dayField value:"); //dayField.setTextColor(Color.RED)
      Log.i(TAG, dayField.getText().toString)
    }

    List(dayField, monthField, yearField, hourField, minuteField).foreach { field =>
      editLayout.addView(field)
      field.addTextChangedListener(startDateValidator _)
    }
  }

}
