package com.holyradius.nativecore.util

import android.app.ActivityManager

object ProcessState {
  /** "fg" when the app process has foreground importance, otherwise "bg". */
  fun current(): String {
    val info = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(info)
    return if (info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) "fg" else "bg"
  }
}
