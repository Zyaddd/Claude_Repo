package com.arabtv.iptv.data

object Pairing {
    /** No 0/O, 1/I/L or U/V, so codes are easy to read off a TV and type. */
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTWXYZ"
    const val CODE_LENGTH = 10

    fun normalize(input: String): String =
        input.uppercase().filter { it in ALPHABET }

    /** "ABCDE23456" -> "ABCDE-23456" */
    fun format(code: String): String =
        if (code.length == CODE_LENGTH) code.substring(0, 5) + "-" + code.substring(5) else code
}
