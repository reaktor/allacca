package fi.allacca

import android.app.Activity
import android.widget._

class WeeksView(activity: Activity, adapter: WeeksAdapter2) extends ListView(activity) {
  val howManyWeeksToLoadAtTime = 20
}

class WeeksAdapter2 {
}
