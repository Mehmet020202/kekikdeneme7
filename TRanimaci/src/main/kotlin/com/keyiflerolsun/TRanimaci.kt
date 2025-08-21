// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.*
import org.jsoup.Jsoup

class TRanimaci : MainAPI() {
    override var mainUrl              = "https://tranimaci.com"
    override var name                 = "TrAnimeci"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Anime)

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/library/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 500L  // ? 0.5 saniye
    override var sequentialMainPageScrollDelay = 500L  // ? 0.5 saniye

    override val mainPage = mainPageOf(
        "${mainUrl}/son-eklenen" to "Son Eklenen",
        "${mainUrl}/son-bolumler" to "Son Bölümler",
        "${mainUrl}/category/action"                                   to "Aksiyon",
        "${mainUrl}/category/cars"                                     to "Arabalar",
        "${mainUrl}/category/supernatural"                             to "Doğaüstü",
        "${mainUrl}/category/drama"                                    to "Dram",
        "${mainUrl}/category/ecchi"                                    to "Ecchi",
        "${mainUrl}/category/fantasy"                                  to "Fantastik",
        "${mainUrl}/category/mystery"                                  to "Gizem",
        "${mainUrl}/category/comedy"                                   to "Komedi",
        "${mainUrl}/category/horror"                                   to "Korku",
        "${mainUrl}/category/adventure"                                to "Macera",
        "${mainUrl}/category/mecha"                                    to "Mecha",
        "${mainUrl}/category/music"                                    to "Müzik",
        "${mainUrl}/category/romance"                                  to "Romantik",
        "${mainUrl}/category/sports"                                   to "Spor"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = document.select("article.bs div.bsx").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.limit img")?.attr("src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/search?name=${query}").document

        return document.select("article.bs div.bsx").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.thumb img")?.attr("src"))
        val description = document.selectFirst("div.anime-description")?.text()?.trim()
        val tags        = document.select("div#genxed a[href*='/category']").map { it.text() }

        val episodeses = mutableListOf<Episode>()

        for (bolum in document.select("div.eplister ul li a")) {
            val epHref = fixUrlNull(bolum.attr("href")) ?: continue
            val epName = bolum.selectFirst(".epl-title")?.text()?.trim() ?: continue
            val epEpisode = epName.replace("Bölüm", "").trim().toIntOrNull()
	
                val newEpisode = newEpisode(epHref) {
                    this.name = epName
                    this.episode = epEpisode
                }
                episodeses.add(newEpisode)
            }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeses) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
        }
    }

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    Log.d("ANI", "data » $data")
    try {
        val document = app.get(data).document
        var foundLinks = false

        // 1. video_source içeren <script> etiketi (orijinal yöntem)
        val scriptContent = document.select("script").firstOrNull {
            it.html().contains("video_source")
        }?.html()

        if (scriptContent != null) {
            val videoSourceJson = Regex("""video_source\s*=\s*`(\[.*?])`""", RegexOption.DOT_MATCHES_ALL)
                .find(scriptContent)
                ?.groups?.get(1)
                ?.value

            if (videoSourceJson != null) {
                val videoSourceArray = JSONArray(videoSourceJson)

                // Her bir API URL'sine istek at
                for (i in 0 until videoSourceArray.length()) {
                    val source = videoSourceArray.getJSONObject(i)
                    val apiUrl = source.getString("url")
                    Log.d("ANI", "apiUrl » $apiUrl")

                    try {
                        // API sayfasını çek
                        val apiHtml = app.get(apiUrl, headers = mapOf("Referer" to "https://tranimaci.com/")).text
                        val apiDoc = Jsoup.parse(apiHtml)

                        // const sources = [...] içeren <script> bul
                        val sourcesScript = apiDoc.select("script").firstOrNull {
                            it.html().contains("const sources")
                        } ?: continue

                        val sourcesArrayRaw = Regex("""const\s+sources\s*=\s*(\[[\s\S]*?])\s*;""")
                            .find(sourcesScript.html())
                            ?.groups?.get(1)
                            ?.value
                            ?: continue

                        // MP4 linklerini JSON olarak parse et
                        val mp4Array = JSONArray(sourcesArrayRaw)
                        Log.d("ANI", "mp4Array » $mp4Array")

                        for (j in 0 until mp4Array.length()) {
                            val mp4 = mp4Array.getJSONObject(j)
                            val videoUrl = "https://api.animeuzayi.com" + mp4.getString("src")
                            val quality = mp4.optInt("size", Qualities.Unknown.value)

                            callback.invoke(
                                newExtractorLink(
                                    source = this.name,
                                    name = "${this.name} - ${quality}p",
                                    url = videoUrl,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.referer = "https://api.animeuzayi.com/"
                                    this.quality = quality
                                }
                            )
                            foundLinks = true
                        }
                    } catch (e: Exception) {
                        Log.d("ANI", "Error processing API URL $apiUrl: ${e.message}")
                    }
                }
            }
        }

        // 2. Video player iframe'leri
        if (!foundLinks) {
            document.select("div.video-player iframe, div.player iframe, iframe[src*='player'], iframe").forEach { iframe ->
                val iframeSrc = fixUrlNull(iframe.attr("src")) ?: fixUrlNull(iframe.attr("data-src"))
                if (iframeSrc != null) {
                    Log.d("ANI", "Found iframe: $iframeSrc")
                    loadExtractor(iframeSrc, "${mainUrl}/", subtitleCallback, callback)
                    foundLinks = true
                }
            }
        }

        // 3. Video element'leri
        if (!foundLinks) {
            document.select("video source").forEach { source ->
                val videoSrc = fixUrlNull(source.attr("src"))
                if (videoSrc != null) {
                    Log.d("ANI", "Found video source: $videoSrc")
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
            document.select("script").filter { it.data().contains("\"file\"") || it.data().contains("\"source\"") || it.data().contains("\"url\"") }.forEach { script ->
                try {
                    val jsonData = script.data()
                    val fileMatch = Regex("""["']file["']\s*:\s*["']([^"']+)["']""").find(jsonData)
                    val sourceMatch = Regex("""["']source["']\s*:\s*["']([^"']+)["']""").find(jsonData)
                    val urlMatch = Regex("""["']url["']\s*:\s*["']([^"']+)["']""").find(jsonData)
                    
                    val videoUrl = fileMatch?.groupValues?.get(1) ?: sourceMatch?.groupValues?.get(1) ?: urlMatch?.groupValues?.get(1)
                    if (videoUrl != null && videoUrl.isNotEmpty()) {
                        Log.d("ANI", "Found JSON video URL: $videoUrl")
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
                    Log.d("ANI", "Error parsing JSON script: ${e.message}")
                }
            }
        }

        // 5. Tüm script'leri tara
        if (!foundLinks) {
            document.select("script").forEach { script ->
                try {
                    val scriptData = script.data()
                    // Video URL pattern'lerini ara
                    val patterns = listOf(
                        Regex("""["']video["']\s*:\s*["']([^"']+)["']"""),
                        Regex("""["']file["']\s*:\s*["']([^"']+)["']"""),
                        Regex("""["']source["']\s*:\s*["']([^"']+)["']"""),
                        Regex("""["']url["']\s*:\s*["']([^"']+)["']"""),
                        Regex("""src\s*=\s*["']([^"']*\.m3u8[^"']*)["']"""),
                        Regex("""src\s*=\s*["']([^"']*\.mp4[^"']*)["']""")
                    )
                    
                    for (pattern in patterns) {
                        val match = pattern.find(scriptData)
                        if (match != null) {
                            val videoUrl = match.groupValues[1]
                            if (videoUrl.isNotEmpty() && (videoUrl.contains("http") || videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))) {
                                Log.d("ANI", "Found video URL in script: $videoUrl")
                                callback.invoke(
                                    newExtractorLink(
                                        source = "Script Video",
                                        name = "Script Video",
                                        url = videoUrl,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        headers = mapOf("Referer" to "${mainUrl}/")
                                        quality = Qualities.Unknown.value
                                    }
                                )
                                foundLinks = true
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d("ANI", "Error parsing script: ${e.message}")
                }
            }
        }

        return foundLinks
    } catch (e: Exception) {
        Log.d("ANI", "Error in loadLinks: ${e.message}")
        return false
    }
}
}
