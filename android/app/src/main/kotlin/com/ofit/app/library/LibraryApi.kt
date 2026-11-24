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
    private val baseUrl: String = DEFAULT_BASE_URL,
    /** The user's own Gemini key, if they added one: sent only with analyses. */
    private val geminiKey: () -> String? = { null },
) {

    suspend fun search(query: String): List<VideoHit> {
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
    suspend fun analyze(videoId: String): Analysis {
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

    suspend fun list(top: Boolean): List<LibraryEntry> {
        val json = request("GET", "/api/programs?sort=" + (if (top) "top" else "new") + "&limit=30")
        return json.getJSONArray("programs").objects().map { parseEntry(it) }
    }

    /** One workout with its steps. */
    suspend fun get(id: String): Pair<LibraryEntry, WorkoutProgram> {
        val json = request("GET", "/api/programs/" + URLEncoder.encode(id, "UTF-8"))
        return parseEntry(json) to parseProgram(json.getJSONObject("program"))
    }

    suspend fun publish(program: WorkoutProgram, videoId: String?, origin: String): String {
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
        return request("POST", "/api/programs", body).getString("id")
    }

    suspend fun rate(id: String, stars: Int): LibraryEntry {
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
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
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

    private fun parseEntry(json: JSONObject) = LibraryEntry(
        id = json.getString("id"),
        name = json.getString("name"),
        videoId = if (json.isNull("videoId")) null else json.optString("videoId").ifEmpty { null },
        steps = json.getInt("steps"),
        durationSeconds = json.getInt("durationSeconds"),
        rating = json.getDouble("rating"),
        ratings = json.getInt("ratings"),
        origin = json.optString("origin", "manual"),
    )

    private fun parseProgram(json: JSONObject) = WorkoutProgram(
        name = json.getString("name"),
        steps = json.getJSONArray("steps").objects().map {
            WorkoutStep(
                speedKph = it.getDouble("speedKph"),
                inclinePercent = it.getDouble("inclinePercent"),
                durationSeconds = it.getInt("durationSeconds"),
            )
        },
    )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    companion object {
        const val DEFAULT_BASE_URL = "https://ofit-backend.vercel.app"
    }
}
