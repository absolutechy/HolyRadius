package com.holyradius.nativecore.diag

import androidx.core.content.FileProvider

/** Own subclass so the manifest entry never collides with another library's FileProvider. */
class DiagFileProvider : FileProvider()
