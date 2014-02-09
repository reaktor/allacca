package fi.allacca

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.{RelativeLayout, TextView}
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup

class EditEventActivity extends Activity with TypedViewHolder {
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "EditEventActivity.onCreate")

    val header = new TextView(this)
    header.setId(1)
    header.setText("Edit Event")
    val params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
    header.setLayoutParams(params)

    val editLayout = new RelativeLayout(this)
    val mainLayoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    editLayout.setLayoutParams(mainLayoutParams)
    editLayout.addView(header)

    setContentView(editLayout)
  }

}
