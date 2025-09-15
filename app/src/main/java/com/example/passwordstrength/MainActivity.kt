package com.example.passwordstrength

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.passwordstrength.ui.theme.PasswordStrengthTheme
import com.nulabinc.zxcvbn.Zxcvbn

class MainActivity : ComponentActivity() {

    private var passwordStrengthCalculatorToUse: PasswordStrengthCalculator =
        PasswordStrengthCalculator.ZXCVBN4J

    private lateinit var zxcvbn: Zxcvbn

    private lateinit var webView: WebView
    private val zxcvbnTsScript: String by lazy {
        assets.open(SCRIPT_FILE_NAME).bufferedReader().use { it.readText() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        zxcvbn = Zxcvbn()
        webView = WebView(this).apply {
            @SuppressLint("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
        }

        setContent {
            var password by rememberSaveable { mutableStateOf("") }
            var showPassword by remember { mutableStateOf(false) }
            var passwordStrength by rememberSaveable { mutableFloatStateOf(0f) }

            PasswordStrengthTheme {
                PasswordStrengthScreen(
                    onPasswordCalculatorSelectionChange = { passwordStrengthCalculator ->
                        passwordStrengthCalculatorToUse = passwordStrengthCalculator
                        calculatePasswordStrength(password) { passwordStrength = it.score / 4.0f }
                    },
                    password = password,
                    showPassword = showPassword,
                    onShowPasswordClicked = { showPassword = !showPassword },
                    onPasswordChange = { newPassword ->
                        password = newPassword
                        calculatePasswordStrength(newPassword) { passwordStrength = it.score / 4.0f }
                    },
                    passwordStrength = passwordStrength,
                )
            }
        }
    }

    private fun calculatePasswordStrength(
        password: String,
        onPasswordStrengthCalculated: (PasswordStrength) -> Unit,
    ) {
        when(passwordStrengthCalculatorToUse) {
            PasswordStrengthCalculator.ZXCVBN4J -> {
                val score = zxcvbn.measure(password).score
                val passwordStrength = PasswordStrength.entries.firstOrNull { it.score == score } ?: PasswordStrength.VERY_GUESSABLE
                onPasswordStrengthCalculated(passwordStrength)
            }
            PasswordStrengthCalculator.ZXCVBNTS_WITH_WEBVIEW -> {
                val script = zxcvbnTsScript.replace("\$arg1", password)
                webView.evaluateJavascript(script) { result ->
                    val score = minOf(result.toInt(), 4)
                    val passwordStrength = PasswordStrength.entries.firstOrNull { it.score == score } ?: PasswordStrength.VERY_GUESSABLE
                    onPasswordStrengthCalculated(passwordStrength)
                }
            }
            PasswordStrengthCalculator.ZXCVBNTS_WITH_JAVASCRIPT_ENGINE -> {

            }
        }
    }

    companion object {
        const val SCRIPT_FILE_NAME = "zxcvbn-ts.js"

        val passwordStrengthCalculatorLabelProvider: (PasswordStrengthCalculator) -> String = { passwordCalculator ->
            when(passwordCalculator) {
                PasswordStrengthCalculator.ZXCVBN4J -> "Native"
                PasswordStrengthCalculator.ZXCVBNTS_WITH_WEBVIEW -> "WebView"
                PasswordStrengthCalculator.ZXCVBNTS_WITH_JAVASCRIPT_ENGINE -> "JSEngine"
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordStrengthScreen(
    onPasswordCalculatorSelectionChange: (PasswordStrengthCalculator) -> Unit,
    password: String,
    showPassword: Boolean,
    onShowPasswordClicked: () -> Unit,
    onPasswordChange: (String) -> Unit,
    passwordStrength: Float
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Password Strength") }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column (
            modifier = Modifier.padding(innerPadding),
        ) {
            SegmentedButtonRow(
                items = PasswordStrengthCalculator.entries.toList(),
                labelProvider = MainActivity.passwordStrengthCalculatorLabelProvider,
                onSelectionChange = onPasswordCalculatorSelectionChange,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
            )
            PasswordTextField(
                password = password,
                showPassword = showPassword,
                onShowPasswordClicked = onShowPasswordClicked,
                onPasswordChange = onPasswordChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
            )
            LinearProgressIndicator(
                progress = { passwordStrength },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
            )
        }
    }
}

@Composable
fun <T> SegmentedButtonRow(
    items: List<T>,
    labelProvider: (T) -> String,
    onSelectionChange: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    SingleChoiceSegmentedButtonRow(
        modifier = modifier,
    ) {
        items.forEachIndexed { index, item ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = items.size
                ),
                onClick = {
                    selectedIndex = index
                    onSelectionChange(item)
                },
                selected = index == selectedIndex,
                label = { Text(labelProvider.invoke(item)) }
            )
        }
    }
}

@Composable
fun PasswordTextField(
    password: String,
    showPassword: Boolean,
    onShowPasswordClicked: () -> Unit,
    onPasswordChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Enter password") },
        visualTransformation = if (showPassword) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = modifier,
        trailingIcon = {
            Icon(
                if (showPassword) {
                    Icons.Filled.Visibility
                } else {
                    Icons.Filled.VisibilityOff
                },
                contentDescription = "Toggle password visibility",
                modifier = Modifier.clickable { onShowPasswordClicked() })
        }
    )
}

@Preview(showBackground = true)
@Composable
fun SegmentedButtonRowPreview() {
    PasswordStrengthTheme {
        SegmentedButtonRow(
            items = PasswordStrengthCalculator.entries.toList(),
            labelProvider = MainActivity.passwordStrengthCalculatorLabelProvider,
            onSelectionChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PasswordStrengthScreenPreview() {
    PasswordStrengthTheme {
        PasswordStrengthScreen(
            onPasswordCalculatorSelectionChange = {},
            password = "password",
            showPassword = false,
            onShowPasswordClicked = {},
            onPasswordChange = {},
            passwordStrength = 0.5f,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PasswordTextFieldHiddenPreview() {
    PasswordStrengthTheme {
        PasswordTextField(
            password = "password",
            showPassword = false,
            onShowPasswordClicked = {},
            onPasswordChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PasswordTextFieldRevealedPreview() {
    PasswordStrengthTheme {
        PasswordTextField(
            password = "password",
            showPassword = true,
            onShowPasswordClicked = {},
            onPasswordChange = {},
        )
    }
}
