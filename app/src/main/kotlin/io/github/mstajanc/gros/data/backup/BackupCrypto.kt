package io.github.mstajanc.gros.data.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Dôvod, prečo sa záloha nedá otvoriť alebo obnoviť (hlásenia podľa G6). */
enum class BackupError {
    /** Súbor nemá hlavičku zálohy Groš (cudzí alebo orezaný súbor). */
    NOT_A_BACKUP,

    /** Súbor je zo novšej verzie formátu, ktorú táto appka nepozná. */
    UNSUPPORTED_FORMAT,

    /** Nesprávne heslo alebo poškodený obsah; AES-GCM ich nevie rozlíšiť. */
    WRONG_PASSWORD_OR_CORRUPTED,

    /** Záloha je z novšej verzie schémy databázy. */
    NEWER_SCHEMA,

    /** Obsah sa podarilo dešifrovať, ale neprešiel kontrolou (referencie, invarianty). */
    INVALID_CONTENT,
}

class BackupException(val error: BackupError, cause: Throwable? = null) : Exception(error.name, cause)

/**
 * Šifrovanie súboru zálohy: AES-256-GCM, kľúč z hesla cez PBKDF2-HMAC-SHA256.
 *
 * Formát: `GROS` (4 B) · verzia formátu (1 B) · počet iterácií (4 B, big-endian) · soľ (16 B) ·
 * nonce (12 B) · šifrovaný text so značkou GCM. Celá hlavička vstupuje do šifrovania ako overené
 * dáta (AAD), takže zmena ktoréhokoľvek bajtu sa odhalí. Všetko prebieha v pamäti.
 */
object BackupCrypto {

    const val FORMAT_VERSION = 1
    const val DEFAULT_ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 1_000
    private const val MAX_ITERATIONS = 10_000_000
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private val MAGIC = "GROS".encodeToByteArray()
    const val HEADER_SIZE = 4 + 1 + 4 + SALT_SIZE + NONCE_SIZE

    class Header(val formatVersion: Int, val iterations: Int, val salt: ByteArray, val nonce: ByteArray)

    fun encrypt(
        plain: ByteArray,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS)
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .put(MAGIC)
            .put(FORMAT_VERSION.toByte())
            .putInt(iterations)
            .put(salt)
            .put(nonce)
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        val header = readHeader(file)
        if (header.iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw BackupException(BackupError.WRONG_PASSWORD_OR_CORRUPTED)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(password, header.salt, header.iterations),
                GCMParameterSpec(TAG_BITS, header.nonce),
            )
            cipher.updateAAD(file, 0, HEADER_SIZE)
            cipher.doFinal(file, HEADER_SIZE, file.size - HEADER_SIZE)
        } catch (e: GeneralSecurityException) {
            throw BackupException(BackupError.WRONG_PASSWORD_OR_CORRUPTED, e)
        }
    }

    /** Prečíta hlavičku; cudzí, orezaný alebo novší súbor odmietne ešte pred odvodením kľúča. */
    fun readHeader(file: ByteArray): Header {
        if (file.size < HEADER_SIZE + TAG_BITS / 8 || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BackupException(BackupError.NOT_A_BACKUP)
        }
        val buffer = ByteBuffer.wrap(file, MAGIC.size, HEADER_SIZE - MAGIC.size)
        val version = buffer.get().toInt() and 0xFF
        if (version != FORMAT_VERSION) throw BackupException(BackupError.UNSUPPORTED_FORMAT)
        val iterations = buffer.getInt()
        val salt = ByteArray(SALT_SIZE).also { buffer.get(it) }
        val nonce = ByteArray(NONCE_SIZE).also { buffer.get(it) }
        return Header(version, iterations, salt, nonce)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }
}
