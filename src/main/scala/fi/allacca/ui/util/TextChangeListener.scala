package fi.allacca.ui.util

import android.text.{Editable, TextWatcher}

/*
 * Converted to Scala from: http://stackoverflow.com/questions/2763022/android-how-can-i-validate-edittext-input
 */
abstract class TextChangeListener extends TextWatcher {
  def changed(text: String)
  override def afterTextChanged(s: Editable) {
    val text = s
    changed(text.toString)
  }
  override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
  override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
}

object TextChangeListener {
  implicit def func2TextChangeListener(f: String => Unit) =
    new TextChangeListener() {
      override def changed(text: String) = f(text)
    }
}

