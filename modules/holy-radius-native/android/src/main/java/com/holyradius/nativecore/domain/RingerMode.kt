package com.holyradius.nativecore.domain

/**
 * Ringer modes, ordered by quietness. [quietness] lets the session logic avoid ever making the
 * phone louder than the user left it (e.g. never switch SILENT -> VIBRATE).
 */
enum class RingerMode(val wire: String, val quietness: Int) {
  NORMAL("normal", 0),
  VIBRATE("vibrate", 1),
  SILENT("silent", 2);

  companion object {
    fun fromWire(value: String): RingerMode =
      entries.firstOrNull { it.wire == value } ?: throw IllegalArgumentException("Unknown ringer mode: $value")
  }
}

/** Typed result of a set-and-verify ringer change. */
sealed interface RingerSetResult {
  val wire: String

  data class Ok(val observed: RingerMode) : RingerSetResult {
    override val wire = "ok"
  }

  data object NeedsPolicyAccess : RingerSetResult {
    override val wire = "needs_policy_access"
  }

  /** The call did not throw, but reading back shows a different mode (OEM/OS override). */
  data class Mismatch(val requested: RingerMode, val observed: RingerMode) : RingerSetResult {
    override val wire = "mismatch"
  }

  data class Failed(val reason: String) : RingerSetResult {
    override val wire = "failed"
  }
}
