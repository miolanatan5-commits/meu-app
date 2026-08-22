package com.example.superstore.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.superstore.data.DownloadAsset
import com.example.superstore.data.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object AppActions {

    /** Abre a página do repositório/app no navegador padrão do aparelho. */
    fun openInBrowser(context: Context, url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Baixa um .apk e abre o instalador do Android. Requer que o usuário tenha
     * permitido "instalar apps desconhecidos" para o Super Store — se não tiver,
     * o Android mostra essa tela de permissão automaticamente.
     */
    suspend fun downloadAndInstallApk(context: Context, asset: DownloadAsset): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
                val file = File(dir, asset.fileName)
                downloadToFile(asset.downloadUrl, file)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Baixa um arquivo que não é instalável no Android (.exe, .dmg, .deb, .ipa...)
     * usando o gerenciador de downloads do sistema, pra aparecer na pasta Downloads
     * e na barra de notificações — igual baixar qualquer arquivo pelo navegador.
     */
    fun downloadWithSystemManager(context: Context, asset: DownloadAsset) {
        val request = android.app.DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle(asset.fileName)
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, asset.fileName)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        manager.enqueue(request)
    }

    private fun downloadToFile(url: String, destination: File) {
        val request = Request.Builder().url(url).header("User-Agent", "SuperStore-App").build()
        okHttpClientForDownload.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} ao baixar $url")
            }
            val body = response.body ?: throw IllegalStateException("Resposta vazia ao baixar $url")
            FileOutputStream(destination).use { output ->
                body.byteStream().copyTo(output)
            }
        }
    }
}

private val okHttpClientForDownload = okhttp3.OkHttpClient.Builder()
    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
    .build()

/** Mensagem amigável explicando o que o botão faz, pra usar na UI. */
fun actionLabelFor(platform: Platform): String = when (platform) {
    Platform.ANDROID -> "Instalar"
    Platform.IOS -> "Baixar (iOS exige App Store/TestFlight para instalar)"
    else -> "Baixar"
}
