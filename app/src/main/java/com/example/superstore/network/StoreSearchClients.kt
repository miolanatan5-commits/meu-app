package com.example.superstore.network

import com.example.superstore.data.AppResult
import com.example.superstore.data.DownloadAsset
import com.example.superstore.data.Store
import com.example.superstore.data.classifyPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

private fun get(url: String): String {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "SuperStore-App")
        .build()
    httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code} ao consultar $url")
        }
        return response.body?.string() ?: throw IllegalStateException("Resposta vazia de $url")
    }
}

private fun enc(query: String): String = URLEncoder.encode(query, "UTF-8")

private object FdroidIndexCache {
    var fdroidJson: JSONObject? = null
    var izzyJson: JSONObject? = null
}

object StoreSearch {

    suspend fun search(store: Store, query: String): List<AppResult> = withContext(Dispatchers.IO) {
        when (store) {
            Store.GITHUB -> searchGitHub(query)
            Store.GITLAB -> searchGitLab(query)
            Store.CODEBERG -> searchCodeberg(query)
            Store.APTOIDE -> searchAptoide(query)
            Store.FDROID -> searchFdroidIndex(query, izzy = false)
            Store.IZZYONDROID -> searchFdroidIndex(query, izzy = true)
            Store.ACCRESCENT -> searchAccrescent(query)
            Store.OPENAPK -> searchOpenApk(query)
        }
    }

    /**
     * Busca os arquivos (assets) da release mais recente de um repositório
     * GitHub/GitLab/Codeberg — é aqui que a gente descobre o .apk/.exe/.dmg/etc
     * pra oferecer "Instalar" ou "Baixar". Só é chamada quando o usuário abre
     * um resultado específico, pra não estourar limite de requisições da API.
     */
    suspend fun fetchReleaseAssets(store: Store, repoPath: String): List<DownloadAsset> =
        withContext(Dispatchers.IO) {
            when (store) {
                Store.GITHUB -> fetchGitHubReleaseAssets(repoPath)
                Store.GITLAB -> fetchGitLabReleaseAssets(repoPath)
                Store.CODEBERG -> fetchCodebergReleaseAssets(repoPath)
                else -> emptyList()
            }
        }

    // --- GitHub -------------------------------------------------------
    private fun searchGitHub(query: String): List<AppResult> {
        val url = "https://api.github.com/search/repositories?q=${enc(query)}&sort=stars&order=desc&per_page=25"
        val json = JSONObject(get(url))
        val items = json.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            val owner = item.optJSONObject("owner")
            AppResult(
                name = item.optString("name"),
                description = item.optString("description", ""),
                author = owner?.optString("login") ?: "",
                iconUrl = owner?.optString("avatar_url"),
                pageUrl = item.optString("html_url"),
                store = Store.GITHUB,
                apiRepoPath = item.optString("full_name", null)
            )
        }
    }

    private fun fetchGitHubReleaseAssets(repoPath: String): List<DownloadAsset> {
        val url = "https://api.github.com/repos/$repoPath/releases/latest"
        val json = JSONObject(get(url))
        val assets = json.optJSONArray("assets") ?: return emptyList()
        return (0 until assets.length()).map { i ->
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            DownloadAsset(
                fileName = name,
                downloadUrl = asset.optString("browser_download_url"),
                platform = classifyPlatform(name)
            )
        }
    }

    // --- GitLab ---------------------------------------------------------
    private fun searchGitLab(query: String): List<AppResult> {
        val url = "https://gitlab.com/api/v4/projects?search=${enc(query)}&order_by=star_count&sort=desc&per_page=25"
        val body = get(url)
        val items = org.json.JSONArray(body)
        return (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            AppResult(
                name = item.optString("name"),
                description = item.optString("description", ""),
                author = item.optJSONObject("namespace")?.optString("name") ?: "",
                iconUrl = item.optString("avatar_url", "").ifBlank { null },
                pageUrl = item.optString("web_url"),
                store = Store.GITLAB,
                apiRepoPath = item.optString("path_with_namespace", null)
            )
        }
    }

    private fun fetchGitLabReleaseAssets(repoPath: String): List<DownloadAsset> {
        val encodedPath = enc(repoPath)
        val url = "https://gitlab.com/api/v4/projects/$encodedPath/releases"
        val body = get(url)
        val releases = org.json.JSONArray(body)
        if (releases.length() == 0) return emptyList()
        val latest = releases.getJSONObject(0)
        val links = latest.optJSONObject("assets")?.optJSONArray("links") ?: return emptyList()
        return (0 until links.length()).map { i ->
            val link = links.getJSONObject(i)
            val name = link.optString("name")
            DownloadAsset(
                fileName = name,
                downloadUrl = link.optString("direct_asset_url", link.optString("url")),
                platform = classifyPlatform(name)
            )
        }
    }

    // --- Codeberg (Gitea API) -------------------------------------------
    private fun searchCodeberg(query: String): List<AppResult> {
        val url = "https://codeberg.org/api/v1/repos/search?q=${enc(query)}&limit=25"
        val json = JSONObject(get(url))
        val items = json.optJSONArray("data") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            val owner = item.optJSONObject("owner")
            AppResult(
                name = item.optString("name"),
                description = item.optString("description", ""),
                author = owner?.optString("login") ?: "",
                iconUrl = owner?.optString("avatar_url"),
                pageUrl = item.optString("html_url"),
                store = Store.CODEBERG,
                apiRepoPath = item.optString("full_name", null)
            )
        }
    }

    private fun fetchCodebergReleaseAssets(repoPath: String): List<DownloadAsset> {
        val url = "https://codeberg.org/api/v1/repos/$repoPath/releases/latest"
        val json = JSONObject(get(url))
        val assets = json.optJSONArray("assets") ?: return emptyList()
        return (0 until assets.length()).map { i ->
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            DownloadAsset(
                fileName = name,
                downloadUrl = asset.optString("browser_download_url"),
                platform = classifyPlatform(name)
            )
        }
    }

    // --- Aptoide ----------------------------------------------------------
    // API não-oficial/observada publicamente (ws75.aptoide.com). Pode mudar sem aviso.
    private fun searchAptoide(query: String): List<AppResult> {
        val url = "https://ws75.aptoide.com/api/7/apps/search/query=${enc(query)}/limit=25"
        val json = JSONObject(get(url))
        val list = json.optJSONObject("datalist")?.optJSONArray("list") ?: return emptyList()
        return (0 until list.length()).map { i ->
            val item = list.getJSONObject(i)
            val apkUrl = item.optJSONObject("file")?.optString("path", "")?.ifBlank { null }
            AppResult(
                name = item.optString("name"),
                description = item.optJSONObject("file")?.optString("path") ?: "",
                author = item.optJSONObject("developer")?.optString("name") ?: "",
                iconUrl = item.optString("icon", "").ifBlank { null },
                pageUrl = item.optJSONObject("urls")?.optString("w") ?: "",
                store = Store.APTOIDE,
                directDownload = apkUrl?.let {
                    DownloadAsset(fileName = "${item.optString("name")}.apk", downloadUrl = it, platform = com.example.superstore.data.Platform.ANDROID)
                }
            )
        }
    }

    // --- F-Droid / IzzyOnDroid --------------------------------------------
    private fun searchFdroidIndex(query: String, izzy: Boolean): List<AppResult> {
        val cached = if (izzy) FdroidIndexCache.izzyJson else FdroidIndexCache.fdroidJson
        val json = cached ?: run {
            val url = if (izzy) {
                "https://apt.izzysoft.de/fdroid/repo/index-v1.json"
            } else {
                "https://f-droid.org/repo/index-v1.json"
            }
            val loaded = JSONObject(get(url))
            if (izzy) FdroidIndexCache.izzyJson = loaded else FdroidIndexCache.fdroidJson = loaded
            loaded
        }

        val apps = json.optJSONArray("apps") ?: return emptyList()
        val repoAddress = json.optJSONObject("repo")?.optString("address")
            ?: (if (izzy) "https://apt.izzysoft.de/fdroid/repo" else "https://f-droid.org/repo")
        val queryLower = query.lowercase()

        val results = mutableListOf<AppResult>()
        for (i in 0 until apps.length()) {
            val app = apps.getJSONObject(i)
            val name = app.optString("name")
            val summary = app.optString("summary", "")
            if (name.lowercase().contains(queryLower) || summary.lowercase().contains(queryLower)) {
                val packageName = app.optString("packageName")
                val versionCode = app.optLong("suggestedVersionCode", -1L)
                val apkFileName = if (versionCode > 0) "${packageName}_$versionCode.apk" else null
                results.add(
                    AppResult(
                        name = name,
                        description = summary,
                        author = app.optString("authorName", ""),
                        iconUrl = null,
                        pageUrl = "$repoAddress/$packageName",
                        store = if (izzy) Store.IZZYONDROID else Store.FDROID,
                        directDownload = apkFileName?.let {
                            DownloadAsset(
                                fileName = it,
                                downloadUrl = "$repoAddress/$it",
                                platform = com.example.superstore.data.Platform.ANDROID
                            )
                        }
                    )
                )
            }
            if (results.size >= 25) break
        }
        return results
    }

    // --- Accrescent / OpenAPK ------------------------------------------------
    // Sem API pública de busca confirmada — ver observação no chat.
    private fun searchAccrescent(query: String): List<AppResult> {
        throw UnsupportedOperationException(
            "Busca na Accrescent por \"$query\" ainda não está implementada — não há API pública de busca confirmada."
        )
    }

    private fun searchOpenApk(query: String): List<AppResult> {
        throw UnsupportedOperationException(
            "Busca na OpenAPK por \"$query\" ainda não está implementada — o site não tem API pública conhecida."
        )
    }
}
