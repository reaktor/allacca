package fi.allacca

import android.app._
import android.os.Bundle
import android.widget._
import android.view.{View, ViewGroup}
import android.graphics.Color
import android.view.ViewGroup.LayoutParams
import android.util.Log


class AllaccaMain extends Activity with TypedViewHolder {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    val textView = new TextView(this)
    textView.setId(1)
    textView.setText("Hello")
    val linearLayout = new LinearLayout(this)
    val relativeParams = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT)
    relativeParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
    linearLayout.addView(textView)
    addAEventButton(linearLayout)
    setContentView(linearLayout)
  }

  private def addAEventButton(linearLayout: LinearLayout) {
    val b = new Button(this)
    val params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    b.setText("+")
    b.setTextColor(Color.WHITE)
    b.setLayoutParams(params)
    b.setOnClickListener(createNewEvent _)
    linearLayout.addView(b)
  }

  def createNewEvent (view: View) {
    Log.d("ALLACCA", "+ createNewEvent")
  }

}

