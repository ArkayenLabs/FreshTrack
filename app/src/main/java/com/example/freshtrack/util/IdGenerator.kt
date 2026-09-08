package com.example.freshtrack.util

import java.util.UUID

/**
 * Source of the ids the app mints.
 *
 * Behind an interface so tests can assert on the exact operation id that was
 * enqueued. Idempotency depends entirely on those ids being what we think they
 * are, so it is worth being able to check rather than assume.
 */
interface IdGenerator {
    fun newId(): String

    companion object {
        val Uuid: IdGenerator = UuidGenerator()
    }
}

private class UuidGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
