package de.sulo.manitr

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Passwortbasierte Verschlüsselung (PBKDF2-HMAC-SHA256 + AES-256-GCM) für Backup-Dateien. */
object BackupCrypto {
    private val MAGIC = "SULOBK1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val ITERATIONS = 210_000

    class BadPasswordOrFile(message: String) : Exception(message)

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also(rnd::nextBytes)
        val iv = ByteArray(IV_LEN).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derive(password, salt), "AES"), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (data.size < MAGIC.size + SALT_LEN + IV_LEN + 16 || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BadPasswordOrFile("Keine gültige Sülo-ManiTR-Backup-Datei.")
        }
        var o = MAGIC.size
        val salt = data.copyOfRange(o, o + SALT_LEN); o += SALT_LEN
        val iv = data.copyOfRange(o, o + IV_LEN); o += IV_LEN
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derive(password, salt), "AES"), GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(data, o, data.size - o)
        } catch (e: java.security.GeneralSecurityException) {
            throw BadPasswordOrFile("Falsches Passwort oder beschädigte Datei.")
        }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
