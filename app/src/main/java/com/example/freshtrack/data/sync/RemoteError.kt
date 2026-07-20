package com.example.freshtrack.data.sync

/**
 * Transport-agnostic failure from the remote store.
 *
 * The sync engine reasons about *what to do next*, not about Firestore. Keeping
 * the backend's exception types out of it means the engine has no Android or
 * Firebase dependency and can be unit tested on the JVM — and swapping backends
 * later would not touch the retry logic.
 */
sealed class RemoteError(message: String, cause: Throwable?) : Exception(message, cause) {

    /** Rules refused the write. Expected for a free account; retrying is futile. */
    class PermissionDenied(cause: Throwable? = null) :
        RemoteError("Remote write refused by security rules", cause)

    /** Offline, timed out, throttled. Worth another attempt later. */
    class Transient(cause: Throwable? = null) :
        RemoteError("Temporary remote failure", cause)

    /** Anything else. Retrying is not expected to help. */
    class Permanent(cause: Throwable? = null) :
        RemoteError("Unrecoverable remote failure", cause)
}
