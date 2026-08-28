package com.mediasage.data.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import kotlin.io.encoding.Base64

/**
 * Encrypts/decrypts the reflection note text with the account's shared key (MS-740) — pure
 * commonMain, no expect/actual needed, since cryptography-kotlin's optimal provider resolves to
 * the platform's native crypto (JCA on Android, CryptoKit on iOS) internally. Unlike
 * [ReflectionNoteCipher] (which wraps a non-exportable, device-local key), this key is portable
 * across every device signed into the same account, which is what makes cross-device sync of the
 * note actually work.
 */
object SharedNoteCipher {
    private val aesGcm by lazy { CryptographyProvider.Default.get(AES.GCM) }

    suspend fun generateKey(): ByteArray =
        aesGcm.keyGenerator().generateKey().encodeToByteArray(AES.Key.Format.RAW)

    suspend fun encrypt(plaintext: String, rawKey: ByteArray): String {
        val key = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, rawKey)
        val combined = key.cipher().encrypt(plaintext.encodeToByteArray())
        return Base64.encode(combined)
    }

    suspend fun decrypt(ciphertext: String, rawKey: ByteArray): String {
        val key = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, rawKey)
        val plaintext = key.cipher().decrypt(Base64.decode(ciphertext))
        return plaintext.decodeToString()
    }
}
