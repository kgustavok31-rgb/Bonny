package com.larkplayer

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

data class Song(
    val id: Long, val title: String, val artist: String, val album: String,
    val albumId: Long, val durationMs: Long, val uri: Uri
)
data class Playlist(val name: String, val ids: List<Long>)

object MusicRepository {
    fun load(ctx: Context): List<Song> {
        val out = mutableListOf<Song>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID
        )
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += Song(
                    id, c.getString(1) ?: "Sem título", c.getString(2) ?: "Desconhecido",
                    c.getString(4) ?: "Álbum desconhecido", c.getLong(5), c.getLong(3),
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                )
            }
        }
        return out
    }
}

object ArtLoader {
    private val cache = LruCache<Long, Bitmap>(80)
    private val misses = Collections.synchronizedSet(HashSet<Long>())

    fun key(s: Song): Long = if (s.albumId > 0) s.albumId else -s.id
    fun cached(s: Song): Bitmap? = cache.get(key(s))

    fun load(ctx: Context, s: Song): Bitmap? {
        val k = key(s)
        cache.get(k)?.let { return it }
        if (k in misses) return null
        val bmp = try {
            if (Build.VERSION.SDK_INT >= 29) {
                ctx.contentResolver.loadThumbnail(s.uri, Size(512, 512), null)
            } else if (s.albumId > 0) {
                val u = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), s.albumId)
                ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) }
            } else null
        } catch (e: Exception) { null }
        if (bmp != null) cache.put(k, bmp) else misses.add(k)
        return bmp
    }
}

class PlaylistStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun load(): List<Playlist> {
        val arr = JSONArray(prefs.getString("data", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val ids = o.getJSONArray("ids")
            Playlist(o.getString("name"), (0 until ids.length()).map { i -> ids.getLong(i) })
        }
    }

    fun save(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p -> arr.put(JSONObject().put("name", p.name).put("ids", JSONArray(p.ids))) }
        prefs.edit().putString("data", arr.toString()).apply()
    }
}

class LibraryStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("library", Context.MODE_PRIVATE)
    private fun read(key: String) = (prefs.getString(key, "") ?: "").split(",").mapNotNull { it.toLongOrNull() }
    fun recents(): List<Long> = read("recents")
    fun favorites(): Set<Long> = read("favorites").toSet()
    fun saveRecents(l: List<Long>) = prefs.edit().putString("recents", l.joinToString(",")).apply()
    fun saveFavorites(s: Set<Long>) = prefs.edit().putString("favorites", s.joinToString(",")).apply()
}
