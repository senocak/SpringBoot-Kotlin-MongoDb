package com.github.senocak.auth.util

object AppConstants {
    const val DEFAULT_PAGE_NUMBER = "0"
    const val DEFAULT_PAGE_SIZE = "10"
    const val TOKEN_HEADER_NAME = "Authorization"
    const val TOKEN_PREFIX = "Bearer "
    const val ADMIN = "ADMIN"
    const val USER = "USER"
    const val SECURITY_SCHEME_NAME = "bearerAuth"
}

private class RandomStringGenerator(
    private val length: Int = 1,
) {
    private val symbols: CharArray = ALPHA_NUM.toCharArray()
    private val buf: CharArray = CharArray(size = length)

    fun next(): String =
        buf.indices
            .forEach { buf[it] = symbols[symbols.indices.random()] }
            .run { String(chars = buf) }

    companion object {
        private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val DIGITS = "0123456789"
        private val ALPHA_NUM: String = UPPER + UPPER.lowercase() + DIGITS
    }
}

fun Int.randomStringGenerator(): String = RandomStringGenerator(length = this).next()
