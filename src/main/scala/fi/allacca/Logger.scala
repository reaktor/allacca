package fi.allacca

import android.util.Log

object Logger {
  val TAG = "ALLACCA"
  def info(msg: => String) {
    Log.i(TAG, msg)
  }
  def debug(msg: => String) {
    Log.d(TAG, msg)
  }
}
