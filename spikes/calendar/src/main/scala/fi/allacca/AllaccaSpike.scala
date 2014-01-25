package fi.allacca

import android.app.Activity
import android.os.Bundle

class AllaccaSpike extends Activity with TypedViewHolder {
    override def onCreate(savedInstanceState: Bundle): Unit = {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        findView(TR.textview).setText("Terve android-sdksta!")
    }
}
