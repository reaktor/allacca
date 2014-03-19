package fi.allacca

import android.app.{AlertDialog, DialogFragment, Dialog, Activity}
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
import android.content.{DialogInterface, Intent, Context}
import org.joda.time.{LocalDate, Period, IllegalFieldValueException, DateTime}
import android.view.{ViewGroup, View}
import scala.Array
import fi.allacca.ui.util.TextChangeListener


class EditEventActivity extends Activity with TypedViewHolder {
  import EditEventActivity._

  private lazy val calendarEventService = new CalendarEventService(this)

  private lazy val calendarSelection = createCalendarSelection
  private lazy val eventNameHeader = createHeader("Event name", Some(calendarSelection.getId))
  private lazy val eventNameField = createTextField(getPrepopulateText { e => e.title }, eventNameHeader.getId, "Event title")
  private lazy val startTimeHeader = createHeader("Start time", Some(eventNameField.getId))
  private lazy val startDateTimeField = new DateTimeField(getPrepopulateStartTime, startTimeHeader.getId, this, okButtonController, Some(startTimeFocusChange))
  private lazy val endTimeHeader = createHeader("End time", Some(startDateTimeField.lastElementId))
  private lazy val endDateTimeField = new DateTimeField(getPrepopulateEndTime, endTimeHeader.getId, this, okButtonController)
  private lazy val eventLocationHeader = createHeader("Event location", Some(endDateTimeField.lastElement.getId))
  private lazy val eventLocationField = createTextField(getPrepopulateText { e => e.location }, eventLocationHeader.getId, "Event location")
  private lazy val eventDescriptionHeader = createHeader("Event description", Some(eventLocationField.getId))
  private lazy val eventDescriptionField = createDescriptionField(getPrepopulateText { e => e.description })
  private lazy val okButton = createOkButton
  private lazy val deleteButton = createDeleteButton(okButton.getId)
  private lazy val cancelButton = createCancelButton(deleteButton.getId)
  private lazy val idOfEventWeAreEditing: Option[Long] = getIdOfEditedEvent

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    val editLayout = createMainLayout
    addControlsToLayout(editLayout)
    initTextFieldListeners()
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
    editLayout.addView(calendarSelection)
    editLayout.addView(eventNameHeader)
    editLayout.addView(eventNameField)
    editLayout.addView(startTimeHeader)
    editLayout.addView(endTimeHeader)
    editLayout.addView(eventLocationHeader)
    editLayout.addView(eventLocationField)
    editLayout.addView(eventDescriptionHeader)
    editLayout.addView(eventDescriptionField)
    editLayout.addView(createHeader("", Some(eventDescriptionField.getId))) //Without this bottomMargin of last element doesn't work :(
    editLayout.addView(okButton)
    editLayout.addView(deleteButton)
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

  private def initTextFieldListeners() {
    eventNameField.addTextChangedListener(okButtonController _)
    eventLocationField.addTextChangedListener(okButtonController _)
  }

  private def getIdOfEditedEvent: Option[Long] = getOptionalLongFromIntentExtra(EVENT_ID)

  private def getOriginalFocusDay: Option[Long] = getOptionalLongFromIntentExtra(FOCUS_DATE_EPOCH_MILLIS)

  private def getOptionalLongFromIntentExtra(key: String): Option[Long] = {
    val value = getIntent.getLongExtra(key, NULL_VALUE)
    if (value == NULL_VALUE) None else Some(value)
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

  private def startTimeFocusChange(view: View, focus: Boolean) {
    if (startDateTimeField.isValid && !focus) { //When focus is lost, we check the end date situation (no partial fillings with one digit this way)
      val startTime = startDateTimeField.getDateTime
      val endTime = endDateTimeField.getDateTime
      if (endTime.isBefore(startTime)) endDateTimeField.setDateTime(startTime.plusHours(1))
    }
    okButtonController()
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

  private def createDescriptionField(prepopulate: String): EditText = {
    val field = createPlainTextFieldWithHint(prepopulate, "Event description")
    val layoutParams: RelativeLayout.LayoutParams = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    layoutParams.addRule(BELOW, eventDescriptionHeader.getId)
    layoutParams.bottomMargin = dip2px(50, this)
    field.setLayoutParams(layoutParams)
    field
  }

  private def createTextField(prepopulate: String, belowField: Int, hint: String): EditText = {
    val field: EditText = createPlainTextFieldWithHint(prepopulate, hint)
    val layoutParams: RelativeLayout.LayoutParams = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    layoutParams.addRule(BELOW, belowField)
    field.setLayoutParams(layoutParams)
    field
  }

  def createPlainTextFieldWithHint(prepopulate: String, hint: String): EditText = {
    val field = new EditText(this)
    field.setText(prepopulate)
    field.setId(idGenerator.nextId)
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
    button.setTextColor(Color.GREEN)
    button.setOnClickListener(saveEvent _)
    button
  }

  private def createDeleteButton(leftOfId: Int): Button = {
    val button = new Button(this)
    button.setId(idGenerator.nextId)
    val params = new RelativeLayout.LayoutParams(dip2px(50, this), WRAP_CONTENT)
    params.addRule(LEFT_OF, leftOfId)
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
    button.setLayoutParams(params)
    button.setText("X")
    button.setTextColor(Color.RED)
    button.setOnClickListener(confirmDelete _)
    button.setEnabled(idOfEventWeAreEditing.isDefined)
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
      backToRefreshedParentView(Some(eventToSave))
    }
  }

  private def backToRefreshedParentView(savedEvent: Option[CalendarEvent] = None) {
    Log.i(TAG, "Going back to main view and refreshing the results")
    val intent = new Intent(this, classOf[AllaccaMain])
    val dateToFocusOn: Long = savedEvent match {
      case Some(event) => event.startTime
      case None => getOriginalFocusDay.getOrElse(new LocalDate)
    }
    intent.putExtra(FOCUS_DATE_EPOCH_MILLIS, dateToFocusOn)
    setResult(Activity.RESULT_OK, intent)
    finish()
  }

  def confirmDelete(view: View) {
    idOfEventWeAreEditing match {
      case Some(id) => new ConfirmDeleteDialogFragment(calendarEventService, id, this, backToRefreshedParentView()).show(getFragmentManager, "Delete")
      case _ => Log.i(TAG, "No saved event to delete")
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
    textField.addTextChangedListener(new TextChangeListener {
      def changed(text: String) {
        if (text.length >= inputLength && textField.hasFocus && (textField.getParent != null)) {
          textField.clearFocus()
          val parent = textField.getParent.asInstanceOf[ViewGroup]
          val nextFocusTarget = parent.findViewById(textField.getNextFocusDownId)
          nextFocusTarget.requestFocus()
        }
      }
    })
    textField
  }
  def dip2px(dip: Float, context: Context): Int = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, context.getResources.getDisplayMetrics))
}

class DateTimeField(val prePopulateTime: DateTime, placeBelowFieldId: Int, val context: Context, changeListener: (String => Unit), focusListener: Option[(View, Boolean) => Unit] = None) {
  val hourField: EditText = EditEventActivity.addTextField(context, 50, 2, "h", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId))
  val minuteField: EditText = EditEventActivity.addTextField(context, 50, 2, "m", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId), (RIGHT_OF, hourField.getId))
  val dayField: EditText = EditEventActivity.addTextField(context, 50, 2, "d", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId), (RIGHT_OF, minuteField.getId))
  val monthField: EditText = EditEventActivity.addTextField(context, 50, 2, "m", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId), (RIGHT_OF, dayField.getId))
  val yearField: EditText = EditEventActivity.addTextField(context, 65, 4, "year", TYPE_CLASS_NUMBER, (BELOW, placeBelowFieldId), (RIGHT_OF, monthField.getId))
  val fields = List(hourField, minuteField, dayField, monthField, yearField)

  def init(editLayout: RelativeLayout) {
    setDateTime(prePopulateTime)
    fields.foreach { field =>
      editLayout.addView(field)
      field.setSelectAllOnFocus(true)
      field.addTextChangedListener(validate _)
      field.addTextChangedListener(changeListener)
      focusListener.map { focusListener => field.setOnFocusChangeListener(focusListener) }
    }
    hourField.setNextFocusDownId(minuteField.getId)
    minuteField.setNextFocusDownId(dayField.getId)
    dayField.setNextFocusDownId(monthField.getId)
    monthField.setNextFocusDownId(yearField.getId)
  }

  def getDateTime: DateTime = {
    def intValue(editText: EditText) = {
      editText.getText.toString.toInt
    }
    new DateTime(intValue(yearField), intValue(monthField), intValue(dayField), intValue(hourField), intValue(minuteField), 0, 0)
  }

  def setDateTime(prepopulate: DateTime) {
    dayField.setText(digitToStr(prepopulate.getDayOfMonth))
    monthField.setText(digitToStr(prepopulate.getMonthOfYear))
    yearField.setText(prepopulate.getYear.toString)
    hourField.setText(digitToStr(prepopulate.getHourOfDay))
    minuteField.setText(digitToStr(prepopulate.getMinuteOfHour))
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

  def lastElement = yearField
  def lastElementId = yearField.getId
  def firstElementId = hourField.getId

  private def digitToStr(d: Int): String = {
    val str = d.toString
    if (str.length < 2) s"0$str" else str
  }
}

class ConfirmDeleteDialogFragment(eventService: CalendarEventService, eventId: Long, activity: Activity, confirmedCallback: => Unit) extends DialogFragment {
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val builder: AlertDialog.Builder = new AlertDialog.Builder(activity)
    builder
      .setMessage("Delete event?")
      .setPositiveButton("OK", new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, id: Int) {
          Log.i(TAG, s"Deleting event $eventId")
          eventService.deleteEvent(eventId)
          confirmedCallback
        }
        })
      .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, id: Int) {
          Log.i(TAG, s"Not deleting event $eventId")
      }
    })
    builder.create()
  }
}
