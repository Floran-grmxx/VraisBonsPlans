package fr.floran.vraisbonsplans.sources

import java.net.HttpURLConnection
import java.net.URL

/** Minimal blocking HTTP helper for the sources that accept plain clients (Dealabs, Ledenicheur). */
object HttpClient {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
    private const val TIMEOUT_MILLIS = 20_000

    fun get(url: String): String = request(url, method = "GET", jsonBody = null)

    fun postJson(url: String, jsonBody: String): String = request(url, method = "POST", jsonBody = jsonBody)

    private fun request(url: String, method: String, jsonBody: String?): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9")
            // Android's default Accept header ("text/html, image/gif, …; q=.2") gets a 403 from Ledenicheur.
            connection.setRequestProperty("Accept", "*/*")
            if (jsonBody != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(jsonBody.toByteArray()) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw HttpStatusException(url, status)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

class HttpStatusException(url: String, val status: Int) : Exception("HTTP $status on $url")
