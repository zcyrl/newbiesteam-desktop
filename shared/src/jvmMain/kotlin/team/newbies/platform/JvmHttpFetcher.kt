package team.newbies.platform

import team.newbies.core.platform.HttpFetcher
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * JVM actual for the shared [HttpFetcher] (used on Windows / macOS / Linux desktop).
 */
object JvmHttpFetcher : HttpFetcher {

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    override suspend fun get(url: String, headers: Map<String, String>): HttpFetcher.HttpResult {
        return try {
            val builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(8000))
                .GET()
            headers.forEach { (k, v) -> builder.header(k, v) }

            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                HttpFetcher.HttpResult.Success(response.body())
            } else {
                HttpFetcher.HttpResult.Failure("HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            HttpFetcher.HttpResult.Failure(e.message)
        }
    }
}
