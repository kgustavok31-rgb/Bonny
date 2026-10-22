package com.larkplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import kotlinx.coroutines.delay

private val AUDIO_PERM =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: PlayerViewModel = viewModel()) {
    val ctx = LocalContext.current
    fun hasAudio() = ContextCompat.checkSelfPermission(ctx, AUDIO_PERM) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasAudio()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasAudio() }

    LaunchedEffect(Unit) {
        if (!granted) {
            val perms = mutableListOf(AUDIO_PERM)
            if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
            launcher.launch(perms.toTypedArray())
        }
    }
    LaunchedEffect(granted) { if (granted) vm.loadSongs() }
    LaunchedEffect(Unit) { while (true) { vm.refresh(); delay(500) } }

    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Lark Player") }) },
        bottomBar = { MiniPlayer(vm) }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Músicas") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Playlists") })
            }
            if (!granted) Text("Permita o acesso às músicas para continuar.", Modifier.padding(16.dp))
            else if (tab == 0) SongList(vm) else PlaylistList(vm)
        }
    }
}

@Composable
fun SongList(vm: PlayerViewModel) {
    if (vm.songs.isEmpty()) { Text("Nenhuma música encontrada.", Modifier.padding(16.dp)); return }
    LazyColumn {
        itemsIndexed(vm.songs, key = { _, s -> s.id }) { i, s ->
            var menu by remember { mutableStateOf(false) }
            val playing = vm.currentId == s.id.toString()
            ListItem(
                modifier = Modifier.clickable { vm.play(vm.songs, i) },
                headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                supportingContent = { Text("${s.artist} · ${fmt(s.durationMs)}", maxLines = 1) },
                trailingContent = {
                    Box {
                        TextButton({ menu = true }) { Text("⋮") }
                        DropdownMenu(menu, { menu = false }) {
                            if (vm.playlists.isEmpty()) DropdownMenuItem({ Text("Crie uma playlist primeiro") }, { menu = false })
                            vm.playlists.forEach { p ->
                                DropdownMenuItem({ Text("Adicionar a ${p.name}") }, { vm.addToPlaylist(p.name, s.id); menu = false })
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun PlaylistList(vm: PlayerViewModel) {
    var dialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column {
        Button({ dialog = true }, Modifier.padding(12.dp)) { Text("Nova playlist") }
        LazyColumn {
            itemsIndexed(vm.playlists, key = { _, p -> p.name }) { _, p ->
                ListItem(
                    modifier = Modifier.clickable { vm.playPlaylist(p) },
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text("${p.ids.size} músicas") },
                    trailingContent = { TextButton({ vm.deletePlaylist(p.name) }) { Text("Excluir") } }
                )
            }
        }
    }
    if (dialog) AlertDialog(
        onDismissRequest = { dialog = false },
        title = { Text("Nova playlist") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nome") }) },
        confirmButton = { TextButton({ vm.createPlaylist(name); name = ""; dialog = false }) { Text("Criar") } },
        dismissButton = { TextButton({ dialog = false }) { Text("Cancelar") } }
    )
}

@Composable
fun MiniPlayer(vm: PlayerViewModel) {
    val song = vm.songs.find { it.id.toString() == vm.currentId } ?: return
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(song.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            Slider(
                value = if (vm.duration > 0) vm.position.toFloat() / vm.duration else 0f,
                onValueChange = { vm.seekTo((it * vm.duration).toLong()) }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton({ vm.toggleShuffle() }) { Text(if (vm.shuffle) "🔀 on" else "🔀") }
                TextButton({ vm.previous() }) { Text("⏮") }
                FilledIconButton({ vm.togglePlay() }) { Text(if (vm.isPlaying) "⏸" else "▶") }
                TextButton({ vm.next() }) { Text("⏭") }
                TextButton({ vm.cycleRepeat() }) {
                    Text(when (vm.repeatMode) { Player.REPEAT_MODE_ALL -> "🔁 todas"; Player.REPEAT_MODE_ONE -> "🔂 uma"; else -> "🔁" })
                }
            }
        }
    }
}
