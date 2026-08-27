package com.mediasage.data.crypto

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.create
import platform.posix.memcpy
import platform.Security.SecItemCopyMatching
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateDecryptedData
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyRef
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecKeyAlgorithmECIESEncryptionStandardX963SHA256AESGCM
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecReturnRef
import kotlin.io.encoding.Base64

private const val KEY_TAG = "com.mediasage.reflectionNoteKey"
private const val KEY_SIZE_BITS = 256

// kSecClass/kSecAttrKeyType/etc. are CFStringRef constants — NSMutableDictionary's setObject
// needs an actual NSString/NSCopying instance, so bridge each constant once before use. This is
// the standard Kotlin/Native idiom for using CoreFoundation constants as Foundation objects; it
// is safe to call repeatedly on these static, process-lifetime constants.
@OptIn(ExperimentalForeignApi::class)
private fun CFStringRef?.asNSString(): NSString = CFBridgingRelease(this) as NSString

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun applicationTag(): NSData = KEY_TAG.encodeToByteArray().toNSData()

// The CFDictionaryRef/SecKeyRef casts below bridge toll-free-bridged Foundation/CoreFoundation
// types — a documented Kotlin/Native interop pattern the compiler can't verify statically.
@Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST")
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun findExistingPrivateKey(tag: NSData): SecKeyRef? = memScoped {
    val query = NSMutableDictionary().apply {
        setObject(kSecClassKey.asNSString(), forKey = kSecClass.asNSString())
        setObject(tag, forKey = kSecAttrApplicationTag.asNSString())
        setObject(kSecAttrKeyTypeECSECPrimeRandom.asNSString(), forKey = kSecAttrKeyType.asNSString())
        setObject(true, forKey = kSecReturnRef.asNSString())
    }
    val result = alloc<CFTypeRefVar>()
    val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
    if (status == 0) result.value as SecKeyRef? else null
}

@Suppress("CAST_NEVER_SUCCEEDS")
@OptIn(ExperimentalForeignApi::class)
private fun generatePrivateKey(tag: NSData): SecKeyRef {
    val privateKeyAttrs = NSMutableDictionary().apply {
        setObject(true, forKey = kSecAttrIsPermanent.asNSString())
        setObject(tag, forKey = kSecAttrApplicationTag.asNSString())
    }
    val attributes = NSMutableDictionary().apply {
        setObject(kSecAttrKeyTypeECSECPrimeRandom.asNSString(), forKey = kSecAttrKeyType.asNSString())
        setObject(KEY_SIZE_BITS, forKey = kSecAttrKeySizeInBits.asNSString())
        setObject(privateKeyAttrs, forKey = kSecPrivateKeyAttrs.asNSString())
    }
    val privateKey = SecKeyCreateRandomKey(attributes as CFDictionaryRef, null)
    requireNotNull(privateKey) { "Failed to generate reflection note EC key pair" }
    return privateKey
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val byteArray = ByteArray(length.toInt())
    if (length > 0u) {
        byteArray.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
    return byteArray
}

private class IosReflectionNoteCipher : ReflectionNoteCipher {
    @OptIn(ExperimentalForeignApi::class)
    private val privateKey: SecKeyRef by lazy {
        val tag = applicationTag()
        findExistingPrivateKey(tag) ?: generatePrivateKey(tag)
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    @OptIn(ExperimentalForeignApi::class)
    override fun encrypt(plaintext: String): String {
        val publicKey = requireNotNull(SecKeyCopyPublicKey(privateKey)) { "Missing public key" }
        val ciphertext = SecKeyCreateEncryptedData(
            publicKey,
            kSecKeyAlgorithmECIESEncryptionStandardX963SHA256AESGCM,
            plaintext.encodeToByteArray().toNSData() as CFDataRef,
            null
        )
        requireNotNull(ciphertext) { "Failed to encrypt reflection note" }
        return Base64.encode((ciphertext as NSData).toByteArray())
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    @OptIn(ExperimentalForeignApi::class)
    override fun decrypt(ciphertext: String): String {
        val plaintext = SecKeyCreateDecryptedData(
            privateKey,
            kSecKeyAlgorithmECIESEncryptionStandardX963SHA256AESGCM,
            Base64.decode(ciphertext).toNSData() as CFDataRef,
            null
        )
        requireNotNull(plaintext) { "Failed to decrypt reflection note" }
        return (plaintext as NSData).toByteArray().decodeToString()
    }
}

actual fun createReflectionNoteCipher(): ReflectionNoteCipher = IosReflectionNoteCipher()
