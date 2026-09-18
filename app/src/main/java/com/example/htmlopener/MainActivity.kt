package com.example.htmlopener

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private lateinit var browserManager: BrowserManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var fileServer: HtmlFileServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        browserManager = BrowserManager(this)
        settingsManager = SettingsManager(this)
        fileServer = HtmlFileServer(this)

        setContent {
            HtmlOpenerApp(browserManager, settingsManager, fileServer)
        }
    }

    override fun onDestroy() {
        fileServer.stop()
        super.onDestroy()
    }
}

@Composable
fun HtmlOpenerApp(
    browserManager: BrowserManager,
    settingsManager: SettingsManager,
    fileServer: HtmlFileServer
) {
    var showSettings by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Select an HTML file to begin.") }
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }

        selectedFile = uri
        selectedFileName = getFileName(context, uri)
        status = "HTML file selected."
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF080808)
        ) {
            if (showSettings) {
                SettingsScreen(
                    browserManager = browserManager,
                    settingsManager = settingsManager,
                    onBack = { showSettings = false }
                )
            } else {
                HomeScreen(
                    selectedFileName = selectedFileName,
                    status = status,
                    onSettings = { showSettings = true },
                    onSelectFile = {
                        filePicker.launch(arrayOf("text/html", "text/plain"))
                    },
                    onOpenFile = {
                        val uri = selectedFile

                        if (uri == null) {
                            Toast.makeText(
                                context,
                                "Select an HTML file first.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@HomeScreen
                        }

                        val browser = settingsManager.getDefaultBrowser()

                        if (
                            browser == null ||
                            !browserManager.isBrowserInstalled(browser)
                        ) {
                            Toast.makeText(
                                context,
                                "Select a default browser in Settings.",
                                Toast.LENGTH_LONG
                            ).show()
                            showSettings = true
                            return@HomeScreen
                        }

                        status = "Opening HTML file..."

                        if (!browserManager.openHtmlUri(browser, uri)) {
                            val localUrl = fileServer.start(uri)

                            if (browserManager.openUrl(browser, localUrl)) {
                                status = "HTML file opened."
                            } else {
                                status = "Unable to open browser."
                                Toast.makeText(
                                    context,
                                    "Unable to open the selected browser.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            status = "HTML file opened."
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    selectedFileName: String?,
    status: String,
    onSettings: () -> Unit,
    onSelectFile: () -> Unit,
    onOpenFile: () -> Unit
) {
    val selectFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        selectFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 55.dp, vertical = 35.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "HTML Opener",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            TvIconButton(onClick = onSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(Modifier.height(70.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Open HTML File",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(30.dp))

            TvButton(
                "Select HTML File",
                Modifier.focusRequester(selectFocusRequester),
                onSelectFile
            )

            Spacer(Modifier.height(20.dp))

            selectedFileName?.let {
                Text(it, color = Color.LightGray, fontSize = 18.sp)

                Spacer(Modifier.height(20.dp))

                TvButton("Open in Browser", onClick = onOpenFile)
            }

            Spacer(Modifier.height(30.dp))

            Text(status, color = Color.Gray, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SettingsScreen(
    browserManager: BrowserManager,
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val browsers = remember { browserManager.getInstalledBrowsers() }
    var selectedBrowser by remember {
        mutableStateOf(settingsManager.getDefaultBrowser())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 55.dp, vertical = 35.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Settings",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            TvButton("Back", onClick = onBack)
        }

        Spacer(Modifier.height(45.dp))

        Text(
            "Default Browser",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(20.dp))

        if (browsers.isEmpty()) {
            Text(
                "No compatible browser was detected.",
                color = Color.LightGray,
                fontSize = 18.sp
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(browsers, key = { it.packageName }) { browser ->
                    BrowserRow(
                        browser = browser,
                        selected = selectedBrowser == browser.packageName,
                        onClick = {
                            selectedBrowser = browser.packageName
                            settingsManager.setDefaultBrowser(browser.packageName)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserRow(
    browser: BrowserInfo,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(
                if (focused) Color(0xFF303030) else Color(0xFF181818),
                RoundedCornerShape(10.dp)
            )
            .border(
                BorderStroke(
                    if (focused) 3.dp else 1.dp,
                    if (focused) Color.White else Color(0xFF404040)
                ),
                RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (selected) "●" else "○",
            color = Color.White,
            fontSize = 24.sp
        )

        Spacer(Modifier.width(18.dp))

        Text(
            browser.label,
            color = Color.White,
            fontSize = 19.sp
        )
    }
}

@Composable
private fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier = modifier
            .width(280.dp)
            .height(65.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) Color.White else Color(0xFF222222),
            contentColor = if (focused) Color.Black else Color.White
        ),
        border = BorderStroke(
            if (focused) 3.dp else 1.dp,
            if (focused) Color.White else Color(0xFF444444)
        )
    ) {
        Text(
            text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TvIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(65.dp)
            .background(
                if (focused) Color.White else Color(0xFF202020),
                RoundedCornerShape(12.dp)
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Color.White else Color(0xFF444444),
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (focused) Color.Black else Color.White

        CompositionLocalProvider(
            LocalContentColor provides tint
        ) {
            content()
        }
    }
}

private fun getFileName(
    context: Context,
    uri: Uri
): String {
    var name = uri.lastPathSegment ?: "Selected HTML file"

    try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

            if (index >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(index)
            }
        }
    } catch (_: Exception) {
    }

    return name
}
