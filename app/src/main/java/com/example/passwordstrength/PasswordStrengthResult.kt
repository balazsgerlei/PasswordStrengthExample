package com.example.passwordstrength

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PasswordStrengthResult(
    val passwordStrength: PasswordStrength,
    val calculationTimeMillis: Long,
) : Parcelable
