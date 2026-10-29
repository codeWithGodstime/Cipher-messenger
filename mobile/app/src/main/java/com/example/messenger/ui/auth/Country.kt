package com.example.messenger.ui.auth

data class Country(
    val name: String,
    val flag: String,
    val dialCode: String,
    val digitCount: Int,
)

val Countries = listOf(
    Country("Nigeria", "🇳🇬", "+234", 10),
)

fun formatNational(digits: String, country: Country): String {
    if (digits.isEmpty()) return ""
    if (country.dialCode == "+1") {
        return when {
            digits.length <= 3 -> "($digits"
            digits.length <= 6 -> "(${digits.take(3)}) ${digits.drop(3)}"
            else -> "(${digits.take(3)}) ${digits.substring(3, 6)}-${digits.drop(6)}"
        }
    }
    return digits.chunked(3).joinToString(" ")
}

fun Country.displayNumber(digits: String): String {
    val national = formatNational(digits, this)
    return if (national.isEmpty()) dialCode else "$dialCode $national"
}

fun Country.e164(digits: String): String = dialCode + digits.filter { it.isDigit() }
