package dev.ytcar.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import dev.ytcar.data.ChannelItem
import dev.ytcar.data.Entry
import dev.ytcar.data.VideoItem
import dev.ytcar.data.formatDuration
import dev.ytcar.player.PlayerHub
import dev.ytcar.player.Status
import android.os.Build

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(ctx) else darkColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun AppRoot(vm: MainViewModel, setFullscreen: (Boolean) -> Unit) {
    when (val r = vm.stack.last()) {
        Route.Home -> HomeScreen(vm)
        is Route.Search -> SearchScreen(vm, r)
        is Route.Channel -> ChannelScreen(vm, r)
        is Route.Player -> PlayerScreen(vm, r.item, setFullscreen)
    }
}

// ---- shared pieces ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Bar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = { actions() },
    )
}

@Composable
private fun EntryList(
    state: ListState,
    vm: MainViewModel,
    padding: PaddingValues,
    header: (@Composable () -> Unit)? = null,
) {
    val lazy: LazyListState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf {
        val last = lazy.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        last >= lazy.layoutInfo.totalItemsCount - 3
    } }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) state.loadMore() }

    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(state = lazy, contentPadding = PaddingValues(bottom = 24.dp)) {
            if (header != null) item { header() }
            items(state.items) { e -> EntryRow(e) { vm.open(e) } }
        }
        if (state.loading && state.items.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
        state.error?.let { msg ->
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(msg, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { state.reload() }) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun EntryRow(e: Entry, onClick: () -> Unit) {
    when (e) {
        is Entry.Video -> VideoRow(e.item, onClick)
        is Entry.Channel -> ChannelRow(e.item, onClick)
    }
}

@Composable
private fun VideoRow(v: VideoItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Box {
            AsyncImage(
                model = v.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            val badge = if (v.live) "LIVE" else formatDuration(v.durationSec)
            if (badge.isNotEmpty()) Text(
                badge, color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(v.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ChannelRow(c: ChannelItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = c.avatar, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (c.subscribers > 0) Text("%,d subscribers".format(c.subscribers), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---- screens ---------------------------------------------------------------------------

@Composable
fun HomeScreen(vm: MainViewModel) {
    LaunchedEffect(Unit) { vm.home.start() }
    Scaffold(topBar = {
        Bar("YouTube Car", null) {
            IconButton(onClick = { vm.openSearch() }) { Icon(Icons.Default.Search, contentDescription = "Search") }
        }
    }) { pad -> EntryList(vm.home, vm, pad) }
}

@Composable
fun SearchScreen(vm: MainViewModel, route: Route.Search) {
    Scaffold(topBar = {
        Row(Modifier.statusBarsPadding().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            OutlinedTextField(
                value = route.query, onValueChange = { route.query = it },
                modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Search YouTube") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { route.submit() }),
                trailingIcon = {
                    if (route.query.isNotEmpty()) IconButton(onClick = { route.query = "" }) {
                        Icon(Icons.Default.Close, "Clear")
                    }
                },
            )
        }
    }) { pad ->
        val l = route.list
        if (l != null) EntryList(l, vm, pad)
    }
}

@Composable
fun ChannelScreen(vm: MainViewModel, route: Route.Channel) {
    LaunchedEffect(route) { route.list.start() }
    Scaffold(topBar = { Bar(route.name, { vm.pop() }) }) { pad ->
        EntryList(route.list, vm, pad) {
            route.header?.let { h ->
                Column(Modifier.padding(16.dp)) {
                    if (h.channel.subscribers > 0) Text("%,d subscribers".format(h.channel.subscribers), style = MaterialTheme.typography.labelLarge)
                    h.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(vm: MainViewModel, item: VideoItem, setFullscreen: (Boolean) -> Unit) {
    LaunchedEffect(item.url) { PlayerHub.play(item) }
    val status by PlayerHub.status.collectAsState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var forced by remember { mutableStateOf(false) }
    val fullscreen = forced || landscape

    LaunchedEffect(fullscreen) { setFullscreen(fullscreen) }
    DisposableEffect(Unit) { onDispose { setFullscreen(false) } }

    val video: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        player = PlayerHub.player
                        setFullscreenButtonClickListener { forced = it }
                    }
                },
                update = { it.player = PlayerHub.player; it.setFullscreenButtonState(fullscreen) },
                onRelease = { it.player = null },
            )
            if (status is Status.Loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    if (fullscreen) {
        video(Modifier.fillMaxSize())
        BackHandlerFullscreen(landscape) { forced = false }
    } else {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            video(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(item.channel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                (status as? Status.Error)?.let {
                    Text(it.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { PlayerHub.current.value = null; PlayerHub.play(item) }) { Text("Retry") }
                }
                item.channelUrl?.let { cu -> TextButton(onClick = { vm.openChannel(cu, item.channel) }) { Text("Open channel") } }
                TextButton(onClick = { vm.pop() }) { Text("Back to browsing (audio keeps playing)") }
            }
        }
    }
}

@Composable
private fun BackHandlerFullscreen(landscape: Boolean, exit: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = !landscape) { exit() }
}
