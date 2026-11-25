package com.ofit.app.library

import com.ofit.protocol.CoachTalk
import com.ofit.protocol.VideoProgramBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * OpenFit without the hosted server (user 2026-11-24: nobody should need it). The tablet reads the
 * community data straight from the project's public repository, and calls Gemini and YouTube itself
 * with the runner's own key, which never leaves the tablet except to Google.
 */
internal object Http {
    /** GET [url]; the body, or null on 404. */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), readTimeoutMillis: Int = 30_000): String? =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000
                c.readTimeout = readTimeoutMillis
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                val status = c.responseCode
                if (status == 404) return@withContext null
                val text = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw HttpStatus(status, text)
                text
            } catch (e: IOException) {
                throw LibraryException("Could not reach ${URL(url).host}. Check the connection and try again.")
            } finally {
                c.disconnect()
            }
        }

    suspend fun postJson(url: String, body: JSONObject, headers: Map<String, String>, readTimeoutMillis: Int): String =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 15_000
                c.readTimeout = readTimeoutMillis
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = c.responseCode
                val text = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw HttpStatus(status, text)
                text
            } catch (e: IOException) {
                throw LibraryException("Could not reach ${URL(url).host}. Check the connection and try again.")
            } finally {
                c.disconnect()
            }
        }

    class HttpStatus(val status: Int, val body: String) : Exception("HTTP $status")
}

/** The community data in the project's repository (community/ on the main branch), read only. */
object CommunityRepo {
    const val RAW = "https://raw.githubusercontent.com/BaconWappedBitcoin/openfit/main/community"

    suspend fun programs(): JSONArray =
        try {
            Http.get("$RAW/programs.json")?.let { JSONArray(it) } ?: JSONArray()
        } catch (e: Http.HttpStatus) {
            throw LibraryException("The community library could not be read (HTTP ${e.status}).")
        }

    /** A cached AI result for [videoId] (kind: analysis, facts20, landmarks), or null. */
    suspend fun cache(kind: String, videoId: String): String? {
        if (!Regex("^[A-Za-z0-9_-]{11}$").matches(videoId)) return null
        return try {
            Http.get("$RAW/cache/$kind/$videoId.json")
        } catch (e: Http.HttpStatus) {
            null
        }
    }
}

/** YouTube Data API v3 with the runner's own key, and YouTube's key-free embed info. */
class YouTubeDirect(private val key: String) {
    private val api = "https://www.googleapis.com/youtube/v3"

    private suspend fun get(path: String, params: Map<String, String>): JSONObject {
        val query = params.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        return try {
            JSONObject(Http.get("$api/$path?$query", mapOf("x-goog-api-key" to key)) ?: "{}")
        } catch (e: Http.HttpStatus) {
            throw LibraryException(
                if (e.status == 403) "YouTube refused the key. Turn on \"YouTube Data API v3\" for it in Google Cloud, or add a YouTube key."
                else "YouTube said HTTP ${e.status}.",
            )
        }
    }

    suspend fun details(ids: List<String>): List<VideoHit> {
        if (ids.isEmpty()) return emptyList()
        val data = get("videos", mapOf("part" to "snippet,contentDetails,status", "id" to ids.joinToString(","), "maxResults" to "50"))
        val items = data.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { items.getJSONObject(it) }.filter {
            it.optJSONObject("status")?.optBoolean("embeddable", true) != false
        }.map {
            VideoHit(
                id = it.getString("id"),
                title = it.getJSONObject("snippet").optString("title"),
                channel = it.getJSONObject("snippet").optString("channelTitle"),
                durationSeconds = isoSeconds(it.getJSONObject("contentDetails").optString("duration")),
            )
        }
    }

    suspend fun search(q: String): List<VideoHit> {
        val found = get(
            "search",
            mapOf("part" to "snippet", "type" to "video", "videoEmbeddable" to "true", "maxResults" to "20", "q" to "$q running", "safeSearch" to "strict"),
        )
        val items = found.optJSONArray("items") ?: return emptyList()
        val ids = (0 until items.length()).mapNotNull { items.getJSONObject(it).optJSONObject("id")?.optString("videoId") }
            .filter { Regex("^[A-Za-z0-9_-]{11}$").matches(it) }
        return details(ids).filter { it.durationSeconds > 0 }
    }

    companion object {
        fun isoSeconds(iso: String): Int {
            val m = Regex("^P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$").find(iso) ?: return 0
            val (d, h, mi, s) = m.destructured
            return (d.toIntOrNull() ?: 0) * 86400 + (h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
        }

        /** A video's title from YouTube's oEmbed, which needs no key; null if it cannot be read. */
        suspend fun title(videoId: String): String? = runCatching {
            Http.get("https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8"))
                ?.let { JSONObject(it).optString("title").ifBlank { null } }
        }.getOrNull()
    }
}

/** Gemini with the runner's own key: the same prompts and answers as the server's lib/gemini.ts. */
class GeminiDirect(private val key: String, private val model: String = DEFAULT_MODEL) {

    private suspend fun call(body: JSONObject, attempts: Int = 4, readTimeoutMillis: Int = 120_000): JSONObject {
        var attempt = 1
        while (true) {
            try {
                val text = Http.postJson(
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                    body,
                    mapOf("x-goog-api-key" to key),
                    readTimeoutMillis,
                )
                val answer = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                return JSONObject(answer)
            } catch (e: Http.HttpStatus) {
                if (e.status in setOf(429, 500, 503, 504) && attempt < attempts) {
                    delay((1L shl attempt) * 1000)
                    attempt++
                    continue
                }
                throw LibraryException(
                    when (e.status) {
                        400, 403 -> "Gemini refused the key or the request (HTTP ${e.status}). Check your Gemini key."
                        429 -> "Your Gemini key is over its limit for now. Try again later."
                        else -> "Gemini said HTTP ${e.status}."
                    },
                )
            }
        }
    }

    /** Read every 30 s window of the video, a few at a time. Any failed window fails the run. */
    suspend fun analyseVideo(videoId: String, durationSeconds: Int, concurrency: Int = 8): List<VideoProgramBuilder.WindowReading> =
        coroutineScope {
            val gate = Semaphore(concurrency)
            VideoProgramBuilder.windows(durationSeconds).map { (i, s, e) ->
                async { gate.withPermit { window(videoId, i, s, e) } }
            }.awaitAll()
        }

    private suspend fun window(videoId: String, index: Int, start: Int, end: Int): VideoProgramBuilder.WindowReading {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject()
                    .put("file_data", JSONObject().put("file_uri", "https://www.youtube.com/watch?v=$videoId"))
                    .put("video_metadata", JSONObject().put("start_offset", "${start}s").put("end_offset", "${end}s")))
                .put(JSONObject().put("text", windowPrompt(end - start))))))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.2)
                .put("responseMimeType", "application/json")
                .put("responseSchema", JSONObject(WINDOW_SCHEMA)))
        val a = call(body)
        fun num(name: String) = a.optDouble(name).takeIf { !it.isNaN() && !it.isInfinite() }
        return VideoProgramBuilder.WindowReading(
            index, start, end - start, num("speed_kph"), num("incline_percent"), num("confidence") ?: 0.0,
            a.optString("terrain"), a.optString("note").take(300),
            part = a.optString("part").takeIf { it == "intro" || it == "outro" } ?: "running",
        )
    }

    /** Short facts about the place a video was filmed, from its title only. */
    suspend fun placeFacts(title: String): List<String> {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
                "A treadmill runner is watching a scenic running video titled \"$title\".\n" +
                    "If the title names a real place (a city, park, region, trail or landmark), give that place's name and up to 20 short,\n" +
                    "interesting, accurate facts about it that a running coach could mention: history, nature, landmarks, records, culture, food, famous people, sport. All different.\n" +
                    "Each fact one sentence of at most 22 words, friendly, no numbers you are unsure of, nothing about the video or YouTube.\n" +
                    "If no real place is named, return place null and no facts.")))))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.4)
                .put("responseMimeType", "application/json")
                .put("responseSchema", JSONObject("""{"type":"OBJECT","properties":{"place":{"type":"STRING","nullable":true},"facts":{"type":"ARRAY","items":{"type":"STRING"}}},"required":["place","facts"]}""")))
        val a = call(body)
        if (a.isNull("place") || a.optString("place").isBlank()) return emptyList()
        val list = a.optJSONArray("facts") ?: return emptyList()
        return (0 until list.length()).map { list.optString(it).replace(Regex("\\s+"), " ").trim() }
            .filter { it.length in 10..200 }.take(20)
    }

    /** Landmarks along the video and when they come into view, from one low-detail read of it. */
    suspend fun landmarks(videoId: String, title: String): List<CoachTalk.Landmark> {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject()
                    .put("file_data", JSONObject().put("file_uri", "https://www.youtube.com/watch?v=$videoId"))
                    .put("video_metadata", JSONObject().put("fps", 0.2)))
                .put(JSONObject().put("text",
                    "This is a first-person running video titled \"$title\". List the notable landmarks the runner passes:\n" +
                        "famous buildings, monuments, bridges, parks, lakes, mountains, stadiums, statues or well-known streets.\n" +
                        "For each, give the time in seconds from the start of the video when it first comes clearly into view, its name,\n" +
                        "and one friendly, accurate fact about it of at most 22 words. Only real, recognisable landmarks you are confident about;\n" +
                        "skip ordinary houses, shops and roads. At most 20, in time order, at least 20 seconds apart. If there are none, return none.")))))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.2)
                .put("mediaResolution", "MEDIA_RESOLUTION_LOW")
                .put("responseMimeType", "application/json")
                .put("responseSchema", JSONObject("""{"type":"OBJECT","properties":{"landmarks":{"type":"ARRAY","items":{"type":"OBJECT","properties":{"seconds":{"type":"NUMBER"},"name":{"type":"STRING"},"fact":{"type":"STRING"}},"required":["seconds","name","fact"]}}},"required":["landmarks"]}""")))
        return cleanLandmarks(call(body, attempts = 2, readTimeoutMillis = 300_000).optJSONArray("landmarks"))
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash"

        /** In the video, named, short, in order, at least 20 s apart, at most 20 (as the server's cleanLandmarks). */
        fun cleanLandmarks(list: JSONArray?): List<CoachTalk.Landmark> {
            if (list == null) return emptyList()
            fun tidy(s: String, max: Int) = s.replace(Regex("[\\u0000-\\u001f\\u007f]"), " ").replace(Regex("\\s+"), " ").trim().take(max)
            val all = (0 until list.length()).mapNotNull { i ->
                val o = list.optJSONObject(i) ?: return@mapNotNull null
                val seconds = o.optDouble("seconds", -1.0).takeIf { !it.isNaN() }?.let { Math.round(it).toInt() } ?: -1
                val name = tidy(o.optString("name"), 80)
                if (seconds !in 0..4 * 3600 || name.length < 2) null else CoachTalk.Landmark(seconds, name, tidy(o.optString("fact"), 200))
            }.sortedBy { it.seconds }
            val spaced = ArrayList<CoachTalk.Landmark>()
            for (l in all) {
                val last = spaced.lastOrNull()
                if (last != null && (l.seconds - last.seconds < 20 || last.name.equals(l.name, ignoreCase = true))) continue
                spaced += l
            }
            return spaced.take(20)
        }

        private fun windowPrompt(seconds: Int) = """You are analysing a window of a running video so that a treadmill program can be built from it.
The window is $seconds seconds long and shows a runner, usually filmed from a chest or head
camera on the runner themselves, moving along a path.

How to judge the two numbers:
- speed_kph is the runner's ground speed in kilometres per hour. Estimate it from the footfall
  cadence multiplied by the stride length: an adult runner's stride is roughly 1.0 to 1.3 metres,
  so use 1.1 metres when you are unsure. When the camera is on the runner, each footfall makes one
  downward bob, so counting the bobs over ten seconds gives roughly (bobs per 10 s) * 12 steps per
  minute. If nothing in the window allows a judgement, give your best estimate and set the
  confidence low.
- incline_percent is the gradient of the ground under the runner in percent, where 10 means a ten
  per cent uphill slope and -10 a ten per cent downhill slope. Judge it from the visible slope of
  the path against the horizon and from the runner's posture.
- confidence is how sure you are about the two numbers together. Be honest: a low confidence is
  more useful than a confident guess.
- part says what the window mostly is: "intro" for an opening before the run itself gets going
  (title cards, a channel intro, talking to the camera, standing still, a map or drone shot before
  the start), "outro" for a closing after the run has ended (end cards, credits, subscribe screens,
  standing or talking after finishing), and "running" for everything else, including walking,
  short stops at crossings and any footage of moving along the route."""

        private const val WINDOW_SCHEMA = """{"type":"OBJECT","properties":{"speed_kph":{"type":"NUMBER"},"incline_percent":{"type":"NUMBER"},"terrain":{"type":"STRING","enum":["flat","rolling","uphill","downhill","stairs","trail","road","water","other"]},"confidence":{"type":"NUMBER"},"note":{"type":"STRING"},"part":{"type":"STRING","enum":["intro","running","outro"]}},"required":["speed_kph","incline_percent","terrain","confidence","note","part"]}"""
    }
}
