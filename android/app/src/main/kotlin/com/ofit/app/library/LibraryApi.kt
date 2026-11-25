package com.ofit.app.library

import com.ofit.protocol.WorkoutProgram
import com.ofit.protocol.WorkoutStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A YouTube video the search found. */
data class VideoHit(
    val id: String,
    val title: String,
    val channel: String,
    val durationSeconds: Int,
)

/** A workout in the community library, without its steps. */
data class LibraryEntry(
    val id: String,
    val name: String,
    val videoId: String?,
    val steps: Int,
    val durationSeconds: Int,
    val rating: Double,
    val ratings: Int,
    val origin: String,
    /** Easy/Medium/Hard and Walk/Run, from the effort numbers the server sends (null from an older server). */
    val effort: com.ofit.protocol.WorkoutRating.Rating? = null,
    /** For ranking: the rating smoothed towards 3.5, so one vote can't top the list. [rating] is the real average. */
    val score: Double = rating,
)

/** What the analysis of a video produced, before the user has decided to keep it. */
data class Analysis(
    val program: WorkoutProgram,
    val videoId: String,
    val confidence: Double,
)

class LibraryException(message: String) : Exception(message)

/**
 * The community server: search for running videos, turn one into a workout, and share workouts.
 *
 * The server holds the keys; the app holds none. Only a workout (a name and a list of steps) and
 * the YouTube id it goes with are ever uploaded. No video, no file and no account is involved.
 */
class LibraryApi(
    private val installId: () -> String,
    /** The server's address: OpenFit's own, or a self-hoster's (Keys & services). */
    private val baseUrl: () -> String = { DEFAULT_BASE_URL },
    /** The user's own Gemini key, if they added one: sent only with analyses. */
    private val geminiKey: () -> String? = { null },
    /**
     * Whether to use the hosted OpenFit server. Off: everything is done from the tablet with the
     * user's own keys, and the community library is read straight from the project's repository
     * (user 2026-11-24: nobody should need the hosted server).
     */
    private val useServer: () -> Boolean = { true },
    /** The user's own YouTube key; without one, their Gemini key is tried (same Google Cloud key). */
    private val youtubeKey: () -> String? = { null },
) {
    private fun ownGemini(): GeminiDirect =
        GeminiDirect(geminiKey() ?: throw LibraryException("Add your Gemini key under Keys & services, or turn on the OpenFit server."))

    private fun ownYouTube(): YouTubeDirect =
        YouTubeDirect(youtubeKey() ?: geminiKey() ?: throw LibraryException("Searching needs a YouTube key under Keys & services, or the OpenFit server."))

    /** The runner's own key for new AI work (facts, landmarks, analysis); cached results need none. */
    private fun keyHeader(): Map<String, String> = geminiKey()?.let { mapOf("x-gemini-key" to it) } ?: emptyMap()

    private fun noServer(what: String): Nothing =
        throw LibraryException("$what needs the OpenFit server. Turn it on under Keys & services.")

    suspend fun search(query: String): List<VideoHit> {
        if (!useServer()) return ownYouTube().search(query)
        val json = request("GET", "/api/search?q=" + URLEncoder.encode(query, "UTF-8"))
        return json.getJSONArray("videos").objects().map {
            VideoHit(
                id = it.getString("id"),
                title = it.getString("title"),
                channel = it.optString("channel"),
                durationSeconds = it.getInt("durationSeconds"),
            )
        }
    }

    /** Ask the server to watch the video and write a workout. Takes up to a few minutes. */
    suspend fun analyze(videoId: String, durationSeconds: Int = 0): Analysis {
        if (!useServer()) return analyzeHere(videoId, durationSeconds)
        val json = request(
            "POST",
            "/api/analyze",
            JSONObject().put("videoId", videoId),
            readTimeoutMillis = 330_000,
            extraHeaders = geminiKey()?.let { mapOf("x-gemini-key" to it) } ?: emptyMap(),
        )
        return Analysis(
            program = parseProgram(json.getJSONObject("program")),
            videoId = videoId,
            confidence = json.optDouble("confidence", 0.0),
        )
    }

    /** Short facts about the place a video was filmed, for the virtual coach (empty if none). */
    suspend fun facts(videoId: String): List<String> {
        if (!useServer()) {
            CommunityRepo.cache("facts20", videoId)?.let { text ->
                val list = JSONObject(text).optJSONArray("facts") ?: return emptyList()
                return (0 until list.length()).mapNotNull { list.optString(it).takeIf { f -> f.isNotBlank() } }
            }
            val key = geminiKey() ?: return emptyList()
            val title = YouTubeDirect.title(videoId) ?: return emptyList()
            return GeminiDirect(key).placeFacts(title)
        }
        val json = request("GET", "/api/facts?v=" + URLEncoder.encode(videoId, "UTF-8"), extraHeaders = keyHeader())
        val list = json.optJSONArray("facts") ?: return emptyList()
        return (0 until list.length()).mapNotNull { list.optString(it).takeIf { f -> f.isNotBlank() } }
    }

    /** Landmarks seen in a video, with when they come into view, for the virtual coach. */
    suspend fun landmarks(videoId: String): List<com.ofit.protocol.CoachTalk.Landmark> {
        if (!useServer()) {
            CommunityRepo.cache("landmarks", videoId)?.let { text ->
                return GeminiDirect.cleanLandmarks(JSONObject(text).optJSONArray("landmarks"))
            }
            val key = geminiKey() ?: return emptyList()
            val title = YouTubeDirect.title(videoId) ?: "a running video"
            return GeminiDirect(key).landmarks(videoId, title)
        }
        val json = request("GET", "/api/landmarks?v=" + URLEncoder.encode(videoId, "UTF-8"), readTimeoutMillis = 300_000, extraHeaders = keyHeader())
        val list = json.optJSONArray("landmarks") ?: return emptyList()
        return list.objects().mapNotNull {
            val name = it.optString("name").takeIf { n -> n.isNotBlank() } ?: return@mapNotNull null
            com.ofit.protocol.CoachTalk.Landmark(it.optInt("seconds", -1), name, it.optString("fact"))
        }.filter { it.seconds >= 0 }
    }

    suspend fun list(top: Boolean): List<LibraryEntry> {
        if (!useServer()) {
            // Hidden by moderation (reports, or a video gone bad): kept in the file, never shown.
            val all = CommunityRepo.programs().objects().filter { !it.has("hidden") }.map { repoEntry(it) to it.optLong("createdAt") }
            val sorted = if (top) all.sortedByDescending { it.first.score } else all.sortedByDescending { it.second }
            return sorted.take(30).map { it.first }
        }
        val json = request("GET", "/api/programs?sort=" + (if (top) "top" else "new") + "&limit=30")
        return json.getJSONArray("programs").objects().map { parseEntry(it) }
    }

    /** One workout with its steps. */
    suspend fun get(id: String): Pair<LibraryEntry, WorkoutProgram> {
        if (!useServer()) {
            val found = CommunityRepo.programs().objects().firstOrNull { it.optString("id") == id && !it.has("hidden") }
                ?: throw LibraryException("That workout is no longer in the community library.")
            return repoEntry(found) to parseProgram(found.getJSONObject("program"))
        }
        val json = request("GET", "/api/programs/" + URLEncoder.encode(id, "UTF-8"))
        return parseEntry(json) to parseProgram(json.getJSONObject("program"))
    }

    suspend fun publish(program: WorkoutProgram, videoId: String?, origin: String): String {
        if (!useServer()) noServer("Sharing")
        val steps = JSONArray()
        program.steps.forEach {
            steps.put(
                JSONObject()
                    .put("speedKph", it.speedKph)
                    .put("inclinePercent", it.inclinePercent)
                    .put("durationSeconds", it.durationSeconds),
            )
        }
        val body = JSONObject()
            .put("program", JSONObject().put("name", program.name).put("steps", steps))
            .put("origin", origin)
        if (videoId != null) body.put("videoId", videoId)
        // The sharer's own Gemini key, if any, lets the server add an AI look to its checks.
        return request("POST", "/api/programs", body, extraHeaders = keyHeader()).getString("id")
    }

    /** Report a community workout; true if it is now hidden. */
    suspend fun report(id: String, reason: String): Boolean {
        if (!useServer()) noServer("Reporting")
        val path = "/api/programs/" + URLEncoder.encode(id, "UTF-8") + "/report"
        return request("POST", path, JSONObject().put("reason", reason)).optBoolean("hidden")
    }

    /** The owner takes a workout out of the community library (server checks [token]). */
    suspend fun remove(id: String, token: String) {
        if (!useServer()) noServer("Removing")
        val path = "/api/programs/" + URLEncoder.encode(id, "UTF-8")
        request("DELETE", path, extraHeaders = mapOf("x-admin-token" to token))
    }

    suspend fun rate(id: String, stars: Int): LibraryEntry {
        if (!useServer()) noServer("Rating")
        val path = "/api/programs/" + URLEncoder.encode(id, "UTF-8") + "/rate"
        return parseEntry(request("POST", path, JSONObject().put("stars", stars)))
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        readTimeoutMillis: Int = 30_000,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl() + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("x-install-id", installId())
            extraHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw LibraryException("The server sent something unexpected (HTTP $status).")
            }
            if (status !in 200..299) {
                throw LibraryException(json.optString("error", "The server said HTTP $status."))
            }
            json
        } catch (e: IOException) {
            throw LibraryException("Could not reach the server. Check the connection and try again.")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Analysis on the tablet with the user's own key: the community's cached readings when the
     * video was read before (free), otherwise Gemini window by window, then the same builder as the
     * server.
     */
    private suspend fun analyzeHere(videoId: String, knownSeconds: Int): Analysis {
        val title = YouTubeDirect.title(videoId) ?: "Video workout"
        val cached = CommunityRepo.cache("analysis", videoId)?.let { text ->
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                fun num(n: String) = if (o.isNull(n)) null else o.optDouble(n).takeIf { !it.isNaN() }
                com.ofit.protocol.VideoProgramBuilder.WindowReading(
                    o.optInt("index", i), o.optInt("startSeconds"), o.optInt("durationSeconds"),
                    num("speedKph"), num("inclinePercent"), o.optDouble("confidence", 0.0),
                )
            }
        }
        val readings = cached ?: run {
            val seconds = knownSeconds.takeIf { it > 0 } ?: ownYouTube().details(listOf(videoId)).firstOrNull()?.durationSeconds
                ?: throw LibraryException("Video not found, or it is private.")
            if (seconds < 120) throw LibraryException("This video is too short to make a workout.")
            if (seconds > 3600) throw LibraryException("This video is longer than 60 minutes.")
            ownGemini().analyseVideo(videoId, seconds)
        }
        val program = try {
            com.ofit.protocol.VideoProgramBuilder.build(readings, title)
        } catch (e: IllegalArgumentException) {
            throw LibraryException("No speeds could be read from this video.")
        }
        val confidence = readings.map { it.confidence }.average().takeIf { !it.isNaN() } ?: 0.0
        return Analysis(program, videoId, confidence)
    }

    /** A workout from the repository's programs.json, as a library entry. */
    private fun repoEntry(json: JSONObject): LibraryEntry {
        val program = parseProgram(json.getJSONObject("program"))
        val count = json.optInt("ratingCount")
        val sum = json.optDouble("ratingSum", 0.0)
        val score = (sum + 3.5 * 5) / (count + 5)
        return LibraryEntry(
            id = json.getString("id"),
            name = program.name,
            videoId = if (json.isNull("videoId")) null else json.optString("videoId").ifEmpty { null },
            steps = program.steps.size,
            durationSeconds = program.totalDurationSeconds,
            rating = if (count > 0) Math.round(sum / count * 10) / 10.0 else 0.0,
            ratings = count,
            score = score,
            origin = json.optString("origin", "manual"),
            effort = com.ofit.protocol.WorkoutRating.of(program),
        )
    }

    private fun parseEntry(json: JSONObject) = LibraryEntry(
        id = json.getString("id"),
        name = json.getString("name"),
        videoId = if (json.isNull("videoId")) null else json.optString("videoId").ifEmpty { null },
        steps = json.getInt("steps"),
        durationSeconds = json.getInt("durationSeconds"),
        rating = json.getDouble("rating"),
        ratings = json.getInt("ratings"),
        score = json.optDouble("score").takeIf { !it.isNaN() } ?: json.getDouble("rating"),
        origin = json.optString("origin", "manual"),
        effort = json.optDouble("mets").takeIf { !it.isNaN() && it > 0 }?.let { mets ->
            com.ofit.protocol.WorkoutRating.classify(mets, json.optDouble("runShare", 0.0), json.getInt("durationSeconds"))
        },
    )

    private fun parseProgram(json: JSONObject) = WorkoutProgram(
        name = json.getString("name"),
        videoPaceKph = json.optDouble("videoPaceKph").takeIf { !it.isNaN() && it in 0.5..30.0 },
        steps = json.getJSONArray("steps").objects().map {
            WorkoutStep(
                speedKph = it.getDouble("speedKph"),
                inclinePercent = it.getDouble("inclinePercent"),
                durationSeconds = it.getInt("durationSeconds"),
            )
        },
    ).withBounds(
        json.optInt("introEndSeconds", -1).takeIf { it > 0 },
        json.optInt("outroStartSeconds", -1).takeIf { it > 0 },
    )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    companion object {
        const val DEFAULT_BASE_URL = com.ofit.protocol.ServerUrl.DEFAULT

        /** The app's one client, set by TreadmillController, for places without the controller (the coach). */
        @Volatile var shared: LibraryApi? = null
    }
}
