package fi

import android.view.View
import android.view.View.OnClickListener

package object allacca {
  implicit def func2OnClickListener( f: View => Unit ) =
    new  OnClickListener() {
      def onClick( evt: View ) = f(evt)
    }
}
