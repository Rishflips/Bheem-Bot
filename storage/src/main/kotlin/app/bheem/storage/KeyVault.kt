package app.bheem.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyLostException(message: String, cause: Throwable? = null) : Exception(message, cause)

class KeyVault(private val context: Context, val alias: String = DEFAULT_ALIAS, val fileName: String = DEFAULT_FILE) {
    private val keyFile: File get() = File(context.noBackupFilesDir, fileName)

    fun hasKey(): Boolean = keyFile.exists()

    fun databaseKey(): ByteArray = synchronized(lock) { databaseKeyLocked() }

    private fun databaseKeyLocked(): ByteArray {
        val file = keyFile
        if (file.exists()) return unwrap(file.readBytes())
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val temp = File(file.path + ".part")
        java.io.FileOutputStream(temp).use { out ->
            out.write(wrap(key))
            out.fd.sync()
        }
        check(temp.renameTo(file)) { "could not store the database key" }
        return key
    }

    fun erase() = synchronized(lock) { eraseLocked() }

    private fun eraseLocked() {
        keyFile.delete()
        File(keyFile.path + ".part").delete()
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    private fun wrap(key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        return cipher.iv + cipher.doFinal(key)
    }

    private fun unwrap(stored: ByteArray): ByteArray = try {
        val iv = stored.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(TAG_BITS, iv)) }
        cipher.doFinal(stored, IV_BYTES, stored.size - IV_BYTES)
    } catch (e: Exception) {
        throw KeyLostException("the database key cannot be unwrapped; the data cannot be read and must be erased", e)
    }

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return try {
            generate(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BYTES * 8)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
    }

    companion object {
        private val lock = Any()
        const val DEFAULT_ALIAS = "bheem.database.key"
        const val DEFAULT_FILE = "database.key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
