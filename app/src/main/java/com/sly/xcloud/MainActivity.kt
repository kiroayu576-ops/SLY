package com.sly.xcloud

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sly.xcloud.data.AppSettings
import com.sly.xcloud.data.ScriptStore
import com.sly.xcloud.data.SettingsStore
import com.sly.xcloud.data.UserScript
import com.sly.xcloud.ui.theme.SlyXcloudTheme
import org.json.JSONObject
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SlyXcloudTheme {
                val context = LocalContext.current.applicationContext
                val settingsStore = remember { SettingsStore(context) }
                val scriptStore = remember { ScriptStore(context) }
                SlyXcloudApp(settingsStore, scriptStore)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlyXcloudApp(
    settingsStore: SettingsStore,
    scriptStore: ScriptStore
) {
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scripts by scriptStore.scriptsFlow.collectAsStateWithLifecycle()
    val appName = stringResource(id = R.string.app_name)
    val activity = LocalContext.current as? Activity

    var showSettings by remember { mutableStateOf(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = appName) },
                actions = {
                    IconButton(onClick = { webViewRef.value?.loadUrl(settings.startUrl) }) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = "Home")
                    }
                    IconButton(onClick = { webViewRef.value?.reload() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reload")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            WebViewPanel(
                appSettings = settings,
                scripts = scripts,
                webViewRef = webViewRef
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            scripts = scripts,
            onDismiss = { showSettings = false },
            onUpdateStartUrl = settingsStore::updateStartUrl,
            onDesktopMode = settingsStore::setDesktopMode,
            onKeepScreenOn = settingsStore::setKeepScreenOn,
            onScriptsEnabled = settingsStore::setScriptsEnabled,
            onImmersiveMode = settingsStore::setImmersiveMode,
            onCustomCssEnabled = settingsStore::setCustomCssEnabled,
            onCustomCssChange = settingsStore::updateCustomCss,
            onAddScript = scriptStore::add,
            onUpdateScript = scriptStore::update,
            onDeleteScript = scriptStore::delete,
            onToggleScript = scriptStore::setEnabled,
            onClearWebData = {
                webViewRef.value?.let { clearWebData(it) }
            }
        )
    }

    LaunchedEffect(settings.immersiveMode) {
        activity?.let { setImmersiveMode(it, settings.immersiveMode) }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.let { setImmersiveMode(it, false) }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewPanel(
    appSettings: AppSettings,
    scripts: List<UserScript>,
    webViewRef: MutableState<WebView?>
) {
    val context = LocalContext.current
    val defaultUserAgent = remember { WebSettings.getDefaultUserAgent(context) }
    val desktopUserAgent = remember {
        defaultUserAgent
            .replace("Android", "X11; Linux x86_64")
            .replace("Mobile", "")
    }

    val latestSettings = rememberUpdatedState(appSettings)
    val latestScripts = rememberUpdatedState(scripts)

    val slyWebViewClient = remember {
        object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val currentSettings = latestSettings.value
                if (currentSettings.scriptsEnabled) {
                    injectScripts(view, latestScripts.value)
                }
                if (currentSettings.customCssEnabled) {
                    injectCss(view, currentSettings.customCss)
                }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewRef.value = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadsImagesAutomatically = true
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.userAgentString = if (appSettings.desktopMode) {
                    desktopUserAgent
                } else {
                    defaultUserAgent
                }
                settings.useWideViewPort = appSettings.desktopMode
                settings.loadWithOverviewMode = appSettings.desktopMode
                keepScreenOn = appSettings.keepScreenOn
                this.webViewClient = slyWebViewClient
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                loadUrl(appSettings.startUrl)
            }
        },
        update = { webView ->
            applyWebViewSettings(webView, appSettings, defaultUserAgent, desktopUserAgent)
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.destroy()
            webViewRef.value = null
        }
    }

    LaunchedEffect(appSettings.startUrl) {
        webViewRef.value?.let { view ->
            if (view.url != appSettings.startUrl) {
                view.loadUrl(appSettings.startUrl)
            }
        }
    }

    LaunchedEffect(appSettings.scriptsEnabled, scripts) {
        val view = webViewRef.value ?: return@LaunchedEffect
        if (appSettings.scriptsEnabled) {
            injectScripts(view, scripts)
        }
    }

    LaunchedEffect(appSettings.customCssEnabled, appSettings.customCss) {
        val view = webViewRef.value ?: return@LaunchedEffect
        if (appSettings.customCssEnabled) {
            injectCss(view, appSettings.customCss)
        } else {
            removeCss(view)
        }
    }
}

private fun applyWebViewSettings(
    webView: WebView,
    settings: AppSettings,
    defaultUserAgent: String,
    desktopUserAgent: String
) {
    val webSettings = webView.settings
    val targetUserAgent = if (settings.desktopMode) desktopUserAgent else defaultUserAgent
    if (webSettings.userAgentString != targetUserAgent) {
        webSettings.userAgentString = targetUserAgent
    }
    webSettings.useWideViewPort = settings.desktopMode
    webSettings.loadWithOverviewMode = settings.desktopMode
    webView.keepScreenOn = settings.keepScreenOn
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    settings: AppSettings,
    scripts: List<UserScript>,
    onDismiss: () -> Unit,
    onUpdateStartUrl: (String) -> Unit,
    onDesktopMode: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onScriptsEnabled: (Boolean) -> Unit,
    onImmersiveMode: (Boolean) -> Unit,
    onCustomCssEnabled: (Boolean) -> Unit,
    onCustomCssChange: (String) -> Unit,
    onAddScript: (UserScript) -> Unit,
    onUpdateScript: (UserScript) -> Unit,
    onDeleteScript: (UserScript) -> Unit,
    onToggleScript: (UserScript, Boolean) -> Unit,
    onClearWebData: () -> Unit
) {
    var urlText by remember(settings.startUrl) { mutableStateOf(settings.startUrl) }
    var editingScript by remember { mutableStateOf<UserScript?>(null) }
    var cssText by remember(settings.customCss) { mutableStateOf(settings.customCss) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "App", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text(text = "Start URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    onUpdateStartUrl(urlText)
                }),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onUpdateStartUrl(urlText) }) {
                    Text(text = "Apply URL")
                }
            }

            SettingToggle(
                title = "Keep screen on",
                checked = settings.keepScreenOn,
                onCheckedChange = onKeepScreenOn
            )
            SettingToggle(
                title = "Immersive mode",
                checked = settings.immersiveMode,
                onCheckedChange = onImmersiveMode
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClearWebData) {
                    Text(text = "Clear web data")
                }
            }

            Divider()
            Text(text = "Stream", style = MaterialTheme.typography.titleMedium)

            SettingToggle(
                title = "Desktop mode",
                checked = settings.desktopMode,
                onCheckedChange = onDesktopMode
            )

            Divider()
            Text(text = "Scripts", style = MaterialTheme.typography.titleMedium)

            SettingToggle(
                title = "Enable scripts",
                checked = settings.scriptsEnabled,
                onCheckedChange = onScriptsEnabled
            )

            Divider()
            Text(text = "Custom CSS", style = MaterialTheme.typography.titleMedium)

            SettingToggle(
                title = "Enable custom CSS",
                checked = settings.customCssEnabled,
                onCheckedChange = onCustomCssEnabled
            )

            OutlinedTextField(
                value = cssText,
                onValueChange = {
                    cssText = it
                    onCustomCssChange(it)
                },
                label = { Text(text = "CSS") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                maxLines = 10,
                enabled = settings.customCssEnabled
            )

            Button(onClick = {
                editingScript = UserScript(
                    id = UUID.randomUUID().toString(),
                    name = "",
                    enabled = true,
                    code = ""
                )
            }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Add script")
            }

            if (scripts.isEmpty()) {
                Text(text = "No scripts yet. Add one to inject on page load.")
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    scripts.forEach { script ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = script.name.ifBlank { "Untitled script" })
                            }
                            Switch(
                                checked = script.enabled,
                                onCheckedChange = { onToggleScript(script, it) }
                            )
                            IconButton(onClick = { editingScript = script }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { onDeleteScript(script) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (editingScript != null) {
        ScriptEditorDialog(
            initial = editingScript,
            onDismiss = { editingScript = null },
            onSave = { script ->
                val exists = scripts.any { it.id == script.id }
                if (exists) {
                    onUpdateScript(script)
                } else {
                    onAddScript(script)
                }
                editingScript = null
            }
        )
    }
}

@Composable
private fun SettingToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ScriptEditorDialog(
    initial: UserScript?,
    onDismiss: () -> Unit,
    onSave: (UserScript) -> Unit
) {
    val base = initial ?: return
    var name by remember(base.id) { mutableStateOf(base.name) }
    var enabled by remember(base.id) { mutableStateOf(base.enabled) }
    var code by remember(base.id) { mutableStateOf(base.code) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (base.name.isBlank()) "New script" else "Edit script") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = "Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(text = "JavaScript") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = 10
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Enabled", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    base.copy(
                        name = name.trim(),
                        enabled = enabled,
                        code = code
                    )
                )
            }) {
                Text(text = "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

private fun injectScripts(webView: WebView, scripts: List<UserScript>) {
    scripts.filter { it.enabled }.forEach { script ->
        val safeName = script.name.replace("'", "\\'")
        val wrapped = "(function(){try{${script.code}\n}catch(e){console.error('Script error: $safeName', e);}})();"
        webView.evaluateJavascript(wrapped, null)
    }
}

private fun injectCss(webView: WebView, css: String) {
    if (css.isBlank()) return
    val cssLiteral = JSONObject.quote(css)
    val wrapped = "(function(){try{var id='sly-custom-css';var style=document.getElementById(id);if(!style){style=document.createElement('style');style.id=id;document.head.appendChild(style);}style.innerHTML=$cssLiteral;}catch(e){console.error('CSS error', e);}})();"
    webView.evaluateJavascript(wrapped, null)
}

private fun removeCss(webView: WebView) {
    val wrapped = "(function(){try{var style=document.getElementById('sly-custom-css');if(style){style.remove();}}catch(e){console.error('CSS remove error', e);}})();"
    webView.evaluateJavascript(wrapped, null)
}

private fun clearWebData(webView: WebView) {
    webView.clearHistory()
    webView.clearCache(true)
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
}

private fun setImmersiveMode(activity: Activity, enabled: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(activity.window, !enabled)
    val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
    if (enabled) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
