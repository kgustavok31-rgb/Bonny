package com.larkplayer

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PlaylistStore(app)
    private val lib = LibraryStore(app)
    private var lastLogged: String? = null
    private var controller: MediaController? = null

    var songs by mutableStateOf(listOf<Song>()); private set
    var playlists by mutableStateOf(store.load()); private set
    var recents by mutableStateOf(lib.recents()); private set
    var favorites by mutableStateOf(lib.favorites()); private set
    var currentId by mutableStateOf<String?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var shuffle by mutableStateOf(false); private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(listener)
            sync(c)
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync(p: Player) {
        isPlaying = p.isPlaying
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        val id = p.currentMediaItem?.mediaId
        currentId = id
        if (id != null && id != lastLogged) {
            lastLogged = id
            id.toLongOrNull()?.let { addRecent(it) }
        }
        refresh()
    }

    fun refresh() {
        val c = controller ?: return
        position = c.currentPosition
        duration = c.duration.coerceAtLeast(0L)
    }

    fun loadSongs() { songs = MusicRepository.load(getApplication()) }

    fun play(list: List<Song>, index: Int) {
        val c = controller ?: return
        val items = list.map {
            MediaItem.Builder().setMediaId(it.id.toString()).setUri(it.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).setAlbumTitle(it.album).build())
                .build()
        }
        c.setMediaItems(items, index, 0L)
        c.prepare(); c.play()
    }

    fun playPlaylist(p: Playlist) {
        val list = p.ids.mapNotNull { id -> songs.find { it.id == id } }
        if (list.isNotEmpty()) play(list, 0)
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms); position = ms }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        playlists = playlists + Playlist(name.trim(), emptyList()); store.save(playlists)
    }

    fun addToPlaylist(name: String, songId: Long) {
        playlists = playlists.map {
            if (it.name == name && songId !in it.ids) it.copy(ids = it.ids + songId) else it
        }
        store.save(playlists)
    }

    fun deletePlaylist(name: String) {
        playlists = playlists.filterNot { it.name == name }; store.save(playlists)
    }

    private fun addRecent(id: Long) {
        recents = (listOf(id) + recents.filter { it != id }).take(20)
        lib.saveRecents(recents)
    }

    fun toggleFavorite(id: Long) {
        favorites = if (id in favorites) favorites - id else favorites + id
        lib.saveFavorites(favorites)
    }

    override fun onCleared() { controller?.release() }
}
