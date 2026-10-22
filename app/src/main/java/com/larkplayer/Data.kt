package com.larkplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

data class Song(val id: Long, val title: String, val artist: String, val durationMs: Long, val uri: Uri)
data class Playlist(val name: String, val ids: List<Long>)

object MusicRepository {
    fun load(ctx: Context): List<Song> {
        val out = mutableListOf<Song>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION
        )
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += Song(
                    id, c.getString(1) ?: "Sem título", c.getString(2) ?: "Desconhecido", c.getLong(3),
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                )
            }
        }
        return out
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
        list.forEach { p ->
            arr.put(JSONObject().put("name", p.name).put("ids", JSONArray(p.ids)))
        }
        prefs.edit().putString("data", arr.toString()).apply()
    }
}
