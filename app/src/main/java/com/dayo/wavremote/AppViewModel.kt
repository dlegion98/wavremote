package com.dayo.wavremote

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface Screen {
    data object Connect : Screen
    /** Launch screen while signing in to the remembered router. */
    data object Starting : Screen
    data class Login(val host: String) : Screen
    data object Main : Screen
    data object WebSettings : Screen
    data class Settings(val page: SettingsPage?) : Screen
}

/** One read from the router; [viaSet] for the few reads the web UI does as a "set" (e.g. LED act=dump). */
data class Req(val fname: String, val opt: String, val extra: String = "", val viaSet: Boolean = false) {
    val key get() = "$fname/$opt$extra"
}

enum class Tab { HOME, DEVICES, WIFI, MORE }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("wavremote", Context.MODE_PRIVATE)

    var screen by mutableStateOf<Screen>(
        if (prefs.getString("lastHost", null) != null) Screen.Starting else Screen.Connect
    ); private set
    var tab by mutableStateOf(Tab.HOME)

    // ---- connect
    var scanning by mutableStateOf(false); private set
    var scanResults by mutableStateOf<List<ProbeResult>>(emptyList()); private set
    var wifiInfo by mutableStateOf<WifiInfo?>(null); private set
    var customHost by mutableStateOf(prefs.getString("customHost", "") ?: "")
    private var autoLoginTried = false

    // ---- session
    private var api: WavlinkApi? = null
    var host by mutableStateOf<String?>(null); private set
    var loginBusy by mutableStateOf(false); private set
    var loginError by mutableStateOf<String?>(null); private set
    var needsSetup by mutableStateOf(false); private set

    // ---- data
    var overview by mutableStateOf<Overview?>(null); private set
    var devices by mutableStateOf<DeviceList?>(null); private set
    var wifiConfig by mutableStateOf<WifiConfig?>(null); private set
    var meshNodes by mutableStateOf<List<MeshNode>?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)

    val lastHost: String? get() = prefs.getString("lastHost", null)


    // ================================================================ app updates (GitHub releases)

    var update by mutableStateOf<UpdateInfo?>(null); private set
    var updateStatus by mutableStateOf<String?>(null); private set
    var updateProgress by mutableStateOf<Float?>(null); private set

    fun checkForUpdate(manual: Boolean = true) {
        if (!Updater.enabled) {
            if (manual) updateStatus = "Updates turn on once the app is installed from GitHub."
            return
        }
        if (manual) updateStatus = "Checking…"
        viewModelScope.launch {
            try {
                update = withContext(Dispatchers.IO) { Updater.check() }
                updateStatus = if (update == null) "You have the latest version." else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) updateStatus = "Couldn't check for updates. Try again when you're online."
            }
        }
    }

    fun installUpdate() {
        val info = update ?: return
        val ctx = getApplication<Application>()
        if (!Updater.canInstall(ctx)) {
            updateStatus = "Allow WavRemote to install updates, then tap Install again."
            Updater.openInstallPermission(ctx)
            return
        }
        if (updateProgress != null) return
        updateProgress = 0f
        updateStatus = "Downloading…"
        viewModelScope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    Updater.download(ctx, info) { p -> updateProgress = p }
                }
                updateStatus = "Installing…"
                withContext(Dispatchers.IO) { Updater.install(ctx, apk) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateStatus = "The update didn't download. Try again."
            } finally {
                updateProgress = null
            }
        }
    }

    // ================================================================ connect

    fun scan() {
        if (scanning) return
        prefs.edit().putString("customHost", customHost).apply()
        viewModelScope.launch {
            scanning = true
            val ctx = getApplication<Application>()
            val info = WifiNetwork.current(ctx)
            wifiInfo = info
            WifiNetwork.bindAppToWifi(ctx, info)
            val extra = customHost.ifBlank { lastHost }
            scanResults = RouterScanner.scan(info?.network, info?.gateway, extra)
            scanning = false

            // Auto-connect to the remembered router on first launch.
            if (!autoLoginTried) {
                autoLoginTried = true
                val saved = lastHost
                val hash = prefs.getString("hash:$saved", null)
                if (saved != null && hash != null &&
                    scanResults.any { it.host == saved && it.state == ProbeState.WAVLINK }
                ) {
                    openRouter(saved)
                }
            }
        }
    }

    fun openRouter(host: String) {
        loginError = null
        screen = Screen.Login(host)
        val hash = prefs.getString("hash:$host", null)
        if (hash != null) doLogin(host, hash, remember = true)
        else checkSetup(host)
    }

    fun backToConnect() {
        screen = Screen.Connect
        if (scanResults.isEmpty() && !scanning) scan()
    }

    /** True when a sign-in is remembered for [host], so it can be retried without the password. */
    fun hasSavedLogin(host: String): Boolean = prefs.getString("hash:$host", null) != null

    /** On launch, skip the search and go straight to the router used last time. */
    private fun autoConnect(saved: String) {
        autoLoginTried = true
        val ctx = getApplication<Application>()
        val info = WifiNetwork.current(ctx)
        wifiInfo = info
        WifiNetwork.bindAppToWifi(ctx, info)
        val hash = prefs.getString("hash:$saved", null)
        if (hash != null) {
            doLogin(saved, hash, remember = true)
        } else {
            screen = Screen.Login(saved)
            checkSetup(saved)
        }
    }

    private fun newApi(host: String) =
        WavlinkApi(host, WifiNetwork.current(getApplication())?.network)

    private fun checkSetup(host: String) {
        viewModelScope.launch {
            val g = runCatching { withContext(Dispatchers.IO) { newApi(host).guide() } }.getOrNull()
            needsSetup = g != null && g.has("init_status") && !g.truthy("init_status")
        }
    }

    // ================================================================ login

    fun login(host: String, password: String, remember: Boolean) {
        doLogin(host, WavlinkApi.hashPassword(password), remember)
    }

    private fun doLogin(host: String, hash: String, remember: Boolean) {
        if (loginBusy) return
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            val a = newApi(host)
            try {
                withContext(Dispatchers.IO) { a.login(hash) }
                api = a
                this@AppViewModel.host = host
                val ed = prefs.edit()
                ed.putString("lastHost", host)
                if (remember) ed.putString("hash:$host", hash) else ed.remove("hash:$host")
                ed.apply()
                clearData()
                tab = Tab.HOME
                screen = Screen.Main
                refreshAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is RouterException) prefs.edit().remove("hash:$host").apply()
                loginError = friendly(e)
                if (screen is Screen.Starting) screen = Screen.Login(host)
            } finally {
                loginBusy = false
            }
        }
    }

    fun logout(forget: Boolean) {
        val h = host
        api?.logout()
        api = null
        if (forget && h != null) prefs.edit().remove("hash:$h").apply()
        clearData()
        loginError = null
        screen = if (h != null) Screen.Login(h) else Screen.Connect
    }

    private fun clearData() {
        overview = null; devices = null; wifiConfig = null; meshNodes = null
    }

    // ================================================================ data

    fun refreshAll() {
        refreshOverview(); refreshDevices(); refreshWifi(); refreshMesh()
    }

    fun refreshOverview(quiet: Boolean = false) = load(quiet, { it.overview() }) { overview = it }
    fun refreshDevices(quiet: Boolean = false) = load(quiet, { it.devices() }) {
        devices = it
        val now = System.currentTimeMillis()
        val next = DeviceBook.record(seen, it, now)
        val grew = next.size != seen.size
        seen = next
        if (grew || now - seenSavedAt > 60_000) {
            seenSavedAt = now
            DeviceBook.saveSeen(prefs, next)
        }
        val sample = TrafficSample(System.currentTimeMillis(), it.online.sumOf { d -> d.downKBs }, it.online.sumOf { d -> d.upKBs })
        trafficHistory = (trafficHistory + sample).takeLast(60)
        Widgets.pushFromApp(getApplication<Application>(), overview, it)
    }

    /** Total network throughput, one sample per device refresh (~10 s), last 60 kept. */
    var trafficHistory by mutableStateOf<List<TrafficSample>>(emptyList()); private set
    fun refreshWifi(quiet: Boolean = false) = load(quiet, { it.wifi() }) { wifiConfig = it }
    fun refreshMesh(quiet: Boolean = false) = load(quiet, { it.meshNodes() }) { meshNodes = it }

    private fun <T> load(quiet: Boolean, fetch: (WavlinkApi) -> T, apply: (T) -> Unit) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { fetch(a) }
                apply(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e, quiet)
            }
        }
    }

    private fun action(success: String?, then: () -> Unit = {}, block: (WavlinkApi) -> Unit) {
        val a = api ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block(a) }
                success?.let { message = it }
                then()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e, quiet = false)
            } finally {
                busy = false
            }
        }
    }

    private fun handleError(e: Exception, quiet: Boolean) {
        if (e is RouterException && e.code == WavlinkApi.SESSION_EXPIRED) {
            api = null
            screen = Screen.Login(host ?: return)
            loginError = e.message
            return
        }
        if (!quiet) message = friendly(e)
    }

    private fun friendly(e: Exception): String = when (e) {
        is RouterException -> e.message ?: "Router error"
        is java.net.SocketTimeoutException -> "The router didn't answer in time."
        is java.net.ConnectException, is java.net.NoRouteToHostException ->
            "Can't reach the router. Make sure you're on its Wi-Fi."
        else -> e.message ?: e.javaClass.simpleName
    }

    // ================================================================ actions

    fun rename(d: Device, name: String) =
        action("Renamed to $name", { refreshDevices(true) }) { it.rename(d.mac, name.trim()) }

    fun setBlocked(d: Device, blocked: Boolean) =
        action(if (blocked) "${d.name} blocked from the internet" else "${d.name} allowed again",
            { refreshDevices(true) }) { it.setBlocked(d.mac, blocked) }

    fun setLimit(d: Device, downKB: Int, upKB: Int) =
        action("Speed limit saved", { refreshDevices(true) }) { it.setLimit(d.mac, downKB, upKB) }

    fun forget(d: Device) =
        action("Removed ${d.name}", { refreshDevices(true) }) { it.forgetOffline(d.mac) }

    // ---- favourites and history (kept on this phone only)

    var favourites by mutableStateOf(DeviceBook.loadFavourites(prefs)); private set
    var seen by mutableStateOf(DeviceBook.loadSeen(prefs)); private set
    private var seenSavedAt = 0L

    /** Device to open as soon as the Devices tab is shown (set from Home). */
    var focusMac by mutableStateOf<String?>(null)

    var deviceSort by mutableStateOf(prefs.getString("deviceSort", "Name") ?: "Name"); private set

    fun chooseSort(sort: String) {
        deviceSort = sort
        prefs.edit().putString("deviceSort", sort).apply()
    }

    fun isFavourite(d: Device): Boolean = DeviceBook.key(d.mac) in favourites

    fun toggleFavourite(d: Device) {
        val k = DeviceBook.key(d.mac)
        val next = if (k in favourites) favourites - k else favourites + k
        favourites = next
        DeviceBook.saveFavourites(prefs, next)
        message = if (k in next) "${d.name} added to favourites" else "${d.name} removed from favourites"
    }

    fun openDevice(d: Device) {
        focusMac = d.mac
        tab = Tab.DEVICES
    }

    fun reboot() = action("Rebooting — the network will be back in about 2 minutes.") { it.reboot() }

    fun saveWifi(ssid2: String, pass2: String, ssid5: String, pass5: String) {
        val cfg = wifiConfig ?: return
        action(
            "Wi-Fi saved. It's restarting now — your phone will reconnect in about 20 seconds " +
                "(use the new name/password if you changed them).",
        ) { it.saveWifi(cfg, ssid2.trim(), pass2, ssid5.trim(), pass5) }
    }

    // ================================================================ settings pages

    var pageData by mutableStateOf<Map<String, JSONObject>>(emptyMap()); private set
    var pageLoading by mutableStateOf(false); private set
    var pageError by mutableStateOf<String?>(null); private set
    var textOutput by mutableStateOf<String?>(null); private set
    var textBusy by mutableStateOf(false); private set

    /** Category shown by the settings list (null = category grid). */
    var settingsGroup by mutableStateOf<String?>(null)

    fun openSettings(page: SettingsPage? = null, fromList: Boolean = false) {
        pageError = null
        textOutput = null
        if (!fromList) settingsGroup = null
        screen = Screen.Settings(page)
    }

    fun openSettingsGroup(group: String) {
        settingsGroup = group
        screen = Screen.Settings(null)
    }

    fun closeSettings() {
        val s = screen
        screen = when {
            s is Screen.Settings && s.page != null && settingsGroup != null -> Screen.Settings(null)
            s is Screen.Settings && s.page == null && settingsGroup != null -> { settingsGroup = null; Screen.Settings(null) }
            else -> Screen.Main
        }
    }

    fun data(r: Req): JSONObject? = pageData[r.key]

    fun loadPage(reqs: List<Req>, optional: List<Req> = emptyList()) {
        val a = api ?: return
        pageLoading = true
        pageError = null
        viewModelScope.launch {
            try {
                fun read(r: Req) = if (r.viaSet) a.set(r.fname, r.opt, r.extra) else a.get(r.fname, r.opt, r.extra)
                val results = withContext(Dispatchers.IO) {
                    val opt = optional.mapNotNull { r -> runCatching { r.key to read(r) }.getOrNull() }.toMap()
                    opt + reqs.associate { r -> r.key to read(r) }
                }
                pageData = pageData + results
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is RouterException && e.code == WavlinkApi.SESSION_EXPIRED) handleError(e, quiet = true)
                else pageError = if (e is RouterException && e.code != 500) {
                    "This router doesn't support this setting (error ${e.code})."
                } else friendly(e)
            } finally {
                pageLoading = false
            }
        }
    }

    /**
     * Sends one or more "set" calls, then reloads the page.
     * [tolerateDisconnect]: the router restarts a service and may drop the connection — treat that as success.
     */
    fun applySettings(
        success: String?,
        reload: List<Req> = emptyList(),
        tolerateDisconnect: Boolean = false,
        reloadDelayMs: Long = 0,
        then: () -> Unit = {},
        block: (WavlinkApi) -> Unit,
    ) {
        action(success, {
            if (reload.isNotEmpty()) viewModelScope.launch {
                if (reloadDelayMs > 0) delay(reloadDelayMs)
                loadPage(reload)
            }
            then()
        }) { a ->
            try {
                block(a)
            } catch (e: java.io.IOException) {
                if (!tolerateDisconnect) throw e
            }
        }
    }

    /** Runs a router-side job, waits, then reads its text output (ping/traceroute, system log). */
    fun runText(prepare: ((WavlinkApi) -> Unit)?, waitMs: Long, path: String) {
        val a = api ?: return
        if (textBusy) return
        textBusy = true
        textOutput = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { prepare?.invoke(a) }
                if (waitMs > 0) delay(waitMs)
                textOutput = withContext(Dispatchers.IO) { a.cgiText(path) }.ifBlank { "(no output)" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                textOutput = "Couldn't get the output: " + friendly(e)
            } finally {
                textBusy = false
            }
        }
    }

    /** Set, wait, then re-read (network diagnostics, firmware check). */
    fun runAndRead(setData: String, waitMs: Long, read: Req, onProgress: (String) -> Unit = {}) {
        val a = api ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { a.call(setData) }
                if (waitMs > 0) delay(waitMs)
                val r = withContext(Dispatchers.IO) { a.get(read.fname, read.opt, read.extra) }
                pageData = pageData + (read.key to r)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e, quiet = false)
            } finally {
                busy = false
            }
        }
    }

    /** Admin password: verify the old one, set the new one, then sign back in with it. */
    fun changeAdminPassword(old: String, new: String, onDone: () -> Unit) {
        val h = host ?: return
        val oldHash = WavlinkApi.hashPassword(old)
        val newHash = WavlinkApi.hashPassword(new)
        action("Admin password changed.", then = {
            val remembered = prefs.contains("hash:$h")
            if (remembered) prefs.edit().putString("hash:$h", newHash).apply()
            onDone()
        }) { a ->
            try {
                a.set("system", "verify_login", "&usrid=$oldHash")
            } catch (e: RouterException) {
                throw RouterException(-1, "The current password is wrong.")
            }
            a.set("system", "login_account", "&user=admin&password=${WavlinkApi.enc(new)}")
            Thread.sleep(4000)
            a.login(newHash)
        }
    }

    /** LAN IP change: the router moves, so follow it to the new address. */
    fun followRouterTo(newHost: String) {
        val old = host ?: return
        val hash = prefs.getString("hash:$old", null)
        val ed = prefs.edit()
        ed.putString("lastHost", newHost)
        if (hash != null) ed.putString("hash:$newHost", hash)
        ed.apply()
        api = null
        clearData()
        message = "The router is moving to $newHost. Reconnect in about a minute."
        screen = Screen.Connect
        customHost = newHost
    }

    fun factoryReset() {
        val h = host
        action("Factory reset started. Join the router's default Wi-Fi (on its label) in about 3 minutes.",
            then = {
                if (h != null) prefs.edit().remove("hash:$h").apply()
                api = null
                clearData()
                screen = Screen.Connect
            }) { a ->
            try { a.set("system", "reset_reboot", "&action=1") } catch (_: java.io.IOException) {}
        }
    }

    // ================================================================ quick controls (Home)

    fun loadQuick() = loadPage(emptyList(), Quick.all)

    /** Returns false when the feature needs setting up first (caller opens its page). */
    fun quickGuest(on: Boolean): Boolean {
        val d = data(Quick.GUEST) ?: return false
        val body = Quick.guestData(d, on) ?: return false
        applySettings(if (on) "Guest network turned on." else "Guest network turned off.", listOf(Quick.GUEST),
            tolerateDisconnect = true, reloadDelayMs = 12000) { it.set("net", "wifi_ap_guest", body) }
        return true
    }

    fun quickKids(on: Boolean): Boolean {
        val d = data(Quick.KIDS) ?: return false
        val body = Quick.kidsData(d, on) ?: return false
        applySettings(if (on) "Kids' Wi-Fi turned on." else "Kids' Wi-Fi turned off.", listOf(Quick.KIDS),
            tolerateDisconnect = true, reloadDelayMs = 12000) { it.set("net", "wifi_lt", body) }
        return true
    }

    fun quickLights(on: Boolean) {
        applySettings(if (on) "Lights on." else "Lights off.", listOf(Quick.LED)) {
            it.set("system", "led", "&act=${if (on) "on" else "off"}")
        }
    }

    fun openWebSettings() { screen = Screen.WebSettings }
    fun closeWebSettings() { screen = if (api != null) Screen.Main else Screen.Connect }

    val currentHost: String? get() = host

    // Runs last, so every property above is initialised first.
    init {
        val saved = lastHost
        if (saved != null) autoConnect(saved) else scan()
        checkForUpdate(manual = false)
    }
}
