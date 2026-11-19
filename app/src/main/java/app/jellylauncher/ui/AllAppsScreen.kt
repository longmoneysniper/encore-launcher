package app.jellylauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jellylauncher.data.LaunchableApp
import kotlinx.coroutines.delay

private val GoldDot = Color(0xFFFFC145)

@Composable
fun AllAppsScreen(
    visibleApps: List<LaunchableApp>,
    hiddenApps: List<LaunchableApp>,
    pinned: List<String>,
    onLaunch: (LaunchableApp) -> Unit,
    onTogglePin: (String) -> Unit,
    onMovePin: (String, Int) -> Unit,
    onToggleHide: (String) -> Unit,
    onClose: () -> Unit,
) {
    var editMode by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var lastIndex by remember { mutableIntStateOf(0) }

    val apps = if (showHidden) hiddenApps else visibleApps

    BackHandler {
        when {
            menuApp != null -> menuApp = null
            else -> onClose()
        }
    }

    val restore = remember { FocusRequester() }
    LaunchedEffect(apps.size, menuApp == null, showHidden) {
        if (apps.isNotEmpty() && menuApp == null) {
            delay(150)
            runCatching { restore.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (menuApp != null) {
            val app = menuApp!!
            val isPinned = app.packageName in pinned
            val isHidden = hiddenApps.any { it.packageName == app.packageName }
            Column(
                Modifier.fillMaxSize().padding(horizontal = 56.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }
                if (!isHidden) {
                    MenuButton(if (isPinned) "Remove from dock" else "Add to dock", first) {
                        onTogglePin(app.packageName); menuApp = null
                    }
                    if (isPinned) {
                        MenuButton("Move left in dock") { onMovePin(app.packageName, -1) }
                        MenuButton("Move right in dock") { onMovePin(app.packageName, 1) }
                    }
                    MenuButton("Hide this app") { onToggleHide(app.packageName); menuApp = null }
                } else {
                    MenuButton("Show this app again", first) { onToggleHide(app.packageName); menuApp = null }
                }
                MenuButton("Close") { menuApp = null }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showHidden) "Hidden apps" else "All apps",
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    HeaderButton(if (editMode) "Done editing" else "Edit apps") { editMode = !editMode }
                    Spacer(Modifier.width(10.dp))
                    HeaderButton(if (showHidden) "Back to apps" else "Hidden (${hiddenApps.size})") {
                        showHidden = !showHidden; lastIndex = 0
                    }
                }
                if (editMode) {
                    Text(
                        "Press OK on an app to add it to the dock, move it, or hide it.",
                        color = GoldDot, fontSize = 13.sp,
                        modifier = Modifier.padding(start = 48.dp, top = 4.dp),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                        Column {
                            FocusCard(
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                focusRequester = if (index == lastIndex.coerceAtMost(apps.size - 1)) restore else null,
                                onFocused = { lastIndex = index },
                                onClick = { if (editMode || showHidden) menuApp = app else onLaunch(app) },
                            ) {
                                Box(Modifier.fillMaxSize().background(CardBg), contentAlignment = Alignment.Center) {
                                    if (app.banner != null) {
                                        Image(
                                            bitmap = app.banner,
                                            contentDescription = app.label,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Image(
                                            bitmap = app.icon,
                                            contentDescription = app.label,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(52.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                (if (app.packageName in pinned) "● " else "") + app.label,
                                color = if (app.packageName in pinned) GoldDot else Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit) {
    FocusCard(
        modifier = Modifier.height(38.dp),
        shape = RoundedCornerShape(10.dp),
        onClick = onClick,
    ) {
        Box(Modifier.background(CardBg).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MenuButton(label: String, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    FocusCard(
        modifier = Modifier.width(320.dp).height(46.dp),
        focusRequester = focusRequester,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize().background(CardBg), contentAlignment = Alignment.CenterStart) {
            Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
    Spacer(Modifier.height(10.dp))
}
