package com.holyradius.nativecore.util

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Minimal Task.await() so we don't need kotlinx-coroutines-play-services. */
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
  addOnSuccessListener { cont.resume(it) }
  addOnFailureListener { cont.resumeWithException(it) }
  addOnCanceledListener { cont.cancel() }
}
