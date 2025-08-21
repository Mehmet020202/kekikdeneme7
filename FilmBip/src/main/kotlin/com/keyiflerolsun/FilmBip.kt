// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.extractors.YoutubeExtractor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.jsoup.Jsoup

class FilmBip : MainAPI() {
    override var mainUrl              = "https://filmbip.com"
    override var name                 = "FilmBip"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/filmler/"                        to "Yeni Filmler",
        "${mainUrl}/film/tur/aile/"                   to "Aile Filmleri",
        "${mainUrl}/film/tur/aksiyon/"                to "Aksiyon Filmleri",
        "${mainUrl}/film/tur/belgesel/"               to "Belgesel Filmleri",
        "${mainUrl}/film/tur/bilim-kurgu/"            to "Bilim Kurgu Filmleri",
        "${mainUrl}/film/tur/dram/"                   to "Dram Filmleri",
        "${mainUrl}/film/tur/fantastik/"              to "Fantastik Filmler",
        "${mainUrl}/film/tur/gerilim/"                to "Gerilim Filmleri",
        "${mainUrl}/film/tur/gizem/"                  to "Gizem Filmleri",
        "${mainUrl}/film/tur/komedi/"                 to "Komedi Filmleri",
        "${mainUrl}/film/tur/korku/"                  to "Korku Filmleri",
        "${mainUrl}/film/tur/macera/"                 to "Macera Filmleri",
        "${mainUrl}/film/tur/muzik/"                  to "Müzik Filmleri",
        "${mainUrl}/film/tur/romantik/"               to "Romantik Filmler",
        "${mainUrl}/film/tur/savas/"                  to "Savaş Filmleri",
        "${mainUrl}/film/tur/suc/"                    to "Suç Filmleri",
        "${mainUrl}/film/tur/tarih/"                  to "Tarih Filmleri",
        "${mainUrl}/film/tur/vahsi-bati/"             to "Western Filmler",
        "${mainUrl}/film/tur/tv-film/"                to "TV Filmleri",
    )

override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val url = if (page == 1) request.data else "${request.data.removeSuffix("/")}/$page"
    val document = app.get(url).document
    val home = document.select("div.poster-long").mapNotNull { it.toSearchResult() }

    return newHomePageResponse(request.name, home)
}

private fun Element.toSearchResult(): SearchResponse? {
    val title = this.selectFirst("a.block img")?.attr("alt")?.trim() ?: return null
    val href = fixUrlNull(this.selectFirst("a.block")?.attr("href")) ?: return null
    
    val imgElement = this.selectFirst("a.block img.lazy")
    if (imgElement == null) {
        Log.d("FLB", "imgElement is null")
        return null
    }

    val posterUrl = when {
		imgElement.hasAttr("data-src") && imgElement.attr("data-src").isNotBlank() -> {
            Log.d("FLB", "Using data-src: ${imgElement.attr("data-src")}")
            fixUrlNull(imgElement.attr("data-src"))
        }
        imgElement.hasAttr("src") && imgElement.attr("src").isNotBlank() -> {
            Log.d("FLB", "Using src: ${imgElement.attr("src")}")
            fixUrlNull(imgElement.attr("src"))
        }
        else -> {
            Log.d("FLB", "No valid src or data-src found")
            null
        }
    } ?: return null

    return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
}

    override suspend fun search(query: String): List<SearchResponse> {
        val responseRaw = app.post(
            "$mainUrl/search",
            headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "Origin" to mainUrl,
                "Referer" to "$mainUrl/"
            ),
            data = mapOf("query" to query)
        )

        val json = responseRaw.parsedSafe<Map<String, Any>>()
        if (json?.get("success") != true) {
            Log.d("FLB", "Search failed: ${json?.get("success")}")
            return emptyList()
        }

        val theme = json["theme"] as? String ?: return emptyList()
        val document = Jsoup.parse(theme)
        val items = document.select("li")

        return items.mapNotNull { item ->
            val title = item.selectFirst("a.block.truncate")?.text()?.trim() ?: return@mapNotNull null
            val href = fixUrlNull(item.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val posterUrl = fixUrlNull(item.selectFirst("img.lazy")?.attr("data-src"))

            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title  = document.selectFirst("div.page-title h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content")) ?: return null
        val trailerId = document.selectFirst("div.series-profile-trailer")?.attr("data-yt")
        val trailerUrl = trailerId?.takeIf { it.isNotEmpty() }?.let { "https://www.youtube.com/watch?v=$it" }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            addTrailer(trailerUrl)
        }
    }

     override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("FLB", "data » $data")
        try {
            val document = app.get(data).document
            var foundLinks = false

            // 1. Orijinal tv-spoox2 yapısı
            document.select("div#tv-spoox2").forEach {
                val iframe = fixUrlNull(it.selectFirst("iframe")?.attr("src"))
                if (iframe != null) {
                    Log.d("FLB", "Found tv-spoox2 iframe: $iframe")
                    loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
                    foundLinks = true
                }
            }

            // 2. Alternatif video player yapıları
            if (!foundLinks) {
                document.select("div.video-player, div.player, div.embed-player, div#player").forEach { player ->
                    val iframe = fixUrlNull(player.selectFirst("iframe")?.attr("src")) ?: 
                                fixUrlNull(player.selectFirst("iframe")?.attr("data-src"))
                    if (iframe != null) {
                        Log.d("FLB", "Found player iframe: $iframe")
                        loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
                        foundLinks = true
                    }
                }
            }

            // 3. Video element'leri
            if (!foundLinks) {
                document.select("video source").forEach { source ->
                    val videoSrc = fixUrlNull(source.attr("src"))
                    if (videoSrc != null) {
                        Log.d("FLB", "Found video source: $videoSrc")
                        callback.invoke(
                            newExtractorLink(
                                source = "Direct Video",
                                name = "Direct Video",
                                url = videoSrc,
                                type = ExtractorLinkType.M3U8
                            ) {
                                headers = mapOf("Referer" to "${mainUrl}/")
                                quality = Qualities.Unknown.value
                            }
                        )
                        foundLinks = true
                    }
                }
            }

            // 4. JSON data yapısı
            if (!foundLinks) {
                document.select("script").filter { it.data().contains("\"file\"") || it.data().contains("\"source\"") }.forEach { script ->
                    try {
                        val jsonData = script.data()
                        val fileMatch = Regex("""["']file["']\s*:\s*["']([^"']+)["']""").find(jsonData)
                        val sourceMatch = Regex("""["']source["']\s*:\s*["']([^"']+)["']""").find(jsonData)
                        
                        val videoUrl = fileMatch?.groupValues?.get(1) ?: sourceMatch?.groupValues?.get(1)
                        if (videoUrl != null && videoUrl.isNotEmpty()) {
                            Log.d("FLB", "Found JSON video URL: $videoUrl")
                            callback.invoke(
                                newExtractorLink(
                                    source = "JSON Video",
                                    name = "JSON Video",
                                    url = videoUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    headers = mapOf("Referer" to "${mainUrl}/")
                                    quality = Qualities.Unknown.value
                                }
                            )
                            foundLinks = true
                        }
                    } catch (e: Exception) {
                        Log.d("FLB", "Error parsing JSON script: ${e.message}")
                    }
                }
            }

            // 5. AJAX video data
            if (!foundLinks) {
                try {
                    val ajaxResponse = app.get(
                        "${mainUrl}/ajax/video/${data.substringAfterLast("/")}", 
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                        referer = data
                    ).text
                    
                    val videoMatch = Regex("""["']video["']\s*:\s*["']([^"']+)["']""").find(ajaxResponse)
                    val videoUrl = videoMatch?.groupValues?.get(1)
                    if (videoUrl != null && videoUrl.isNotEmpty()) {
                        Log.d("FLB", "Found AJAX video URL: $videoUrl")
                        callback.invoke(
                            newExtractorLink(
                                source = "AJAX Video",
                                name = "AJAX Video",
                                url = videoUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                headers = mapOf("Referer" to "${mainUrl}/")
                                quality = Qualities.Unknown.value
                            }
                        )
                        foundLinks = true
                    }
                } catch (e: Exception) {
                    Log.d("FLB", "Error with AJAX request: ${e.message}")
                }
            }

            // 6. HDPlayerSystem extractor
            if (!foundLinks) {
                document.select("iframe").forEach { iframe ->
                    val iframeSrc = fixUrlNull(iframe.attr("src")) ?: fixUrlNull(iframe.attr("data-src"))
                    if (iframeSrc != null && iframeSrc.contains("hdplayersystem")) {
                        Log.d("FLB", "Found HDPlayerSystem iframe: $iframeSrc")
                        loadExtractor(iframeSrc, "${mainUrl}/", subtitleCallback, callback)
                        foundLinks = true
                    }
                }
            }

            return foundLinks
        } catch (e: Exception) {
            Log.d("FLB", "Error in loadLinks: ${e.message}")
            return false
        }
    }
}
