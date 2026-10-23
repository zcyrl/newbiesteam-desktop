package team.newbies.desktop.shell.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import team.newbies.core.model.UpdatePost
import team.newbies.core.repository.UpdatesRepository
import team.newbies.core.router.UrlRouter
import team.newbies.desktop.shell.os.AppDirs
import team.newbies.desktop.shell.os.DesktopNotifier
import team.newbies.desktop.shell.os.DesktopOpener

/**
 * Background update polling for the desktop shells — the mirror of the Android
 * app's `NewbiesApp`/WorkManager pair (15-minute periodic check + immediate
 * check on startup/resume) plus desktop notifications when something new lands.
 */
class UpdateService(
    private val dirs: AppDirs,
    private val repository: UpdatesRepository = UpdatesRepository(
        team.newbies.platform.JvmHttpFetcher,
        userAgent = "NewbiesTeam-Desktop/3.1.1"
    ),
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL
) {
    companion object {
        const val DEFAULT_POLL_INTERVAL: Long = 15 * 60 * 1000L
    }

    @Serializable
    data class PersistedState(
        val lastSeenId: String = "",
        val lastCheckedAt: Long = 0L
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Volatile private var job: Job? = null
    @Volatile private var lastSeenIdValue: String = persisted().lastSeenId

    fun start(scope: CoroutineScope, onNewPost: (UpdatePost) -> Unit = ::announcePost) {
        if (job?.isActive == true) return
        job = scope.launch {
            // Immediate check on startup, like NewbiesApp's one-time WorkManager request.
            checkOnce(onNewPost)
            while (isActive) {
                delay(pollIntervalMillis)
                checkOnce(onNewPost)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** One-shot check; also invoked from the tray "Check for Updates" action. */
    suspend fun checkOnce(onNewPost: (UpdatePost) -> Unit = ::announcePost): UpdatePost? {
        return withContext(Dispatchers.IO) {
            val latest = try {
                repository.fetchLatestUpdate()
            } catch (_: Exception) {
                null
            }
            val newId = latest?.id?.ifBlank { latest.title } ?: ""

            when {
                latest == null -> null
                // First-ever run: seed the baseline silently (fresh install parity).
                lastSeenIdValue.isBlank() -> {
                    persistSeen(newId)
                    null
                }
                newId != lastSeenIdValue -> {
                    persistSeen(newId)
                    onNewPost(latest)
                    latest
                }
                else -> {
                    persistSeen(newId)
                    null
                }
            }
        }
    }

    fun openUpdatesSection() {
        DesktopOpener.openUrl(UrlRouter.UPDATES_SECTION_URL)
    }

    private fun announcePost(post: UpdatePost) {
        DesktopNotifier.notify(
            title = post.notificationTitle,
            body = post.notificationBody.ifBlank { "New update on the NewbiesTeam hub" }
        )
    }

    private fun persisted(): PersistedState = try {
        val file = dirs.updateStateFile
        if (file.isFile) json.decodeFromString<PersistedState>(file.readText()) else PersistedState()
    } catch (_: Exception) {
        PersistedState()
    }

    private fun persistSeen(id: String) {
        try {
            lastSeenIdValue = id
            dirs.updateStateFile.parentFile?.mkdirs()
            dirs.updateStateFile.writeText(
                json.encodeToString(PersistedState(lastSeenId = id, lastCheckedAt = System.currentTimeMillis()))
            )
        } catch (_: Exception) {
        }
    }
}
