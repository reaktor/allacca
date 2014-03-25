package fi.allacca

import android.util.Log
import android.content.Context
import android.content.pm.ApplicationInfo
import java.util.concurrent.atomic.AtomicBoolean

object Logger {
  val debug = new AtomicBoolean(true)
  val TAG = "ALLACCA"

  def info(msg: => String) {
    if (debug.get()) Log.i(TAG, msg)
  }

  def debug(msg: => String) {
    if (debug.get()) Log.d(TAG, msg)
  }

  def setDebugFlag(context: Context) {
    def isDebuggable = 0 != (context.getApplicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE)
    debug.set(isDebuggable)
  }
}