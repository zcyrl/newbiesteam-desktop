package team.newbies.desktop.shell.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.UUID

/**
 * Zero-dependency loopback HTTP server (JDK `com.sun.net.httpserver`) that
 * serves the offline **NewbiesDev** portal UI bundled as JVM resources — this is
 * the desktop equivalent of the APK's `file:///android_asset/index.html` load,
 * but over HTTP so relative paths, localStorage and fetch() to the relay work
 * exactly like on the phone.
 *
 * Also exposes `/__files/<uuid>-<name>` uploads: the native file picker copies
 * selected files here so the web app can `fetch()` them same-origin.
 */
class DevUiServer(
    private val bundledRoot: String = "newbies-dev-ui",
    private val pickedFilesDir: File
) {
    @Volatile private var server: HttpServer? = null

    /** Actual base URL after bind, e.g. `http://127.0.0.1:4721/index.html`. */
    lateinit var baseUrl: String
        private set

    val port: Int
        get() = server?.address?.port ?: 0

    fun start(preferredPort: Int = 4721, attemptPorts: Int = 20): Boolean {
        if (server != null) return true
        loop@ for (port in preferredPort until preferredPort + attemptPorts) {
            try {
                val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", port), 64)
                val self = this
                httpServer.createContext("/") { exchange -> self.handle(exchange) }
                httpServer.createContext("/__files") { exchange -> self.handlePicked(exchange) }
                httpServer.executor = java.util.concurrent.Executors.newFixedThreadPool(4)
                httpServer.start()
                server = httpServer
                baseUrl = "http://127.0.0.1:$port/index.html"
                return true
            } catch (_: java.net.BindException) {
                continue
            } catch (_: Exception) {
                return false
            }
        }
        return false
    }

    fun stop() {
        try {
            server?.stop(0)
        } catch (_: Exception) {
        }
        server = null
        try {
            pickedFilesDir.listFiles()?.let { staleFiles ->
                staleFiles.forEach { it.delete() }
            }
        } catch (_: Exception) {
        }
    }

    /** Copies a native-picked file into the server's cache and returns its resource URL. */
    fun stagePickedFile(name: String, bytes: ByteArray): String? {
        val safe = (name.substringAfterLast(File.separatorChar)).substringAfterLast('/')
        val safeName = (safe.ifBlank { "picked.file" }).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val key = "p-${UUID.randomUUID().toString().substring(0, 8)}-$safeName"
        return try {
            pickedFilesDir.mkdirs()
            val target = File(pickedFilesDir, key)
            target.writeBytes(bytes)
            "http://127.0.0.1:$port/__files/$key"
        } catch (_: Exception) {
            null
        }
    }

    private fun handle(exchange: HttpExchange) {
        try {
            var path = exchange.requestURI.path
            if (path == "/" || path.isBlank()) path = "/index.html"

            val resourcePath = if (bundledRoot.endsWith("/")) "$bundledRoot$path" else "$bundledRoot/$path"
            val resource = resourcePath.removePrefix("/")
            val bytes = readResource(resource)

            if (bytes == null) {
                send(exchange, 404, "Not found: $resource".toByteArray(Charsets.UTF_8), "text/plain; charset=utf-8")
            } else {
                send(exchange, 200, bytes, contentTypeOf(resource))
            }
        } catch (_: Exception) {
            try { send(exchange, 500, "server error".toByteArray(), "text/plain") } catch (_: Exception) {}
        } finally {
            exchange.close()
        }
    }

    private fun handlePicked(exchange: HttpExchange) {
        try {
            val requested = exchange.requestURI.path.substringAfterLast("/")
            val file = File(pickedFilesDir, requested.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            if (!file.isFile) {
                send(exchange, 404, "gone".toByteArray(), "text/plain")
            } else {
                send(exchange, 200, file.readBytes(), contentTypeOf(file.absolutePath))
            }
        } catch (_: Exception) {
            try { send(exchange, 500, "server error".toByteArray(), "text/plain") } catch (_: Exception) {}
        } finally {
            exchange.close()
        }
    }

    private fun readResource(resourcePath: String): ByteArray? {
        val stream = this::class.java.classLoader.getResourceAsStream(resourcePath) ?: return null
        val buffer = ByteArrayOutputStream()
        stream.use { input -> input.copyTo(buffer) }
        return buffer.toByteArray()
    }

    private fun send(exchange: HttpExchange, status: Int, bytes: ByteArray, contentType: String) {
        val headers = exchange.responseHeaders
        headers.add("Content-Type", contentType)
        headers.add("Cache-Control", "no-store, must-revalidate")
        headers.add("Referrer-Policy", "same-origin")
        exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
    }

    private fun contentTypeOf(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html; charset=utf-8"
            "js", "mjs" -> "application/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "ico" -> "image/x-icon"
            else -> "application/octet-stream"
        }
    }
}
