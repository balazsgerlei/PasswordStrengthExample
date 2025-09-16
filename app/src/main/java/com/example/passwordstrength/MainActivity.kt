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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.javascriptengine.JavaScriptSandbox
import androidx.lifecycle.lifecycleScope
import com.example.passwordstrength.ui.theme.PasswordStrengthTheme
import com.google.common.util.concurrent.ListenableFuture
import com.nulabinc.zxcvbn.Zxcvbn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.NumberFormatException
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var passwordStrengthCalculatorToUse: PasswordStrengthCalculator =
        PasswordStrengthCalculator.ZXCVBN4J

    private val zxcvbn: Zxcvbn by lazy {
        Zxcvbn()
    }

    private val zxcvbnTsScript: String by lazy {
        assets.open(SCRIPT_FILE_NAME).bufferedReader().use { it.readText() }
    }

    private val webView: WebView by lazy {
        WebView(this).apply {
            @SuppressLint("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
        }
    }

    private var jsSandboxCreated = false
    private val jsSandboxFuture: ListenableFuture<JavaScriptSandbox> by lazy {
        jsSandboxCreated = true
        JavaScriptSandbox.createConnectedInstanceAsync(this)
    }

    @OptIn(FlowPreview::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var password by rememberSaveable { mutableStateOf("") }
            var showPassword by remember { mutableStateOf(false) }
            var passwordStrength by rememberSaveable {
                mutableStateOf(
                    PasswordStrengthResult(
                        PasswordStrength.TOO_GUESSABLE,
                        calculationTimeMillis = 0L,
                    )
                )
            }

            LaunchedEffect(password) {
                snapshotFlow { password }.debounce(500L).collectLatest { debouncedPassword ->
                    calculatePasswordStrength(debouncedPassword) { passwordStrength = it }
                }
            }

            PasswordStrengthTheme {
                PasswordStrengthScreen(
                    onPasswordCalculatorSelectionChange = { passwordStrengthCalculator ->
                        passwordStrengthCalculatorToUse = passwordStrengthCalculator
                        calculatePasswordStrength(password) { passwordStrength = it }
                    },
                    password = password,
                    showPassword = showPassword,
                    onShowPasswordClicked = { showPassword = !showPassword },
                    onPasswordChange = { password = it },
                    passwordStrength = passwordStrength,
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (jsSandboxCreated) {
            jsSandboxFuture.get()?.close()
        }
    }

    private fun calculatePasswordStrength(
        password: String,
        onPasswordStrengthCalculated: (PasswordStrengthResult) -> Unit,
    ) {
        val startMillis = System.currentTimeMillis()
        if (password.isBlank()) {
            onPasswordStrengthCalculated(
                PasswordStrengthResult(
                    PasswordStrength.TOO_GUESSABLE,
                    calculationTimeMillis = System.currentTimeMillis() - startMillis
                )
            )
        } else {
            when(passwordStrengthCalculatorToUse) {
                PasswordStrengthCalculator.ZXCVBN4J -> {
                    lifecycleScope.launch {
                        val passwordStrength: PasswordStrength = withContext(Dispatchers.Default) {
                            val score = zxcvbn.measure(password).score
                            passwordStrengthFromScore(score)
                        }
                        onPasswordStrengthCalculated(
                            PasswordStrengthResult(
                                passwordStrength,
                                calculationTimeMillis = System.currentTimeMillis() - startMillis
                            )
                        )
                    }
                }
                PasswordStrengthCalculator.ZXCVBNTS_WITH_WEBVIEW -> {
                    lifecycleScope.launch {
                        val script = withContext(Dispatchers.Default) {
                            zxcvbnTsScript.replace("\$arg1", password)
                        }
                        webView.evaluateJavascript(script) { result ->
                            lifecycleScope.launch {
                                val passwordStrength: PasswordStrength = withContext(Dispatchers.Default) {
                                    passwordStrengthFromScore(result.replace("\"", ""))
                                }
                                onPasswordStrengthCalculated(
                                    PasswordStrengthResult(
                                        passwordStrength,
                                        calculationTimeMillis = System.currentTimeMillis() - startMillis
                                    )
                                )
                            }
                        }
                    }
                }
                PasswordStrengthCalculator.ZXCVBNTS_WITH_JAVASCRIPT_ENGINE -> {
                    lifecycleScope.launch {
                        val passwordStrength: PasswordStrength = withContext(Dispatchers.Default) {
                            val script = zxcvbnTsScript.replace("\$arg1", password)
                            val jsSandbox = jsSandboxFuture.await()
                            val jsIsolate = jsSandbox.createIsolate()
                            val result = jsIsolate.evaluateJavaScriptAsync(script).await()
                            passwordStrengthFromScore(result)
                        }
                        onPasswordStrengthCalculated(
                            PasswordStrengthResult(
                                passwordStrength,
                                calculationTimeMillis = System.currentTimeMillis() - startMillis
                            )
                        )
                    }
                }
            }
        }
    }

    private fun passwordStrengthFromScore(score: String): PasswordStrength = try {
        passwordStrengthFromScore(score = minOf(score.toInt(), 4))
    } catch (_: NumberFormatException) {
        PasswordStrength.VERY_GUESSABLE
    }

    private fun passwordStrengthFromScore(score: Int): PasswordStrength = PasswordStrength.entries.firstOrNull { it.score == score } ?: PasswordStrength.VERY_GUESSABLE

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
    passwordStrength: PasswordStrengthResult
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
                progress = { passwordStrength.passwordStrength.score / 4.0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
            )
            Text(
                "Calculation time: ${passwordStrength.calculationTimeMillis} ms",
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
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
fun PasswordStrengthScreenPreview() {
    PasswordStrengthTheme {
        PasswordStrengthScreen(
            onPasswordCalculatorSelectionChange = {},
            password = "password",
            showPassword = false,
            onShowPasswordClicked = {},
            onPasswordChange = {},
            passwordStrength = PasswordStrengthResult(PasswordStrength.TOO_GUESSABLE, 100L),
        )
    }
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
