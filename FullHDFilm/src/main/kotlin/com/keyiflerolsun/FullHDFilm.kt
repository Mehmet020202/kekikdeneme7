// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import okhttp3.Interceptor
import okhttp3.Response
import android.util.Base64
import org.jsoup.Jsoup
import java.util.regex.Pattern

class FullHDFilm : MainAPI() {
    override var mainUrl              = "https://fullhdfilm1.us"
    override var name                 = "FullHDFilm"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)
    
    // 2025 Güncel Alternatif domain'ler
    private val alternativeDomains = listOf(
        "https://fullhdfilm1.us",
        "https://fullhdfilm.cx",
        "https://fullhdfilm.net",
        "https://fullhdfilm.com",
        "https://fullhdfilm.live",
        "https://fullhdfilm.site",
        "https://fullhdfilm.xyz",
        "https://fullhdfilm.app"
    )

    // Domain'i test et ve çalışan domain'i bul
    private suspend fun findWorkingDomain(): String {
        for (domain in alternativeDomains) {
            try {
                val response = app.get(domain, timeout = 10000)
                if (response.isSuccessful) {
                    Log.d("FHDF", "Working domain found: $domain")
                    return domain
                }
            } catch (e: Exception) {
                Log.d("FHDF", "Domain $domain failed: ${e.message}")
            }
        }
        return mainUrl // Eğer hiçbiri çalışmazsa varsayılan domain'i kullan
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/son-eklenen"                          to "Son Eklenen",
        "${mainUrl}/yeni-eklenen"                         to "Yeni Eklenen",
        "${mainUrl}/tur/turkce-altyazili-film-izle"       to "Altyazılı Filmler",
        "${mainUrl}/tur/netflix-filmleri-izle"		       to "Netflix",
        "${mainUrl}/category/aile-filmleri-izle"	       to "Aile",
        "${mainUrl}/category/aksiyon-filmleri-izle"       to "Aksiyon",
        "${mainUrl}/category/animasyon-filmleri-izle"	   to "Animasyon",
        "${mainUrl}/category/belgesel-filmleri-izle"	   to "Belgesel",
        "${mainUrl}/category/bilim-kurgu-filmleri-izle"   to "Bilim-Kurgu",
        "${mainUrl}/category/biyografi-filmleri-izle"	   to "Biyografi",
        "${mainUrl}/category/dram-filmleri-izle"		   to "Dram",
        "${mainUrl}/category/fantastik-filmler-izle"	   to "Fantastik",
        "${mainUrl}/category/gerilim-filmleri-izle"	   to "Gerilim",
        "${mainUrl}/category/gizem-filmleri-izle"		   to "Gizem",
        "${mainUrl}/category/komedi-filmleri-izle"		   to "Komedi",
        "${mainUrl}/category/korku-filmleri-izle"		   to "Korku",
        "${mainUrl}/category/macera-filmleri-izle"		   to "Macera",
        "${mainUrl}/category/romantik-filmler-izle"	   to "Romantik",
        "${mainUrl}/category/savas-filmleri-izle"		   to "Savaş",
        "${mainUrl}/category/suc-filmleri-izle"		   to "Suç",
        "${mainUrl}/tur/yerli-film-izle"			       to "Yerli Film"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Referer" to "https://fullhdfilm1.us/"
            )
        val baseUrl = request.data
        val urlpage = if (page == 1) baseUrl else "$baseUrl/page/$page"
        val document = app.get(urlpage, headers=headers).document
        val home     = document.select("div.movie-poster").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("img")?.attr("alt")?.trim() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document

        return document.select("div.movie-poster").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
    
        val title       = document.selectFirst("h1")?.text() ?: return null
    
        val poster      = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
        val description = document.select("#details > div:nth-child(2) > div")?.text()?.trim()
        val tags        = document.select("h4 a").map { it.text() }
        val rating      = document.selectFirst("div.button-custom")?.text()?.trim()?.split(" ")?.first()?.toRatingInt()
        val year        = Regex("""(\d+)""").find(document.selectFirst("div.release")?.text()?.trim() ?: "")?.groupValues?.get(1)?.toIntOrNull()
        val actors = document.selectFirst("div.oyuncular")?.ownText() ?.split(",") ?.map { Actor(it.trim()) } ?: emptyList()
    
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.rating = rating
            addActors(actors)
        }
    }

    private fun getIframe(sourceCode: String): String {
        // Base64 kodlu iframe'i içeren script bloğunu yakala
        val base64ScriptRegex = Regex("""<script[^>]*>(PCEtLWJhc2xpazp[^<]*)</script>""")
        val base64Encoded = base64ScriptRegex.find(sourceCode)?.groupValues?.get(1) ?: return ""
    
        return try {
            // Base64 decode
            val decodedHtml = String(Base64.decode(base64Encoded, Base64.DEFAULT), Charsets.UTF_8)
    
            // Jsoup ile parse edip iframe src'sini al
            val iframeSrc = Jsoup.parse(decodedHtml).selectFirst("iframe")?.attr("src")
    
            fixUrlNull(iframeSrc) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractSubtitleUrl(sourceCode: String): String? {
        // playerjsSubtitle değişkenini regex ile bul (genelleştirilmiş)
        val patterns = listOf(
            Pattern.compile("var playerjsSubtitle = \"\\[Türkçe\\](https?://[^\\s\"]+?\\.srt)\";"),
            Pattern.compile("var playerjsSubtitle = \"(https?://[^\\s\"]+?\\.srt)\";"), // Türkçe etiketi olmadan
            Pattern.compile("subtitle:\\s*\"(https?://[^\\s\"]+?\\.srt)\"") // Alternatif subtitle formatı
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(sourceCode)
            if (matcher.find()) {
                val subtitleUrl = matcher.group(1)
                Log.d("FHDF", "Found subtitle URL: $subtitleUrl")
                return subtitleUrl
            }
        }
        Log.d("FHDF", "No subtitle URL found in source code")
        return null
    }

    private suspend fun extractSubtitleFromIframe(iframeUrl: String): String? {
        if (iframeUrl.isEmpty()) return null
        try {
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
                "Referer" to mainUrl
            )
            val iframeResponse = app.get(iframeUrl, headers=headers)
            val iframeSource = iframeResponse.text
            Log.d("FHDF", "Iframe source length: ${iframeSource.length}")
            return extractSubtitleUrl(iframeSource)
        } catch (e: Exception) {
            Log.d("FHDF", "Iframe subtitle extraction error: ${e.message}")
            return null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("FHDF", "data » $data")
        try {
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
                "Referer" to mainUrl
            )
            val response = app.get(data, headers=headers)
            val sourceCode = response.text
            Log.d("FHDF", "Source code length: ${sourceCode.length}")

            var foundLinks = false

            // 1. Ana sayfadan altyazı URL'sini çek
            var subtitleUrl = extractSubtitleUrl(sourceCode)

            // 2. Iframe'den altyazı URL'sini çek
            val iframeSrc = getIframe(sourceCode)
            Log.d("FHDF", "iframeSrc: $iframeSrc")
            if (subtitleUrl == null && iframeSrc.isNotEmpty()) {
                subtitleUrl = extractSubtitleFromIframe(iframeSrc)
            }

            // 3. Altyazı bulunursa subtitleCallback ile gönder
            if (subtitleUrl != null) {
                try {
                    // Altyazı URL'sinin erişilebilirliğini kontrol et
                    val subtitleResponse = app.get(subtitleUrl, headers=headers, allowRedirects=true)
                    if (subtitleResponse.isSuccessful) {
                        subtitleCallback(SubtitleFile("Türkçe", subtitleUrl))
                        Log.d("FHDF", "Subtitle added: $subtitleUrl")
                    } else {
                        Log.d("FHDF", "Subtitle URL inaccessible: ${subtitleResponse.code}")
                    }
                } catch (e: Exception) {
                    Log.d("FHDF", "Subtitle URL error: ${e.message}")
                }
            }

            // 4. Iframe varsa loadExtractor ile işle
            if (iframeSrc.isNotEmpty()) {
                loadExtractor(iframeSrc, mainUrl, subtitleCallback, callback)
                foundLinks = true
            }

            // 5. Video player iframe'leri ara
            if (!foundLinks) {
                val document = Jsoup.parse(sourceCode)
                document.select("div.video-player iframe, div.player iframe, iframe[src*='player'], iframe").forEach { iframe ->
                    val iframeSrc = fixUrlNull(iframe.attr("src")) ?: fixUrlNull(iframe.attr("data-src"))
                    if (iframeSrc != null) {
                        Log.d("FHDF", "Found iframe: $iframeSrc")
                        loadExtractor(iframeSrc, mainUrl, subtitleCallback, callback)
                        foundLinks = true
                    }
                }
            }

            // 6. Video element'leri ara
            if (!foundLinks) {
                val document = Jsoup.parse(sourceCode)
                document.select("video source").forEach { source ->
                    val videoSrc = fixUrlNull(source.attr("src"))
                    if (videoSrc != null) {
                        Log.d("FHDF", "Found video source: $videoSrc")
                        callback.invoke(
                            newExtractorLink(
                                source = "Direct Video",
                                name = "Direct Video",
                                url = videoSrc,
                                type = ExtractorLinkType.M3U8
                            ) {
                                headers = mapOf("Referer" to mainUrl)
                                quality = Qualities.Unknown.value
                            }
                        )
                        foundLinks = true
                    }
                }
            }

            // 7. JSON data yapısı ara
            if (!foundLinks) {
                val document = Jsoup.parse(sourceCode)
                document.select("script").filter { it.data().contains("\"file\"") || it.data().contains("\"source\"") || it.data().contains("\"url\"") }.forEach { script ->
                    try {
                        val jsonData = script.data()
                        val fileMatch = Regex("""["']file["']\s*:\s*["']([^"']+)["']""").find(jsonData)
                        val sourceMatch = Regex("""["']source["']\s*:\s*["']([^"']+)["']""").find(jsonData)
                        val urlMatch = Regex("""["']url["']\s*:\s*["']([^"']+)["']""").find(jsonData)
                        
                        val videoUrl = fileMatch?.groupValues?.get(1) ?: sourceMatch?.groupValues?.get(1) ?: urlMatch?.groupValues?.get(1)
                        if (videoUrl != null && videoUrl.isNotEmpty()) {
                            Log.d("FHDF", "Found JSON video URL: $videoUrl")
                            callback.invoke(
                                newExtractorLink(
                                    source = "JSON Video",
                                    name = "JSON Video",
                                    url = videoUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    headers = mapOf("Referer" to mainUrl)
                                    quality = Qualities.Unknown.value
                                }
                            )
                            foundLinks = true
                        }
                    } catch (e: Exception) {
                        Log.d("FHDF", "Error parsing JSON script: ${e.message}")
                    }
                }
            }

            return foundLinks
        } catch (e: Exception) {
            Log.d("FHDF", "Error in loadLinks: ${e.message}")
            return false
        }
    }
}
