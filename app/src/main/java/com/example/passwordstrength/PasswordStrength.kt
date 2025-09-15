package com.example.passwordstrength

enum class PasswordStrength(val score: Int) {
    TOO_GUESSABLE(0),
    VERY_GUESSABLE(1),
    SOMEWHAT_GUESSABLE(2),
    SAFELY_UNGUESSABLE(3),
    VERY_UNGUESSABLE(4),
}
