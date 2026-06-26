package dev.denza.adbbridge

import java.security.SecureRandom

object AccessCodeGenerator {
    private val random = SecureRandom()

    fun generate(): String {
        val value = random.nextInt(100_000_000)
        return value.toString().padStart(8, '0')
    }
}
