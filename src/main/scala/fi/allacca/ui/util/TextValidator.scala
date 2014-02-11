package fi.allacca.ui.util

import android.text.{Editable, TextWatcher}
import android.widget.TextView

/*
 * Converted to Scala from: http://stackoverflow.com/questions/2763022/android-how-can-i-validate-edittext-input
 */
abstract class TextValidator(textView: TextView) extends TextWatcher {
  def validate(textView: TextView, text: String)
  override def afterTextChanged(s: Editable) {
    val text = textView.getText.toString
    validate(textView, text)
  }
  override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
  override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
}

