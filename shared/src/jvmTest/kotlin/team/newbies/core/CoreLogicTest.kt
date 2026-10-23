package team.newbies.core

import kotlinx.coroutines.runBlocking
import team.newbies.core.model.UpdatePost
import team.newbies.core.model.UrlRoute
import team.newbies.core.parser.FeedParser
import team.newbies.core.platform.HttpFetcher
import team.newbies.core.repository.UpdatesRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoreLogicTest {

    private class StubFetcher(private val responses: MutableMap<String, String>) : HttpFetcher {
        override suspend fun get(url: String, headers: Map<String, String>): HttpFetcher.HttpResult {
            val cleanUrl = url.split("?").first()
            val body = responses[cleanUrl] ?: return HttpFetcher.HttpResult.Failure("missing stub")
            return HttpFetcher.HttpResult.Success(body)
        }
    }

    @Test
    fun `url router classifies direct downloads`() {
        val route = router("https://newbiesteam.surge.sh/assets/roms/xos15.zip")
        assertTrue(route is UrlRoute.DirectDownload)
        assertEquals("xos15.zip", (route as? UrlRoute.DirectDownload)?.filename)
    }

    @Test
    fun `url router classifies external hosts`() {
        assertTrue(router("https://drive.google.com/file/d/abc") is UrlRoute.ExternalBrowser)
        assertTrue(router("https://t.me/newbiesteam") is UrlRoute.ExternalBrowser)
    }

    @Test
    fun `url router keeps hub internal`() {
        assertEquals(UrlRoute.InternalWeb::class.simpleName, router("https://newbiesteam.surge.sh/#roms")::class.simpleName)
        assertTrue(router("http://127.0.0.1:4721/index.html") is UrlRoute.InternalWeb)
    }

    @Test
    fun `update post repository prefers primary json feed`() = runBlocking {
        val updates = """[
            {"id":"upd-2","title":"ROM Build","tag":"ROM","by":"Softbrick","body":"test","date":"2026-10-22"},
            {"id":"upd-1","title":"Older","tag":"ROM","by":"Softbrick","date":"2026-10-21"}
        ]"""
        val repo = UpdatesRepository(
            fetcher = StubFetcher(mutableMapOf(UpdatesRepository.DEFAULT_JSON_FEED to updates)),
            mirrorJsonUrl = "https://mirror.invalid/updates.json",
            fallbackJsUrl = "https://fallback.invalid/data.js"
        )
        val post = repo.fetchLatestUpdate()
        assertNotNull(post)
        assertEquals("upd-2", post.id)
        assertEquals("Softbrick", post.author)
    }

    @Test
    fun `update post repository falls back to data js`() = runBlocking {
        val dataJs = """window.__UPDATES__ = [{ id: "js-1", title: "Boot IMG Fix", tag: "Fix", by: "MBuTT3178" }];"""
        val repo = UpdatesRepository(
            fetcher = StubFetcher(mutableMapOf(UpdatesRepository.DEFAULT_DATA_JS to dataJs)),
            mirrorJsonUrl = "https://mirror.invalid/updates.json"
        )
        val post = repo.fetchLatestUpdate()
        assertNotNull(post)
        assertEquals("js-1", post.id)
        assertEquals("Fix", post.tag)
    }

    @Test
    fun `notification copy mirrors android helper`() {
        val post = UpdatePost(id = "x", title = "", tag = "ROM", author = "Softbrick")
        assertEquals("NewbiesTeam Update", post.notificationTitle)
        assertEquals("ROM by Softbrick", post.notificationBody)
    }

    private fun router(url: String): team.newbies.core.model.UrlRoute =
        team.newbies.core.router.UrlRouter.resolveRoute(url)
}
