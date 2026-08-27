package com.mediasage.data.crypto

/**
 * Encrypts/decrypts the reflection note text at rest. Backed by a non-exportable key held in the
 * platform keystore (Android Keystore / iOS Keychain) — see the `actual` implementations.
 */
interface ReflectionNoteCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

expect fun createReflectionNoteCipher(): ReflectionNoteCipher
