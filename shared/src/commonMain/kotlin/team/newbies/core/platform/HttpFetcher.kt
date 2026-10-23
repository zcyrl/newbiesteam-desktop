package team.newbies.core.platform

/**
 * Minimal HTTP fetch abstraction. The JVM desktop actual uses
 * `java.net.http.HttpClient`, an Android actual (OkHttp) can be added later
 * when the APK apps adopt this shared core.
 */
interface HttpFetcher {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult

    sealed class HttpResult {
        data class Success(val body: String) : HttpResult()
        data class Failure(val message: String? = null) : HttpResult()
    }
}
