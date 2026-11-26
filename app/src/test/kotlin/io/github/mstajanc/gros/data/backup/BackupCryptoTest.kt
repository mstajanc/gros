package io.github.mstajanc.gros.data.backup

import java.security.SecureRandom
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.Test

class BackupCryptoTest {

    private val plain = "Groš – testovacie dáta 1 234,56 €".encodeToByteArray()
    private fun password(text: String = "správne heslo 123") = text.toCharArray()

    /** Rýchly počet iterácií pre testy; formát ho nesie v hlavičke. */
    private val fast = 1_000

    private fun assertError(expected: BackupError, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<BackupException> { block() }.error)
    }

    @Test
    fun `round trip returns the original bytes`() {
        val file = BackupCrypto.encrypt(plain, password(), iterations = fast)
        assertContentEquals(plain, BackupCrypto.decrypt(file, password()))
    }

    @Test
    fun `default key derivation uses 600 000 iterations and round trips`() {
        assertEquals(600_000, BackupCrypto.DEFAULT_ITERATIONS)
        val file = BackupCrypto.encrypt(plain, password())
        assertEquals(600_000, BackupCrypto.readHeader(file).iterations)
        assertContentEquals(plain, BackupCrypto.decrypt(file, password()))
    }

    @Test
    fun `header has magic, format version, iterations, salt and nonce`() {
        val file = BackupCrypto.encrypt(plain, password(), iterations = fast)
        assertContentEquals("GROS".encodeToByteArray(), file.copyOfRange(0, 4))
        val header = BackupCrypto.readHeader(file)
        assertEquals(1, header.formatVersion)
        assertEquals(fast, header.iterations)
        assertEquals(16, header.salt.size)
        assertEquals(12, header.nonce.size)
        // Šifrovaný text = vstup + 16 bajtov autentifikačnej značky GCM.
        assertEquals(BackupCrypto.HEADER_SIZE + plain.size + 16, file.size)
    }

    @Test
    fun `salt and nonce are random for every export`() {
        val a = BackupCrypto.encrypt(plain, password(), iterations = fast)
        val b = BackupCrypto.encrypt(plain, password(), iterations = fast, random = SecureRandom())
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `wrong password is reported together with corruption`() {
        val file = BackupCrypto.encrypt(plain, password(), iterations = fast)
        assertError(BackupError.WRONG_PASSWORD_OR_CORRUPTED) { BackupCrypto.decrypt(file, password("iné heslo 1234")) }
    }

    @Test
    fun `changed ciphertext is detected`() {
        val file = BackupCrypto.encrypt(plain, password(), iterations = fast)
        file[file.size - 20] = (file[file.size - 20].toInt() xor 1).toByte()
        assertError(BackupError.WRONG_PASSWORD_OR_CORRUPTED) { BackupCrypto.decrypt(file, password()) }
    }

    @Test
    fun `changed header is detected because it is authenticated`() {
        val file = BackupCrypto.encrypt(plain, password(), iterations = fast)
        // Bajt soli (za magic, verziou a iteráciami).
        file[10] = (file[10].toInt() xor 1).toByte()
        assertError(BackupError.WRONG_PASSWORD_OR_CORRUPTED) { BackupCrypto.decrypt(file, password()) }
    }

    @Test
    fun `foreign or truncated file is not a backup`() {
        assertError(BackupError.NOT_A_BACKUP) { BackupCrypto.decrypt("PK\u0003\u0004 zip".encodeToByteArray(), password()) }
        assertError(BackupError.NOT_A_BACKUP) { BackupCrypto.decrypt("GRO".encodeToByteArray(), password()) }
        val file = BackupCrypto.encrypt(plain, password(), iterations = fast)
        assertError(BackupError.NOT_A_BACKUP) { BackupCrypto.decrypt(file.copyOf(BackupCrypto.HEADER_SIZE + 4), password()) }
    }

    @Test
    fun `newer file format is rejected`() {
        val file = BackupCrypto.encrypt(plain, password(), iterations = fast)
        file[4] = 2
        assertError(BackupError.UNSUPPORTED_FORMAT) { BackupCrypto.decrypt(file, password()) }
    }

    @Test
    fun `absurd iteration count is rejected without deriving a key`() {
        val file = BackupCrypto.encrypt(plain, password(), iterations = fast)
        // Iterácie sú bajty 5–8 (big-endian); 0x7F000000 by odvodzovanie kľúča zablokovalo na hodiny.
        file[5] = 0x7F
        assertError(BackupError.WRONG_PASSWORD_OR_CORRUPTED) { BackupCrypto.decrypt(file, password()) }
    }
}
