package com.example.superstore.data

/**
 * Lojas/repositórios suportados pela busca.
 * Cada uma tem um adapter correspondente em network/StoreSearchClients.kt
 */
enum class Store(val displayName: String) {
    GITHUB("GitHub"),
    GITLAB("GitLab"),
    CODEBERG("Codeberg"),
    APTOIDE("Aptoide"),
    FDROID("F-Droid"),
    IZZYONDROID("IzzyOnDroid"),
    ACCRESCENT("Accrescent"),
    OPENAPK("OpenAPK")
}

/**
 * Se a loja só hospeda apps Android (útil pra aplicar o filtro de plataforma
 * sem precisar consultar nada na rede) ou se é um repositório de código onde
 * a plataforma só é conhecida olhando os arquivos da release.
 */
val Store.isAndroidOnly: Boolean
    get() = this in setOf(Store.APTOIDE, Store.FDROID, Store.IZZYONDROID, Store.ACCRESCENT, Store.OPENAPK)

/**
 * Lojas sem API de busca própria: em vez de buscar, o app abre o site dentro
 * de uma WebView embutida (não sai pro navegador) e o usuário pesquisa por lá.
 * Accrescent não tem catálogo navegável na web (só dentro do app deles) — abrir
 * mesmo assim, mas o resultado pode ser limitado.
 */
val Store.webBrowseUrl: String?
    get() = when (this) {
        Store.OPENAPK -> "https://www.openapk.net/"
        Store.ACCRESCENT -> "https://accrescent.app/"
        else -> null
    }

/** Plataforma de um arquivo/asset específico para download. */
enum class Platform(val label: String) {
    ANDROID("Android"),
    WINDOWS("Windows"),
    MAC("Mac"),
    LINUX("Linux"),
    IOS("iOS"),
    UNKNOWN("Desconhecida")
}

/** Opção do filtro de plataforma exibido na tela (inclui "Todas"). */
enum class PlatformFilter(val label: String, val platform: Platform?) {
    ALL("Todas", null),
    ANDROID("Android", Platform.ANDROID),
    WINDOWS("Windows", Platform.WINDOWS),
    MAC("macOS", Platform.MAC),
    LINUX("Linux", Platform.LINUX),
    IOS("iOS", Platform.IOS)
}

/**
 * Resultado padronizado de busca, independente da loja de origem.
 *
 * @param apiRepoPath "owner/repo" — usado só para GitHub/GitLab/Codeberg buscarem
 *        os arquivos da última release quando o usuário abre o resultado.
 * @param directDownload quando a própria busca já sabe o link do APK
 *        (F-Droid, IzzyOnDroid, Aptoide) — nesses casos não precisa de release lookup.
 */
data class AppResult(
    val name: String,
    val description: String,
    val author: String,
    val iconUrl: String?,
    val pageUrl: String,
    val store: Store,
    val apiRepoPath: String? = null,
    val directDownload: DownloadAsset? = null
)

/** Um arquivo baixável específico: nome, url e a plataforma detectada pela extensão. */
data class DownloadAsset(
    val fileName: String,
    val downloadUrl: String,
    val platform: Platform
)

/** Deduz a plataforma de um arquivo pela extensão do nome. */
fun classifyPlatform(fileName: String): Platform {
    val lower = fileName.lowercase()
    return when {
        lower.endsWith(".apk") -> Platform.ANDROID
        lower.endsWith(".exe") || lower.endsWith(".msi") -> Platform.WINDOWS
        lower.endsWith(".dmg") || lower.endsWith(".pkg") -> Platform.MAC
        lower.endsWith(".appimage") || lower.endsWith(".deb") || lower.endsWith(".rpm") ||
            lower.endsWith(".tar.gz") || lower.endsWith(".tar.xz") || lower.endsWith(".snap") -> Platform.LINUX
        lower.endsWith(".ipa") -> Platform.IOS
        else -> Platform.UNKNOWN
    }
}
