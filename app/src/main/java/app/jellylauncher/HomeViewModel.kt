package app.jellylauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.jellylauncher.data.AppsRepository
import app.jellylauncher.data.AuthExpiredException
import app.jellylauncher.data.JellyfinApi
import app.jellylauncher.data.LaunchableApp
import app.jellylauncher.data.Prefs
import app.jellylauncher.data.ResumeCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val showLogin: Boolean = false,
    val loggingIn: Boolean = false,
    val showAllApps: Boolean = false,
    val showSetup: Boolean = false,
    val overrideOn: Boolean = false,
    val loginError: String? = null,
    val cards: List<ResumeCard> = emptyList(),
    val apps: List<LaunchableApp> = emptyList(),
    val error: String? = null,
    val userName: String = "",
    val serverUrl: String = "",
    val pinned: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    val visibleApps: List<LaunchableApp> get() = apps.filter { it.packageName !in hidden }
    val hiddenApps: List<LaunchableApp> get() = apps.filter { it.packageName in hidden }

    /** Dock: your pinned apps in your order, or the first few apps until you pin something. */
    val dockApps: List<LaunchableApp>
        get() = (if (pinned.isEmpty()) visibleApps.take(6)
        else pinned.mapNotNull { p -> visibleApps.find { it.packageName == p } }).take(8)
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private val api = JellyfinApi(prefs)
    private val appsRepository = AppsRepository(application)

    private val layoutPrefs = application.getSharedPreferences("layout", 0)

    private val _state = MutableStateFlow(
        HomeUiState(
            showLogin = !prefs.isLoggedIn,
            loading = prefs.isLoggedIn,
            userName = prefs.userName,
            serverUrl = prefs.serverUrl,
            pinned = loadList("pinned"),
            hidden = loadList("hidden").toSet(),
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private fun loadList(key: String): List<String> =
        layoutPrefs.getString(key, "").orEmpty().split(",").filter { it.isNotBlank() }

    private fun saveLayout(pinned: List<String>, hidden: Set<String>) {
        layoutPrefs.edit().putString("pinned", pinned.joinToString(",")).putString("hidden", hidden.joinToString(",")).apply()
        _state.update { it.copy(pinned = pinned, hidden = hidden) }
    }

    /** Pin or unpin an app on the dock (max 8). */
    fun togglePin(pkg: String) {
        val s = _state.value
        val current = if (s.pinned.isEmpty()) s.dockApps.map { it.packageName } else s.pinned
        val next = if (pkg in current) current - pkg else if (current.size < 8) current + pkg else current
        saveLayout(next, s.hidden)
    }

    fun movePin(pkg: String, delta: Int) {
        val s = _state.value
        val list = (if (s.pinned.isEmpty()) s.dockApps.map { it.packageName } else s.pinned).toMutableList()
        val i = list.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        saveLayout(list, s.hidden)
    }

    /** Hide or show an app everywhere in Encore (it stays installed). */
    fun toggleHide(pkg: String) {
        val s = _state.value
        if (pkg in s.hidden) saveLayout(s.pinned, s.hidden - pkg)
        else saveLayout((if (s.pinned.isEmpty()) s.dockApps.map { it.packageName } else s.pinned) - pkg, s.hidden + pkg)
    }

    private val setupPrefs = application.getSharedPreferences("setup", 0)

    init {
        if (!setupPrefs.getBoolean("seen", false)) _state.update { it.copy(showSetup = true, overrideOn = isOverrideOn()) }
    }

    private var refreshJob: Job? = null

    /** Reloads installed apps and Continue Watching. Called every time the home screen comes back. */
    fun refresh() {
        viewModelScope.launch {
            try {
                val apps = appsRepository.load()
                _state.update { it.copy(apps = apps) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep whatever app list we already have.
            }
        }

        if (!prefs.isLoggedIn) {
            _state.update { it.copy(showLogin = true, loading = false) }
            return
        }

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val items = api.continueWatching()
                _state.update {
                    it.copy(
                        cards = items.map(api::toCard),
                        loading = false,
                        error = null,
                        userName = prefs.userName,
                        serverUrl = prefs.serverUrl,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthExpiredException) {
                prefs.signOut()
                _state.update {
                    it.copy(
                        showLogin = true,
                        loading = false,
                        cards = emptyList(),
                        loginError = "Your session expired. Please sign in again.",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = "Can't reach your Jellyfin server (${e.message ?: e.javaClass.simpleName})",
                    )
                }
            }
        }
    }

    fun login(server: String, username: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(loggingIn = true, loginError = null) }
            try {
                api.login(server, username, password)
                _state.update {
                    it.copy(
                        loggingIn = false,
                        showLogin = false,
                        loading = true,
                        error = null,
                        userName = prefs.userName,
                        serverUrl = prefs.serverUrl,
                    )
                }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loggingIn = false,
                        loginError = e.message ?: "Couldn't sign in",
                    )
                }
            }
        }
    }

    fun openSettings() = _state.update { it.copy(showLogin = true, loginError = null) }

    fun openSetup() = _state.update { it.copy(showSetup = true, overrideOn = isOverrideOn()) }

    fun closeSetup() {
        setupPrefs.edit().putBoolean("seen", true).apply()
        _state.update { it.copy(showSetup = false) }
    }

    fun checkOverride() = _state.update { it.copy(overrideOn = isOverrideOn()) }

    private fun isOverrideOn(): Boolean {
        val app = getApplication<Application>()
        val enabled = android.provider.Settings.Secure.getString(
            app.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.contains(app.packageName + "/", ignoreCase = true) && enabled.contains("HomeOverrideService")
    }

    fun openAllApps() = _state.update { it.copy(showAllApps = true) }

    fun closeAllApps() = _state.update { it.copy(showAllApps = false) }

    fun closeSettings() {
        if (prefs.isLoggedIn) _state.update { it.copy(showLogin = false, loginError = null) }
    }
}
