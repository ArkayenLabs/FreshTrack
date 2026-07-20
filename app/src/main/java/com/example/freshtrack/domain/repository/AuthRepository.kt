package com.example.freshtrack.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Interface defining authentication operations.
 */
interface AuthRepository {
    
    /**
     * Emits true if a user is currently logged in, false otherwise.
     */
    val isUserLoggedIn: Flow<Boolean>

    /**
     * Sign in with Email and Password
     */
    suspend fun signInWithEmail(email: String, password: String): Result<Unit>

    /**
     * Register a new user with Email and Password
     */
    suspend fun registerWithEmail(email: String, password: String): Result<Unit>

    /**
     * Sign in with Google using the provided ID token
     */
    suspend fun signInWithGoogle(idToken: String): Result<Unit>

    /**
     * Send a password reset email
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /**
     * Sign out the current user
     */
    suspend fun signOut()
}
