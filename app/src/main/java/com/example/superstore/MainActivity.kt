package com.example.superstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.superstore.data.AppResult
import com.example.superstore.data.DownloadAsset
import com.example.superstore.data.Platform
import com.example.superstore.data.PlatformFilter
import com.example.superstore.data.Store
import com.example.superstore.data.isAndroidOnly
import com.example.superstore.data.webBrowseUrl
import com.example.superstore.network.AppActions
import com.example.superstore.network.StoreSearch
import com.example.superstore.network.actionLabelFor
import com.example.superstore.ui.theme.SuperStoreTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SuperStoreTheme {
                SuperStoreScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperStoreScreen() {
    val context = LocalContext.current
    var selectedStore by remember { mutableStateOf(Store.GITHUB) }
    var storeMenuExpanded by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf(PlatformFilter.ALL) }
    var platformMenuExpanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AppResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedApp by remember { mutableStateOf<AppResult?>(null) }
    var showInAppBrowser by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        if (query.isBlank()) return
        errorMessage = null
        isLoading = true
        scope.launch {
            try {
                val allResults = StoreSearch.search(selectedStore, query.trim())
                // Lojas só-Android: já dá pra aplicar o filtro de plataforma na hora.
                results = if (selectedStore.isAndroidOnly && selectedPlatform != PlatformFilter.ALL) {
                    if (selectedPlatform == PlatformFilter.ANDROID) allResults else emptyList()
                } else {
                    allResults
                }
                if (results.isEmpty()) {
                    errorMessage = "Nenhum resultado encontrado em ${selectedStore.displayName}" +
                        if (selectedPlatform != PlatformFilter.ALL) " para ${selectedPlatform.label}." else "."
                }
            } catch (e: Exception) {
                results = emptyList()
                errorMessage = e.message ?: "Erro ao buscar em ${selectedStore.displayName}."
            } finally {
                isLoading = false
            }
        }
    }

    val browseUrl = selectedStore.webBrowseUrl

    if (showInAppBrowser && browseUrl != null) {
        InAppBrowserScreen(
            url = browseUrl,
            title = selectedStore.displayName,
            onBack = { showInAppBrowser = false }
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Super Store") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Button(onClick = { storeMenuExpanded = true }) {
                        Text(selectedStore.displayName)
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = storeMenuExpanded,
                        onDismissRequest = { storeMenuExpanded = false }
                    ) {
                        Store.entries.forEach { store ->
                            DropdownMenuItem(
                                text = { Text(store.displayName) },
                                onClick = {
                                    selectedStore = store
                                    storeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.size(8.dp))

                Box {
                    OutlinedButton(onClick = { platformMenuExpanded = true }) {
                        Text(selectedPlatform.label)
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = platformMenuExpanded,
                        onDismissRequest = { platformMenuExpanded = false }
                    ) {
                        PlatformFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.label) },
                                onClick = {
                                    selectedPlatform = filter
                                    platformMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (!selectedStore.isAndroidOnly && selectedPlatform != PlatformFilter.ALL) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Para ${selectedStore.displayName}, a plataforma só é confirmada ao abrir cada resultado " +
                        "(verifico os arquivos da última release).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (browseUrl != null) {
                    Text(
                        "${selectedStore.displayName} não tem busca por API — abra o site aqui dentro e pesquise por lá.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = { showInAppBrowser = true }) {
                        Text("Abrir site")
                    }
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Nome do app em ${selectedStore.displayName}") },
                        singleLine = true
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = { runSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (browseUrl == null) {
                when {
                    isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    errorMessage != null -> {
                        Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(results) { app ->
                                AppResultCard(app, onClick = { selectedApp = app })
                            }
                        }
                    }
                }
            }
        }
    }

    selectedApp?.let { app ->
        AppDetailDialog(
            app = app,
            platformFilter = selectedPlatform,
            onDismiss = { selectedApp = null }
        )
    }
}

@Composable
fun AppResultCard(app: AppResult, onClick: () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.iconUrl != null) {
                AsyncImage(model = app.iconUrl, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.size(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.name, style = MaterialTheme.typography.titleMedium)
                if (app.author.isNotBlank()) {
                    Text(text = app.author, style = MaterialTheme.typography.bodySmall)
                }
                if (app.description.isNotBlank()) {
                    Text(text = app.description, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = app.store.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Diálogo de detalhes: mostra os arquivos disponíveis (respeitando o filtro de
 * plataforma escolhido), com botão de Instalar/Baixar pra cada um, e sempre
 * um botão pra abrir o repositório/página do app no navegador.
 */
@Composable
fun AppDetailDialog(app: AppResult, platformFilter: PlatformFilter, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var assets by remember(app) { mutableStateOf<List<DownloadAsset>>(emptyList()) }
    var isLoadingAssets by remember(app) { mutableStateOf(true) }
    var assetsError by remember(app) { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    // Carrega os arquivos disponíveis: direto (F-Droid/IzzyOnDroid/Aptoide)
    // ou consultando a última release (GitHub/GitLab/Codeberg).
    androidx.compose.runtime.LaunchedEffect(app) {
        isLoadingAssets = true
        assetsError = null
        try {
            assets = when {
                app.directDownload != null -> listOf(app.directDownload)
                app.apiRepoPath != null -> StoreSearch.fetchReleaseAssets(app.store, app.apiRepoPath)
                else -> emptyList()
            }
            if (assets.isEmpty()) {
                assetsError = "Nenhum arquivo de instalação encontrado na última release."
            }
        } catch (e: Exception) {
            assetsError = e.message ?: "Erro ao buscar arquivos disponíveis."
        } finally {
            isLoadingAssets = false
        }
    }

    val filteredAssets = if (platformFilter == PlatformFilter.ALL) {
        assets
    } else {
        assets.filter { it.platform == platformFilter.platform }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.name) },
        text = {
            Column {
                if (app.description.isNotBlank()) {
                    Text(app.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                when {
                    isLoadingAssets -> CircularProgressIndicator()
                    filteredAssets.isEmpty() -> Text(
                        assetsError ?: "Nenhum arquivo para a plataforma selecionada.",
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> {
                        filteredAssets.forEach { asset ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(asset.fileName, style = MaterialTheme.typography.bodyMedium)
                                    Text(asset.platform.label, style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = {
                                    scope.launch {
                                        if (asset.platform == Platform.ANDROID) {
                                            val result = AppActions.downloadAndInstallApk(context, asset)
                                            actionMessage = result.fold(
                                                onSuccess = { null },
                                                onFailure = { "Erro ao instalar: ${it.message}" }
                                            )
                                        } else {
                                            AppActions.downloadWithSystemManager(context, asset)
                                            actionMessage = "Baixando \"${asset.fileName}\" — veja em Downloads."
                                        }
                                    }
                                }) {
                                    Text(actionLabelFor(asset.platform))
                                }
                            }
                        }
                    }
                }
                actionMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { AppActions.openInBrowser(context, app.pageUrl) }) {
                Text("Abrir repositório")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}
