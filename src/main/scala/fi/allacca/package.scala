package fi

import android.view.View
import android.view.View.OnClickListener

/*
 * When your code is not in allacca-package, but you need these definitions, do this import:
 * import fi.allacca._
 */
package object allacca {
  //For logging
  def TAG = "ALLACCA"

  //Intent keys
  def EVENT_ID = "fi.allacca.eventID"

  implicit def func2OnClickListener(f: View => Unit) = new OnClickListener() {
    def onClick(evt: View) = f(evt)
  }
}
