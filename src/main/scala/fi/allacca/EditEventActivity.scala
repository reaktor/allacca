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
import android.widget.RelativeLayout.{LayoutParams, BELOW, RIGHT_OF, LEFT_OF}
import fi.allacca.ui.util.TextChangeListener.func2TextChangeListener
import android.graphics.Color
import android.content.{Intent, Context}
import org.joda.time.{IllegalFieldValueException, DateTime}
import android.view.{View, ViewGroup}

class EditEventActivity extends Activity with TypedViewHolder {
  import EditEventActivity._

  private lazy val calendarEventService = new CalendarEventService(this)

  private lazy val eventNameHeader = createHeader("Event name")
  private lazy val eventNameField = createEventNameField()
  private lazy val startTimeHeader = createHeader("Start time", Some(eventNameField.getId))
  private lazy val startDateTimeField = new DateTimeField(startTimeHeader.getId, this)
  private lazy val endTimeHeader = createHeader("End time", Some(startDateTimeField.lastElementId))
  private lazy val endDateTimeField = new DateTimeField(endTimeHeader.getId, this)

  val HARD_CODED_CALENDAR_ID_UNTIL_CALENDAR_SELECTION_IS_IMPLEMENTED: Long = 1

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "EditEventActivity.onCreate")

    val editLayout = new RelativeLayout(this)
    val mainLayoutParams = new RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    editLayout.setPadding(dip2px(16, this), 0, dip2px(16, this), 0)
    editLayout.setLayoutParams(mainLayoutParams)

    editLayout.addView(eventNameHeader)
    editLayout.addView(eventNameField)

    editLayout.addView(startTimeHeader)
    startDateTimeField.init(editLayout)
    editLayout.addView(endTimeHeader)
    endDateTimeField.init(editLayout)

    val okButton = createOkButton
    editLayout.addView(okButton)

    val cancelButton = createCancelButton(okButton.getId)
    editLayout.addView(cancelButton)

    setContentView(editLayout)
  }

  private def createHeader(text: String, belowField: Option[Int] = None) = {
    val header = new TextView(this)
    header.setId(idGenerator.nextId)
    header.setText(text)
    val layoutParams = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    belowField.map { fieldId => layoutParams.addRule(BELOW, fieldId) }
    header.setLayoutParams(layoutParams)
    header
  }

  private def createEventNameField() = {
    val eventNameField = new EditText(this)
    eventNameField.setId(idGenerator.nextId)
    val eventNameLayoutParams: RelativeLayout.LayoutParams = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    eventNameLayoutParams.addRule(BELOW, eventNameHeader.getId)

    eventNameField.setLayoutParams(eventNameLayoutParams)
    eventNameField.setHint("Event title")
    eventNameField.setInputType(TYPE_CLASS_TEXT)
    eventNameField
  }

  private def createOkButton: Button = {
    val button = new Button(this)
    button.setId(idGenerator.nextId)
    val params = new RelativeLayout.LayoutParams(dip2px(50, this), WRAP_CONTENT)
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
    params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
    button.setLayoutParams(params)
    button.setText("✔")
    button.setTextColor(Color.WHITE)
    button.setOnClickListener(saveEvent _)
    button
  }

  private def createCancelButton(leftOfId: Int): Button = {
    val button = new Button(this)
    button.setId(idGenerator.nextId)
    val params = new RelativeLayout.LayoutParams(dip2px(50, this), WRAP_CONTENT)
    params.addRule(LEFT_OF, leftOfId)
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
    button.setLayoutParams(params)
    button.setText("←")
    button.setTextColor(Color.WHITE)
    button.setOnClickListener(cancel _)
    button
  }

  def isValid = !eventNameField.getText.toString.isEmpty && startDateTimeField.isValid && endDateTimeField.isValid

  def cancel(view: View) {
    onBackPressed()
  }

  def saveEvent (view: View) {
    val eventName = eventNameField.getText.toString
    if (isValid) {
      Log.i(TAG, s"all valid, let's save: $eventName ${startDateTimeField.getDateTime} ${endDateTimeField.getDateTime}")
      val startMillis = startDateTimeField.getDateTime.toDate.getTime
      val endMillis = endDateTimeField.getDateTime.toDate.getTime
      val eventToSave = new CalendarEvent(eventName, startMillis, endMillis)
      calendarEventService.createEvent(HARD_CODED_CALENDAR_ID_UNTIL_CALENDAR_SELECTION_IS_IMPLEMENTED, eventToSave)
      Log.i(TAG, "SAVED!")
      onBackPressed()
    } else {
      Log.i(TAG, s"What's not valid? event name valid ${eventName} start valid ${startDateTimeField.isValid} end valid ${endDateTimeField.isValid}")
    }
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
  val fields = List(dayField, monthField, yearField, hourField, minuteField)

  def init(editLayout: RelativeLayout) {
    fields.foreach { field =>
      editLayout.addView(field)
      field.addTextChangedListener(validate _)
    }
  }

  def getDateTime: DateTime = {
    def intValue(editText: EditText) = {
      editText.getText.toString.toInt
    }
    new DateTime(intValue(yearField), intValue(monthField), intValue(dayField), intValue(hourField), intValue(minuteField), 0, 0)
  }

  def isValid: Boolean = {
    try {
      getDateTime //Will throw the exceptions below if not valid
      true
    } catch {
      case e: NumberFormatException =>
        false
      case e: IllegalFieldValueException =>
        false
    }
  }

  def validate(x: String) {
    Log.i(TAG, s"** valid $isValid **")
    if (isValid) {
      fields.foreach { field =>
        field.setTextColor(Color.WHITE)
      }
    } else {
      fields.foreach { field =>
        field.setTextColor(Color.RED)
      }
    }
  }

  def lastElementId = minuteField.getId

}
