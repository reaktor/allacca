package fi.allacca

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.{FrameLayout, EditText, RelativeLayout, TextView}
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup
import android.text.InputType

class EditEventActivity extends Activity with TypedViewHolder {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "EditEventActivity.onCreate")

    val editLayout = new RelativeLayout(this)
    val mainLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    editLayout.setLayoutParams(mainLayoutParams)

    val header = new TextView(this)
    header.setId(1)
    header.setText("Edit Event")
    val params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
    header.setLayoutParams(params)
    editLayout.addView(header)

    val eventNameField = new EditText(this)
    eventNameField.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
    eventNameField.setHint("Event name")
    eventNameField.setInputType(InputType.TYPE_CLASS_NUMBER)
    editLayout.addView(eventNameField)

    setContentView(editLayout)
  }

}
