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
import android.widget.RelativeLayout.{BELOW, RIGHT_OF, LEFT_OF}
import fi.allacca.ui.util.TextChangeListener.func2TextChangeListener
import android.graphics.Color
import android.content.Context
import org.joda.time.{Period, IllegalFieldValueException, DateTime}
import android.view.View
import scala.Array
import android.provider.CalendarContract.Calendars
import android.database.Cursor

class EditEventActivity extends Activity with TypedViewHolder {
  import EditEventActivity._

  private lazy val calendarEventService = new CalendarEventService(this)

  private lazy val calendarSelection = createCalendarSelection
  private lazy val eventNameHeader = createHeader("Event name", Some(calendarSelection.getId))
  private lazy val eventNameField = createEventNameField()
  private lazy val startTimeHeader = createHeader("Start time", Some(eventNameField.getId))
  private lazy val startDateTimeField = new DateTimeField(new DateTime().plus(Period.days(1)), startTimeHeader.getId, this, okButtonController)
  private lazy val endTimeHeader = createHeader("End time", Some(startDateTimeField.lastElementId))
  private lazy val endDateTimeField = new DateTimeField(new DateTime().plus(Period.days(1)).plusHours(1), endTimeHeader.getId, this, okButtonController)
  private lazy val okButton = createOkButton
  private lazy val cancelButton = createCancelButton(okButton.getId)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    val editLayout = createMainLayout
    addControls(editLayout)
    eventNameField.addTextChangedListener(okButtonController _)
    initDateFields(editLayout)
    initTabOrder()
    setContentView(editLayout)
    okButtonController()
  }

  private def initTabOrder() {
    eventNameField.setNextFocusDownId(startDateTimeField.firstElementId)
    startDateTimeField.lastElement.setNextFocusDownId(endDateTimeField.firstElementId)
  }

  private def initDateFields(editLayout: RelativeLayout) {
    startDateTimeField.init(editLayout)
    endDateTimeField.init(editLayout)
  }

  private def createMainLayout: RelativeLayout = {
    val editLayout = new RelativeLayout(this)
    val mainLayoutParams = new RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    editLayout.setPadding(dip2px(16, this), 0, dip2px(16, this), 0)
    editLayout.setLayoutParams(mainLayoutParams)
    editLayout
  }

  private def addControls(editLayout: RelativeLayout) {
    editLayout.addView(calendarSelection)
    editLayout.addView(eventNameHeader)
    editLayout.addView(eventNameField)
    editLayout.addView(startTimeHeader)
    editLayout.addView(endTimeHeader)
    editLayout.addView(okButton)
    editLayout.addView(cancelButton)
  }

  private def createCalendarSelection: Spinner = {
    def getCalendars(calCursor: Cursor, calendars: Array[SpinnerCalendarModel]): Array[SpinnerCalendarModel] = {
      val id = calCursor.getLong(0)
      val displayName = calCursor.getString(1)
      val currCalendar = new SpinnerCalendarModel(id, displayName)
      val newCalendars = calendars :+ currCalendar
      if (calCursor.moveToNext()) getCalendars(calCursor, newCalendars) else newCalendars
    }

    val calendarSelection = new Spinner(this)
    val queryCols = Array[String] ("_id", Calendars.NAME)
    val calCursor: Cursor = getContentResolver.query(Calendars.CONTENT_URI, queryCols, "visible" + " = 1", null, "_id" + " ASC")

    calCursor.moveToFirst()
    val calendars = getCalendars(calCursor, Array())

    val spinnerArrayAdapter: ArrayAdapter[SpinnerCalendarModel] = new ArrayAdapter[SpinnerCalendarModel](this, android.R.layout.simple_spinner_dropdown_item, calendars)
    calendarSelection.setAdapter(spinnerArrayAdapter)

    calendarSelection.setId(idGenerator.nextId)

    val layoutParams = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    calendarSelection.setLayoutParams(layoutParams)
    calendarSelection
  }

  private def okButtonController(text: String = "") {
    Log.i(TAG, s"Setting ok button enabled status to $isValid")
    okButton.setEnabled(isValid)
  }

  private def createHeader(text: String, belowField: Option[Int] = None): TextView = {
    val header = new TextView(this)
    header.setId(idGenerator.nextId)
    header.setText(text)
    val layoutParams = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    belowField.map { fieldId => layoutParams.addRule(BELOW, fieldId) }
    header.setLayoutParams(layoutParams)
    header
  }

  private def createEventNameField(): EditText = {
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
      val startMillis = startDateTimeField.getDateTime.toDate.getTime
      val endMillis = endDateTimeField.getDateTime.toDate.getTime
      val eventToSave = new CalendarEvent(eventName, startMillis, endMillis)
      val selectedCalendar = calendarSelection.getSelectedItem.asInstanceOf[SpinnerCalendarModel]
      Log.i(TAG, s"all valid, let's save: ${selectedCalendar.id} $eventName ${startDateTimeField.getDateTime} ${endDateTimeField.getDateTime}")
      calendarEventService.createEvent(selectedCalendar.id, eventToSave)
      Log.i(TAG, "SAVED!")
      onBackPressed()
    } else {
      Log.i(TAG, s"What's not valid? event name valid $eventName start valid ${startDateTimeField.isValid} end valid ${endDateTimeField.isValid}")
    }
  }
}

class SpinnerCalendarModel(val id: Long, val name: String) {
  override def toString = name
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
  def dip2px(dip: Float, context: Context): Int = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, context.getResources.getDisplayMetrics))
}

class DateTimeField(val prePopulateTime: DateTime, placeBelowFieldId: Int, val context: Context, changeListener: (String => Unit)) {
  val dayField: EditText = EditEventActivity.addTextField(context, 50, 2, "d", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId))
  val monthField: EditText = EditEventActivity.addTextField(context, 50, 2, "m", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId), (RIGHT_OF, dayField.getId))
  val yearField: EditText = EditEventActivity.addTextField(context, 65, 4, "year", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId), (RIGHT_OF, monthField.getId))
  val hourField: EditText = EditEventActivity.addTextField(context, 50, 2, "h", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId), (RIGHT_OF, yearField.getId))
  val minuteField: EditText = EditEventActivity.addTextField(context, 50, 2, "m", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId), (RIGHT_OF, hourField.getId))
  val fields = List(dayField, monthField, yearField, hourField, minuteField)

  def init(editLayout: RelativeLayout) {
    prePopulateFields(prePopulateTime)
    fields.foreach { field =>
      editLayout.addView(field)
      field.setSelectAllOnFocus(true)
      field.addTextChangedListener(validate _)
      field.addTextChangedListener(changeListener)
    }
    dayField.setNextFocusDownId(monthField.getId)
    monthField.setNextFocusDownId(yearField.getId)
    yearField.setNextFocusDownId(hourField.getId)
    hourField.setNextFocusDownId(minuteField.getId)
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
    val modifier: (EditText => Unit) =
      if (isValid) { _.setTextColor(Color.WHITE) } else { _.setTextColor(Color.RED) }
    fields.foreach(modifier)
  }

  def lastElement = minuteField
  def lastElementId = minuteField.getId
  def firstElementId = dayField.getId

  private def prePopulateFields(prepopulate: DateTime) {
    dayField.setText(prepopulate.getDayOfMonth.toString)
    monthField.setText(prepopulate.getMonthOfYear.toString)
    yearField.setText(prepopulate.getYear.toString)
    hourField.setText(prepopulate.getHourOfDay.toString)
    minuteField.setText(prepopulate.getMinuteOfHour.toString)
  }
}
