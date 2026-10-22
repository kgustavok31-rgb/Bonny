package com.larkplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import kotlinx.coroutines.delay

private val AUDIO_PERM =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

@Composable
fun Art(song: Song?, size: Dp, radius: Dp = 12.dp) {
    val h = ((song?.title?.hashCode() ?: 0) and 0x7fffffff) % 360
    val c1 = Color.hsv(h.toFloat(), 0.55f, 0.85f)
    val c2 = Color.hsv(((h + 50) % 360).toFloat(), 0.65f, 0.55f)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(Brush.linearGradient(listOf(c1, c2))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(size * 0.5f))
    }
}

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
    var query by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(false) }
    BackHandler(full) { full = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { MiniPlayer(vm) { full = true } }
        ) { pad ->
            Column(Modifier.padding(pad)) {
                Text(
                    "Lark Player", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(tab == 0, { tab = 0 }, { Text("Músicas") })
                    FilterChip(tab == 1, { tab = 1 }, { Text("Playlists") })
                }
                Spacer(Modifier.height(8.dp))
                if (!granted) {
                    Text("Permita o acesso às músicas para continuar.", Modifier.padding(20.dp))
                } else if (tab == 0) {
                    OutlinedTextField(
                        query, { query = it }, singleLine = true,
                        placeholder = { Text("Buscar música ou artista") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                    SongList(vm, query)
                } else PlaylistList(vm)
            }
        }
        AnimatedVisibility(full, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            FullPlayer(vm) { full = false }
        }
    }
}

@Composable
fun SongList(vm: PlayerViewModel, query: String) {
    val list = vm.songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    if (list.isEmpty()) {
        Text("Nenhuma música encontrada.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)) {
        items(list.size, key = { list[it].id }) { i ->
            val s = list[i]
            var menu by remember { mutableStateOf(false) }
            val playing = vm.currentId == s.id.toString()
            Row(
                Modifier.fillMaxWidth().clickable { vm.play(list, i) }.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(s, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                        color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${s.artist} · ${fmt(s.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais opções") }
                    DropdownMenu(menu, { menu = false }) {
                        if (vm.playlists.isEmpty()) DropdownMenuItem({ Text("Crie uma playlist primeiro") }, { menu = false })
                        vm.playlists.forEach { p ->
                            DropdownMenuItem(
                                { Text("Adicionar a ${p.name}") },
                                { vm.addToPlaylist(p.name, s.id); menu = false },
                                leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistList(vm: PlayerViewModel) {
    var dialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column {
        FilledTonalButton({ dialog = true }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Nova playlist")
        }
        if (vm.playlists.isEmpty())
            Text("Nenhuma playlist ainda.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.playlists, key = { it.name }) { p ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.playPlaylist(p) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.ids.size} músicas", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ vm.playPlaylist(p) }) { Icon(Icons.Rounded.PlayArrow, "Tocar") }
                        IconButton({ vm.deletePlaylist(p.name) }) { Icon(Icons.Rounded.Delete, "Excluir") }
                    }
                }
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
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val song = vm.songs.find { it.id.toString() == vm.currentId } ?: return
    Surface(
        shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clickable { onOpen() }
    ) {
        Column {
            Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(song, 46.dp, 12.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ vm.togglePlay() }) {
                    Icon(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Tocar ou pausar", Modifier.size(30.dp))
                }
                IconButton({ vm.next() }) { Icon(Icons.Rounded.SkipNext, "Próxima", Modifier.size(28.dp)) }
            }
            LinearProgressIndicator(
                progress = { if (vm.duration > 0) vm.position.toFloat() / vm.duration else 0f },
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        }
    }
}

@Composable
fun FullPlayer(vm: PlayerViewModel, onClose: () -> Unit) {
    val song = vm.songs.find { it.id.toString() == vm.currentId }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (song == null) return@Surface
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Fechar", Modifier.size(32.dp)) }
                Text("Tocando agora", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.weight(1f))
            Art(song, 300.dp, 28.dp)
            Spacer(Modifier.height(32.dp))
            Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Slider(
                value = if (vm.duration > 0) vm.position.toFloat() / vm.duration else 0f,
                onValueChange = { vm.seekTo((it * vm.duration).toLong()) }
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(vm.position), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(vm.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val on = MaterialTheme.colorScheme.primary
                val off = MaterialTheme.colorScheme.onSurfaceVariant
                IconButton({ vm.toggleShuffle() }) { Icon(Icons.Rounded.Shuffle, "Aleatório", tint = if (vm.shuffle) on else off) }
                IconButton({ vm.previous() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Anterior", Modifier.size(36.dp)) }
                FilledIconButton({ vm.togglePlay() }, Modifier.size(72.dp)) {
                    Icon(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Tocar ou pausar", Modifier.size(40.dp))
                }
                IconButton({ vm.next() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Próxima", Modifier.size(36.dp)) }
                IconButton({ vm.cycleRepeat() }) {
                    Icon(
                        if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repetir", tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) off else on
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
