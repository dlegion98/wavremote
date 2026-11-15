package com.dayo.wavremote

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** When this app first and last saw a device online (phone clock, milliseconds). */
data class Seen(val first: Long, val last: Long)

/**
 * Things the app remembers about devices on the phone itself: which ones are favourites and
 * when each was last online. Nothing here is sent to the router.
 */
object DeviceBook {
    private const val KEY_FAV = "favMacs"
    private const val KEY_SEEN = "deviceSeen"
    private const val MAX = 300

    fun key(mac: String): String = mac.uppercase()

    fun loadFavourites(prefs: SharedPreferences): Set<String> =
        (prefs.getStringSet(KEY_FAV, null) ?: emptySet()).toSet()

    fun saveFavourites(prefs: SharedPreferences, favourites: Set<String>) {
        prefs.edit().putStringSet(KEY_FAV, favourites.toSet()).apply()
    }

    fun loadSeen(prefs: SharedPreferences): Map<String, Seen> {
        val text = prefs.getString(KEY_SEEN, null) ?: return emptyMap()
        return try {
            val o = JSONObject(text)
            val out = HashMap<String, Seen>()
            val names = o.keys()
            while (names.hasNext()) {
                val k = names.next()
                val a = o.optJSONArray(k) ?: continue
                if (a.length() >= 2) out[k] = Seen(a.optLong(0), a.optLong(1))
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveSeen(prefs: SharedPreferences, seen: Map<String, Seen>) {
        val o = JSONObject()
        for ((k, v) in seen) o.put(k, JSONArray().put(v.first).put(v.last))
        prefs.edit().putString(KEY_SEEN, o.toString()).apply()
    }

    /** Marks every online device as seen at [now]; keeps only the [MAX] most recently seen. */
    fun record(seen: Map<String, Seen>, list: DeviceList, now: Long): Map<String, Seen> {
        if (list.online.isEmpty()) return seen
        val out = HashMap<String, Seen>(seen)
        for (d in list.online) {
            val k = key(d.mac)
            val old = out[k]
            out[k] = Seen(old?.first ?: now, now)
        }
        if (out.size <= MAX) return out
        return out.entries.sortedByDescending { it.value.last }.take(MAX).associate { it.key to it.value }
    }
}

/** "5 minutes ago", "yesterday", "on 3 Nov 2026". */
fun formatAgo(at: Long, now: Long = System.currentTimeMillis()): String {
    val min = (now - at) / 60_000
    return when {
        min < 2 -> "just now"
        min < 60 -> "$min minutes ago"
        min < 120 -> "an hour ago"
        min < 60 * 24 -> "${min / 60} hours ago"
        min < 60 * 48 -> "yesterday"
        min < 60 * 24 * 14 -> "${min / (60 * 24)} days ago"
        else -> "on " + formatDay(at)
    }
}

fun formatDay(at: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(at))
