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
import android.content.{Intent, Context}
import org.joda.time.{Period, IllegalFieldValueException, DateTime}
import android.view.View
import scala.Array

class EditEventActivity extends Activity with TypedViewHolder {
  import EditEventActivity._

  private lazy val calendarEventService = new CalendarEventService(this)

  private lazy val calendarSelection = createCalendarSelection
  private lazy val eventNameHeader = createHeader("Event name", Some(calendarSelection.getId))
  private lazy val eventNameField = createTextField(getPrepopulateText { e => e.title }, eventNameHeader.getId, "Event title")
  private lazy val startTimeHeader = createHeader("Start time", Some(eventNameField.getId))
  private lazy val startDateTimeField = new DateTimeField(getPrepopulateStartTime, startTimeHeader.getId, this, okButtonController)
  private lazy val endTimeHeader = createHeader("End time", Some(startDateTimeField.lastElementId))
  private lazy val endDateTimeField = new DateTimeField(getPrepopulateEndTime, endTimeHeader.getId, this, okButtonController)
  private lazy val eventLocationHeader = createHeader("Event location", Some(endDateTimeField.lastElement.getId))
  private lazy val eventLocationField = createTextField(getPrepopulateText { e => e.location }, eventLocationHeader.getId, "Event location")
  private lazy val eventDescriptionHeader = createHeader("Event description", Some(eventLocationField.getId))
  private lazy val eventDescriptionField = createTextField(getPrepopulateText { e => e.description }, eventDescriptionHeader.getId, "Event description")
  private lazy val okButton = createOkButton
  private lazy val cancelButton = createCancelButton(okButton.getId)
  private lazy val idOfEventWeAreEditing = getIdOfEditedEvent

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    val editLayout = createMainLayout
    addControlsToLayout(editLayout)
    initTextFieldListeners
    initDateFields(editLayout)
    initTabOrder()
    setContentView(wrapInScroller(editLayout))
    okButtonController()
  }
  
  private def createMainLayout: RelativeLayout = {
    val editLayout = new RelativeLayout(this)
    val mainLayoutParams = new RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    mainLayoutParams.bottomMargin = dip2px(50, this)
    editLayout.setPadding(dip2px(16, this), 0, dip2px(16, this), 0)
    editLayout.setLayoutParams(mainLayoutParams)
    editLayout
  }

  private def addControlsToLayout(editLayout: RelativeLayout) {
    def setBottomMarginForWholeLayout {
      val layoutParams = eventDescriptionField.getLayoutParams.asInstanceOf[RelativeLayout.LayoutParams]
      layoutParams.bottomMargin = dip2px(50, this)
      eventDescriptionField.setLayoutParams(layoutParams)
    }
    editLayout.addView(calendarSelection)
    editLayout.addView(eventNameHeader)
    editLayout.addView(eventNameField)
    editLayout.addView(startTimeHeader)
    editLayout.addView(endTimeHeader)
    editLayout.addView(eventLocationHeader)
    editLayout.addView(eventLocationField)
    editLayout.addView(eventDescriptionHeader)
    setBottomMarginForWholeLayout
    editLayout.addView(eventDescriptionField)
    editLayout.addView(createHeader("", Some(eventDescriptionField.getId))) //Without this bottomMargin of last element doesn't work :(
    editLayout.addView(okButton)
    editLayout.addView(cancelButton)
  }

  private def initTabOrder() {
    eventNameField.setNextFocusDownId(startDateTimeField.firstElementId)
    startDateTimeField.lastElement.setNextFocusDownId(endDateTimeField.firstElementId)
    endDateTimeField.lastElement.setNextFocusDownId(eventLocationField.getId)
    eventLocationField.setNextFocusDownId(eventDescriptionField.getId)
  }

  private def wrapInScroller(editLayout: RelativeLayout): ScrollView = {
    val scrollView = new ScrollView(this)
    scrollView.setId(idGenerator.nextId)
    scrollView.addView(editLayout)
    scrollView
  }

  private def initTextFieldListeners {
    eventNameField.addTextChangedListener(okButtonController _)
    eventLocationField.addTextChangedListener(okButtonController _)
  }

  private def getIdOfEditedEvent: Option[Long] = {
    val eventIdWeAreEditing = getIntent.getLongExtra(EVENT_ID, NULL_VALUE)
    if (eventIdWeAreEditing == NULL_VALUE) None else Some(eventIdWeAreEditing)
  }

  private def getPrepopulateText(extractor: CalendarEvent => String): String = {
    getEventWeAreEditing match {
      case Some(event) => extractor(event)
      case None => ""
    }
  }

  private def getPrepopulateStartTime: DateTime = {
    getEventWeAreEditing match {
      case Some(event) => new DateTime(event.startTime)
      case None =>
    val eventDateLong = getIntent.getLongExtra(EVENT_DATE, NULL_VALUE)
    if (eventDateLong == NULL_VALUE) new DateTime().plus(Period.days(1)) else new DateTime(eventDateLong)
    }
  }

  private def getPrepopulateEndTime: DateTime = {
    getEventWeAreEditing match {
      case Some(event) => new DateTime(event.endTime)
      case None => getPrepopulateStartTime.plusHours(1)
    }
  }

  private def getEventWeAreEditing: Option[CalendarEvent] = {
    for {
    eventId <- idOfEventWeAreEditing
    event <- calendarEventService.getEvent(eventId)
    } yield event
  }

  private def initDateFields(editLayout: RelativeLayout) {
    startDateTimeField.init(editLayout)
    endDateTimeField.init(editLayout)
  }

  private def createCalendarSelection: Spinner = {
    val calendars = calendarEventService.getCalendars
    val calendarSelection = new Spinner(this)
    val spinnerArrayAdapter: ArrayAdapter[UserCalendar] = new ArrayAdapter[UserCalendar](this, android.R.layout.simple_spinner_dropdown_item, calendars)
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

  private def createTextField(prepopulate: String, belowField: Int, hint: String): EditText = {
    val field = new EditText(this)
    field.setText(prepopulate)
    field.setId(idGenerator.nextId)
    val layoutParams: RelativeLayout.LayoutParams = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    layoutParams.addRule(BELOW, belowField)

    field.setLayoutParams(layoutParams)
    field.setHint(hint)
    field.setInputType(TYPE_CLASS_TEXT)
    field
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
    if (isValid) {
      val eventToSave: CalendarEvent = extractEventFromFieldValues
      val selectedCalendar = calendarSelection.getSelectedItem.asInstanceOf[UserCalendar]
      saveOrUpdate(eventToSave, selectedCalendar)
      val intent = new Intent
      setResult(Activity.RESULT_OK, intent)
      finish
    }
  }

  private def saveOrUpdate(eventToSave: CalendarEvent, selectedCalendar: UserCalendar) {
    if (idOfEventWeAreEditing.isDefined) {
      val updateCount = calendarEventService.saveEvent(idOfEventWeAreEditing.get, eventToSave)
      Log.i(TAG, s"Updated event $updateCount")
    } else {
      val savedId = calendarEventService.createEvent(selectedCalendar.id, eventToSave)
      Log.i(TAG, s"Saved event with id $savedId")
    }
  }

  private def extractEventFromFieldValues: CalendarEvent = {
    val eventName = eventNameField.getText.toString
    val startMillis = startDateTimeField.getDateTime.toDate.getTime
    val endMillis = endDateTimeField.getDateTime.toDate.getTime
    val eventLocation = eventLocationField.getText.toString
    val eventDescription = eventDescriptionField.getText.toString
    val eventToSave = new CalendarEvent(id = None, title = eventName, startTime = startMillis, endTime = endMillis, location = eventLocation, description = eventDescription)
    eventToSave
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
