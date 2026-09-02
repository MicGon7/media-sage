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
import platform.CoreFoundation.CFErrorRef
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
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

// SecKeyCreateEncryptedData/SecKeyCreateDecryptedData previously passed `null` for the error
// out-param, so a failure surfaced only as a generic "Failed to en/decrypt" message with no way
// to tell a genuine Security-framework failure (e.g. a stale/mismatched Keychain key) apart from
// any other cause. Bridging the CFErrorRef to NSError here (the CF "create rule" applies — this
// call site owns the returned error and CFBridgingRelease hands it to ARC) surfaces the real
// reason in the thrown message instead.
@OptIn(ExperimentalForeignApi::class)
private fun CFErrorRef?.describeSecurityError(): String =
    (CFBridgingRelease(this) as? NSError)?.localizedDescription ?: "no error info returned"

// NSMutableDictionary/NSData are Objective-C objects — the Kotlin compiler's CAST_NEVER_SUCCEEDS
// diagnostic on a raw `as CFDictionaryRef`/`as CFDataRef` is correct, not a false positive to
// suppress: it threw a real ClassCastException at runtime (MS-741). CFBridgingRetain is the
// actual bridge from an Objective-C object to its toll-free-bridged Core Foundation pointer (the
// reverse of CFBridgingRelease in asNSString() above); the object is only needed for the
// duration of the Security call, so `finally` always balances the retain with a release even if
// [block] throws.
@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private inline fun <R> NSMutableDictionary.useAsCfDictionary(block: (CFDictionaryRef) -> R): R {
    val cfDictionary = CFBridgingRetain(this) as CFDictionaryRef
    try {
        return block(cfDictionary)
    } finally {
        CFBridgingRelease(cfDictionary)
    }
}

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private inline fun <R> NSData.useAsCfData(block: (CFDataRef) -> R): R {
    val cfData = CFBridgingRetain(this) as CFDataRef
    try {
        return block(cfData)
    } finally {
        CFBridgingRelease(cfData)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun findExistingPrivateKey(tag: NSData): SecKeyRef? = memScoped {
    val query = NSMutableDictionary().apply {
        setObject(kSecClassKey.asNSString(), forKey = kSecClass.asNSString())
        setObject(tag, forKey = kSecAttrApplicationTag.asNSString())
        setObject(kSecAttrKeyTypeECSECPrimeRandom.asNSString(), forKey = kSecAttrKeyType.asNSString())
        // A raw Kotlin `true` here boxes to a generic NSNumber that Security framework's
        // stricter attributes reject with a silent runtime warning rather than an error — see the
        // identical, more consequential case in generatePrivateKey below. NSNumber(bool = true)
        // is the real CFBoolean-backed value these attributes expect.
        setObject(NSNumber(bool = true), forKey = kSecReturnRef.asNSString())
    }
    val result = alloc<CFTypeRefVar>()
    val status = query.useAsCfDictionary { cfQuery -> SecItemCopyMatching(cfQuery, result.ptr) }
    if (status == 0) result.value as SecKeyRef? else null
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun generatePrivateKey(tag: NSData): SecKeyRef {
    val privateKeyAttrs = NSMutableDictionary().apply {
        // A raw Kotlin `true` boxes to a generic NSNumber, which Security framework rejected with
        // a silent runtime warning ("Value 1 for key perm is not bool") instead of an error — so
        // kSecAttrIsPermanent was never actually honored, and SecKeyCreateRandomKey silently never
        // persisted the EC key to the Keychain. Every fresh process re-generated a new, different
        // key, breaking decryption of anything wrapped under a previous run's key (MS-741).
        setObject(NSNumber(bool = true), forKey = kSecAttrIsPermanent.asNSString())
        setObject(tag, forKey = kSecAttrApplicationTag.asNSString())
    }
    val attributes = NSMutableDictionary().apply {
        setObject(kSecAttrKeyTypeECSECPrimeRandom.asNSString(), forKey = kSecAttrKeyType.asNSString())
        setObject(KEY_SIZE_BITS, forKey = kSecAttrKeySizeInBits.asNSString())
        setObject(privateKeyAttrs, forKey = kSecPrivateKeyAttrs.asNSString())
    }
    val privateKey = attributes.useAsCfDictionary { cfAttributes -> SecKeyCreateRandomKey(cfAttributes, null) }
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

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun encrypt(plaintext: String): String = memScoped {
        // SecKeyCopyPublicKey is a Copy-rule call — this function, not the cached privateKey
        // lazy val, owns the reference, so it must release it once done.
        val publicKey = requireNotNull(SecKeyCopyPublicKey(privateKey)) { "Missing public key" }
        try {
            val cfError = alloc<CFErrorRefVar>()
            val ciphertext = plaintext.encodeToByteArray().toNSData().useAsCfData { cfPlaintext ->
                SecKeyCreateEncryptedData(publicKey, kSecKeyAlgorithmECIESEncryptionStandardX963SHA256AESGCM, cfPlaintext, cfError.ptr)
            }
            if (ciphertext == null) error("Failed to encrypt reflection note: ${cfError.value.describeSecurityError()}")
            Base64.encode((CFBridgingRelease(ciphertext) as NSData).toByteArray())
        } finally {
            CFRelease(publicKey)
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun decrypt(ciphertext: String): String = memScoped {
        val cfError = alloc<CFErrorRefVar>()
        val plaintext = Base64.decode(ciphertext).toNSData().useAsCfData { cfCiphertext ->
            SecKeyCreateDecryptedData(privateKey, kSecKeyAlgorithmECIESEncryptionStandardX963SHA256AESGCM, cfCiphertext, cfError.ptr)
        }
        if (plaintext == null) error("Failed to decrypt reflection note: ${cfError.value.describeSecurityError()}")
        (CFBridgingRelease(plaintext) as NSData).toByteArray().decodeToString()
    }
}

actual fun createReflectionNoteCipher(): ReflectionNoteCipher = IosReflectionNoteCipher()
