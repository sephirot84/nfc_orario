package it.nfctimetracker.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Pin {
    private const val ITERATIONS = 120_000
    fun hash(pin: String): String {
        require(pin.matches(Regex("[0-9]{4,12}"))) { "Il PIN deve contenere da 4 a 12 cifre" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "$ITERATIONS:${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(derive(pin, salt, ITERATIONS))}"
    }
    fun verify(pin: String, encoded: String): Boolean = try {
        val parts = encoded.split(':')
        val iterations = parts[0].toInt().also { require(it in 100_000..200_000) }
        MessageDigest.isEqual(Base64.getDecoder().decode(parts[2]), derive(pin, Base64.getDecoder().decode(parts[1]), iterations))
    } catch (_: Exception) { false }
    fun validEncoding(encoded: String): Boolean = try {
        val p = encoded.split(':')
        p.size == 3 && p[0].toInt() in 100_000..200_000 && Base64.getDecoder().decode(p[1]).size == 16 && Base64.getDecoder().decode(p[2]).size == 32
    } catch (_: Exception) { false }
    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
}
