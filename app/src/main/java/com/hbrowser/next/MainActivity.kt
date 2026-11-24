package com.hbrowser.next

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { HBrowserApp(this) }
    }
}

private data class SavedPage(val title: String, val url: String)
private val blockedHosts = setOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
    "adservice.google.com", "adnxs.com", "adform.net", "taboola.com",
    "outbrain.com", "popads.net", "popcash.net", "pagead2.googlesyndication.com",
    "adroll.com", "criteo.com", "adsrvr.org", "smartadserver.com", "rubiconproject.com",
    "pubmatic.com", "openx.net", "media.net", "quantserve.com", "scorecardresearch.com",
    "amazon-adsystem.com", "zedo.com", "mgid.com", "revcontent.com", "zedo.com",
    "adition.com", "bidswitch.net", "casalemedia.com", "sharethrough.com", "yieldmo.com"
)

@Composable
private fun HBrowserApp(activity: MainActivity) {
    val prefs = remember { activity.getSharedPreferences("hbrowser", Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var glass by remember { mutableStateOf(prefs.getBoolean("glass", true)) }
    var adBlock by remember { mutableStateOf(prefs.getBoolean("adblock", true)) }
    var desktop by remember { mutableStateOf(false) }
    var searchEngine by remember { mutableStateOf(prefs.getString("search_engine", "Google") ?: "Google") }
    var currentUrl by remember { mutableStateOf("https://www.google.com") }
    var title by remember { mutableStateOf("Google") }
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var address by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var tabCount by remember { mutableIntStateOf(1) }
    var home by remember { mutableStateOf(true) }
    var bookmarks by remember { mutableStateOf(loadSaved(prefs, "bookmarks")) }
    var history by remember { mutableStateOf(loadSaved(prefs, "history")) }
    val context = LocalContext.current
    val bg = if (dark) Color(0xFF071321) else Color(0xFFF2F5FA)
    val surface = if (dark) Color(0xFF14263A) else Color.White
    val text = if (dark) Color(0xFFF2F6FC) else Color(0xFF172337)
    val muted = if (dark) Color(0xFF9FB0C4) else Color(0xFF69788C)
    val accent = Color(0xFF83D8FF)

    fun navigate(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        val target = when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.contains(".") && !value.contains(" ") -> "https://$value"
            else -> {
                val base = when (searchEngine) {
                    "Bing" -> "https://www.bing.com/search?q="
                    "DuckDuckGo" -> "https://duckduckgo.com/?q="
                    "Brave" -> "https://search.brave.com/search?q="
                    else -> "https://www.google.com/search?q="
                }
                base + Uri.encode(value)
            }
        }
        home = false
        editing = false
        currentUrl = target
        webView?.loadUrl(target)
    }
    fun saveBookmark() {
        if (currentUrl.isNotBlank()) {
            val item = SavedPage(title.ifBlank { currentUrl }, currentUrl)
            bookmarks = (listOf(item) + bookmarks.filterNot { it.url == item.url }).take(200)
            saveSaved(prefs, "bookmarks", bookmarks)
        }
    }
    fun addHistory(url: String, pageTitle: String) {
        if (url.startsWith("http")) {
            history = (listOf(SavedPage(pageTitle.ifBlank { url }, url)) + history.filterNot { it.url == url }).take(300)
            saveSaved(prefs, "history", history)
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true || !home || editing || showSettings || showMenu || showBookmarks || showHistory || showTabs) {
        when {
            showSettings -> showSettings = false
            showBookmarks -> showBookmarks = false
            showHistory -> showHistory = false
            showTabs -> showTabs = false
            showMenu -> showMenu = false
            editing -> editing = false
            webView?.canGoBack() == true -> webView?.goBack()
            else -> { home = true; webView?.loadUrl("about:blank") }
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = accent, background = bg, surface = surface) else lightColorScheme(primary = Color(0xFF087CC5), background = bg, surface = surface)) {
        Box(Modifier.fillMaxSize().background(bg)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Compact browser chrome, deliberately inside system-bar insets.
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Color(0xFF70D8FF), Color(0xFF4A7CFF)))), contentAlignment = Alignment.Center) {
                        Text("H", color = Color(0xFF071321), fontSize = 23.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("H BROWSER", color = text, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 14.sp)
                        Text(if (loading) "Loading securely…" else "Private • Fast • Fluid", color = muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { showTabs = true }) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp).border(1.dp, muted.copy(.6f), RoundedCornerShape(9.dp))) {
                            Text("$tabCount", color = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = text) }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (glass) surface.copy(alpha = if (dark) .82f else .92f) else surface)
                        .border(1.dp, if (dark) Color.White.copy(.12f) else Color.Black.copy(.07f), RoundedCornerShape(22.dp))
                        .clickable { address = currentUrl; editing = true }.padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = accent, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (home) "Search or enter address" else currentUrl.removePrefix("https://").removePrefix("http://").take(42), color = if (home) muted else text, modifier = Modifier.weight(1f), maxLines = 1, fontSize = 14.sp)
                    Icon(Icons.Default.Tune, null, tint = muted, modifier = Modifier.size(19.dp))
                }
                if (progress in 1..99 && !home) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 18.dp), color = accent, trackColor = Color.Transparent)

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (home) {
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 28.dp, bottom = 24.dp)) {
                            item {
                                Text("A calmer way\n to explore.", color = text, fontSize = 31.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text("Your web, beautifully in flow.", color = muted, fontSize = 14.sp)
                                Spacer(Modifier.height(25.dp))
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(Brush.linearGradient(listOf(Color(0xFF183C59), Color(0xFF182A48)))).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("QUICK SEARCH", color = Color(0xFF83D8FF), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(7.dp))
                                        Text("What are you\nlooking for today?", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Icon(Icons.Default.Explore, null, tint = Color(0xFF83D8FF), modifier = Modifier.size(45.dp))
                                }
                                Spacer(Modifier.height(23.dp))
                                Text("QUICK ACCESS", color = muted, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    QuickTile("Google", Icons.Default.Search, surface, text, Modifier.weight(1f)) { navigate("https://google.com") }
                                    QuickTile("YouTube", Icons.Default.PlayCircle, surface, text, Modifier.weight(1f)) { navigate("https://youtube.com") }
                                    QuickTile("Wikipedia", Icons.Default.MenuBook, surface, text, Modifier.weight(1f)) { navigate("https://wikipedia.org") }
                                    QuickTile("Reddit", Icons.Default.Forum, surface, text, Modifier.weight(1f)) { navigate("https://reddit.com") }
                                }
                            }
                            item {
                                Spacer(Modifier.height(27.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("RECENTLY VISITED", color = muted, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("View all", color = accent, fontSize = 12.sp, modifier = Modifier.clickable { showHistory = true })
                                }
                                Spacer(Modifier.height(9.dp))
                            }
                            if (history.isEmpty()) item {
                                GlassCard(surface, glass) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.History, null, tint = accent)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("Your browsing starts here", color = text, fontWeight = FontWeight.SemiBold)
                                            Text("Pages you visit will appear in this space.", color = muted, fontSize = 12.sp)
                                        }
                                    }
                                }
                            } else items(minOf(history.size, 5)) { i ->
                                Row(Modifier.fillMaxWidth().clickable { navigate(history[i].url) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Language, null, tint = accent)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(history[i].title, color = text, maxLines = 1, fontSize = 14.sp)
                                        Text(history[i].url, color = muted, maxLines = 1, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.NorthEast, null, tint = muted, modifier = Modifier.size(16.dp))
                                }
                            }
                            item {
                                Spacer(Modifier.height(20.dp))
                                Text("MADE FOR YOUR FLOW", color = muted, fontSize = 10.sp, letterSpacing = 2.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Glass surfaces • Soft motion • Your controls", color = muted, fontSize = 12.sp)
                            }
                        }
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    webView = this
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.loadsImagesAutomatically = true
                                    settings.javaScriptCanOpenWindowsAutomatically = false
                                    settings.setSupportMultipleWindows(false)
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    settings.userAgentString = if (desktop) settings.userAgentString.replace("Mobile", "Desktop").replace("Android", "X11; Linux x86_64") else WebSettings.getDefaultUserAgent(ctx)
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { loading = true; progress = 5; url?.let { currentUrl = it } }
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            loading = false; progress = 100
                                            url?.let { currentUrl = it; title = view?.title ?: it; addHistory(it, title) }
                                        }
                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            if (request?.isForMainFrame == true) loading = false
                                        }
                                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                            if (adBlock) {
                                                val host = request?.url?.host?.lowercase() ?: ""
                                                if (blockedHosts.any { host == it || host.endsWith(".$it") }) return WebResourceResponse("text/plain", "utf-8", null)
                                            }
                                            return super.shouldInterceptRequest(view, request)
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                                        override fun onReceivedTitle(view: WebView?, pageTitle: String?) { title = pageTitle ?: title }
                                    }
                                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                                        try {
                                            val req = DownloadManager.Request(Uri.parse(url))
                                            req.setMimeType(mimeType)
                                            req.addRequestHeader("User-Agent", userAgent)
                                            req.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                                            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType))
                                            (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                                            android.widget.Toast.makeText(ctx, "Download started", android.widget.Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) { }
                                    }
                                    loadUrl(currentUrl)
                                }
                            },
                            update = { webView = it }
                        )
                    }
                }

                // Floating, pill-shaped navigation dock.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp)
                        .shadow(if (glass) 16.dp else 5.dp, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (dark) Color(0xE8172A40) else Color(0xF7FFFFFF))
                        .border(1.dp, if (dark) Color.White.copy(.10f) else Color.Black.copy(.06f), RoundedCornerShape(28.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically
                ) {
                    DockButton(Icons.Default.ArrowBack, text, "Back", enabled = webView?.canGoBack() == true) { webView?.goBack() }
                    DockButton(Icons.Default.ArrowForward, text, "Forward", enabled = webView?.canGoForward() == true) { webView?.goForward() }
                    DockButton(if (loading) Icons.Default.Close else Icons.Default.Refresh, text, if (loading) "Stop" else "Reload") { if (loading) webView?.stopLoading() else if (home) navigate("https://google.com") else webView?.reload() }
                    DockButton(Icons.Default.Home, text, "Home") { home = true; webView?.loadUrl("about:blank") }
                    DockButton(Icons.Default.BookmarkAdd, text, "Save") { saveBookmark(); android.widget.Toast.makeText(context, "Bookmark saved", android.widget.Toast.LENGTH_SHORT).show() }
                }
            }

            if (editing) {
                AlertDialog(
                    onDismissRequest = { editing = false },
                    title = { Text("Search or enter address") },
                    text = {
                        OutlinedTextField(value = address, onValueChange = { address = it }, singleLine = true, placeholder = { Text("Search the web or enter URL") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { navigate(address) }))
                    },
                    confirmButton = { TextButton(onClick = { navigate(address) }) { Text("GO") } },
                    dismissButton = { TextButton(onClick = { editing = false }) { Text("CANCEL") } },
                    containerColor = surface, titleContentColor = text, textContentColor = text
                )
            }
            if (showMenu) {
                AlertDialog(onDismissRequest = { showMenu = false }, title = { Text("Browser tools") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MenuRow("Bookmarks", Icons.Default.Bookmarks) { showMenu = false; showBookmarks = true }
                            MenuRow("History", Icons.Default.History) { showMenu = false; showHistory = true }
                            MenuRow("Privacy & site data", Icons.Default.Security) { showMenu = false; showSettings = true }
                            MenuRow("New tab", Icons.Default.Add) { showMenu = false; tabCount++; home = true; webView?.loadUrl("about:blank") }
                            MenuRow("Find on page", Icons.Default.FindInPage) { showMenu = false; webView?.let { it.evaluateJavascript("window.find(prompt('Find on page'))", null) } }
                            MenuRow("Share page", Icons.Default.Share) {
                                showMenu = false
                                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, currentUrl) }
                                activity.startActivity(Intent.createChooser(send, "Share page"))
                            }
                            MenuRow("Settings", Icons.Default.Settings) { showMenu = false; showSettings = true }
                        }
                    }, confirmButton = { TextButton(onClick = { showMenu = false }) { Text("CLOSE") } },
                    containerColor = surface, titleContentColor = text, textContentColor = text)
            }
            if (showSettings) {
                AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("H Browser settings") },
                    text = {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 470.dp)) {
                            item { SettingSwitch("Dark appearance", "Deep midnight browser theme", dark) { dark = it; prefs.edit().putBoolean("dark", it).apply() } }
                            item { SettingSwitch("Frosted glass surfaces", "Translucent layered browser chrome", glass) { glass = it; prefs.edit().putBoolean("glass", it).apply() } }
                            item { SettingSwitch("Ad & tracker blocking", "Blocks common advertising hosts", adBlock) { adBlock = it; prefs.edit().putBoolean("adblock", it).apply() } }
                            item {
                                Text("DEFAULT SEARCH ENGINE", color = muted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                                    listOf("Google", "Bing", "DuckDuckGo", "Brave").forEach { engine ->
                                        FilterChip(selected = searchEngine == engine, onClick = { searchEngine = engine; prefs.edit().putString("search_engine", engine).apply() }, label = { Text(engine, fontSize = 10.sp) })
                                    }
                                }
                            }
                            item { SettingSwitch("Desktop site", "Request desktop versions of websites", desktop) { desktop = it; webView?.settings?.userAgentString = if (it) WebSettings.getDefaultUserAgent(context).replace("Mobile", "Desktop") else WebSettings.getDefaultUserAgent(context); webView?.reload() } }
                            item { SettingSwitch("JavaScript", "Required by many modern websites", webView?.settings?.javaScriptEnabled ?: true) { webView?.settings?.javaScriptEnabled = it; webView?.reload() } }
                            item { SettingSwitch("Accept cookies", "Allow websites to store cookies", true) { CookieManager.getInstance().setAcceptCookie(it) } }
                            item { SettingSwitch("Pop-up windows", "Allow websites to open pop-ups", false) { webView?.settings?.javaScriptCanOpenWindowsAutomatically = it } }
                            item {
                                TextButton(onClick = {
                                    webView?.clearCache(true); webView?.clearHistory(); CookieManager.getInstance().removeAllCookies(null)
                                    history = emptyList(); saveSaved(prefs, "history", history)
                                }) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text("Clear browsing data") }
                            }
                            item { Text("Privacy note: ad blocking uses a built-in host list. It is not a full EasyList/AdGuard filter engine and cannot block every ad or tracker.", color = muted, fontSize = 11.sp) }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showSettings = false }) { Text("DONE") } },
                    containerColor = surface, titleContentColor = text, textContentColor = text)
            }
            if (showBookmarks || showHistory || showTabs) {
                val heading = if (showBookmarks) "Bookmarks" else if (showHistory) "History" else "Your tabs"
                val list = if (showBookmarks) bookmarks else history
                AlertDialog(onDismissRequest = { showBookmarks = false; showHistory = false; showTabs = false },
                    title = { Text(heading) },
                    text = {
                        if (showTabs) Column {
                            Text("Tab 1  •  ${if (home) "New tab" else title}", color = text)
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { tabCount++; home = true; showTabs = false; webView?.loadUrl("about:blank") }) { Icon(Icons.Default.Add, null); Text("  New tab") }
                        } else if (list.isEmpty()) Text("Nothing here yet.", color = muted)
                        else LazyColumn(Modifier.heightIn(max = 400.dp)) {
                            items(list.size) { i ->
                                Row(Modifier.fillMaxWidth().clickable { navigate(list[i].url); showBookmarks = false; showHistory = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (showBookmarks) Icons.Default.Bookmark else Icons.Default.History, null, tint = accent)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(list[i].title, color = text, maxLines = 1)
                                        Text(list[i].url, color = muted, maxLines = 1, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showBookmarks = false; showHistory = false; showTabs = false }) { Text("CLOSE") } },
                    containerColor = surface, titleContentColor = text, textContentColor = text)
            }
        }
    }
}

@Composable
private fun QuickTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, surface: Color, text: Color, modifier: Modifier, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) .94f else 1f, label = "jelly")
    Column(modifier.scale(scale).clip(RoundedCornerShape(19.dp)).background(surface).border(1.dp, Color.White.copy(.08f), RoundedCornerShape(19.dp)).pointerInput(Unit) {
        detectTapGestures(onPress = { pressed = true; val released = tryAwaitRelease(); pressed = false; if (released) onClick() })
    }.padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color(0xFF79D5FF), modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(7.dp))
        Text(label, color = text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GlassCard(surface: Color, glass: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(surface.copy(alpha = if (glass) .82f else 1f)).border(1.dp, Color.White.copy(.09f), RoundedCornerShape(20.dp)).padding(16.dp), content = content)
}

@Composable
private fun DockButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    var down by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (down) .82f else 1f, label = "dockJelly")
    Column(Modifier.scale(scale).clip(RoundedCornerShape(16.dp)).pointerInput(enabled) {
        if (enabled) detectTapGestures(onPress = { down = true; val released = tryAwaitRelease(); down = false; if (released) onClick() })
    }.padding(horizontal = 9.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (enabled) tint else tint.copy(.35f), modifier = Modifier.size(21.dp))
        Text(label, color = if (enabled) tint.copy(.8f) else tint.copy(.3f), fontSize = 9.sp)
    }
}

@Composable
private fun MenuRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(label, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null)
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun loadSaved(prefs: android.content.SharedPreferences, key: String): List<SavedPage> {
    return try {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); SavedPage(o.optString("title"), o.optString("url")) }
    } catch (_: Exception) { emptyList() }
}
private fun saveSaved(prefs: android.content.SharedPreferences, key: String, pages: List<SavedPage>) {
    val arr = JSONArray()
    pages.forEach { arr.put(JSONObject().put("title", it.title).put("url", it.url)) }
    prefs.edit().putString(key, arr.toString()).apply()
}
