@file:OptIn(ExperimentalMaterial3Api::class)

package app.gamecollection

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

private enum class Screen { Catalog, Scan }

@Composable
fun App(vm: MainViewModel) {
    var screen by remember { mutableStateOf(Screen.Catalog) }
    BackHandler(enabled = screen == Screen.Scan) { screen = Screen.Catalog }
    when (screen) {
        Screen.Catalog -> CatalogScreen(vm, onScan = { screen = Screen.Scan })
        Screen.Scan -> ScanScreen(vm, onBack = { screen = Screen.Catalog })
    }
}

@Composable
private fun CatalogScreen(vm: MainViewModel, onScan: () -> Unit) {
    val all by vm.items.collectAsState()
    var filter by remember { mutableStateOf<String?>(null) }
    val platforms = all.map { it.platformKey to it.platformLabel }.distinct()
        .sortedBy { Platforms.order(it.first) }
    val shown = all.filter { filter == null || it.platformKey == filter }
        .sortedWith(compareBy({ Platforms.order(it.platformKey) }, { it.title }))

    Scaffold(
        topBar = { TopAppBar(title = { Text("Моя коллекция: ${all.size}") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onScan) { Text("Сканировать") }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (platforms.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = filter == null, onClick = { filter = null },
                            label = { Text("Все (${all.size})") }
                        )
                    }
                    items(platforms) { p ->
                        FilterChip(
                            selected = filter == p.first, onClick = { filter = p.first },
                            label = { Text("${p.second} (${all.count { it.platformKey == p.first }})") }
                        )
                    }
                }
            } else {
                Text("Пока пусто. Нажмите «Сканировать».", Modifier.padding(16.dp))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(shown, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = {
                            Text(listOf(item.platformLabel, item.region).filter { it.isNotBlank() }.joinToString(" • "))
                        },
                        trailingContent = { TextButton(onClick = { vm.remove(item.id) }) { Text("Удалить") } }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val candidates by vm.candidates.collectAsState()
    val status by vm.status.collectAsState()
    var query by remember { mutableStateOf("") }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    val analyzer = remember { FrameAnalyzer(vm::onBarcode, vm::onOcrText, vm.wantText) }

    Column(Modifier.fillMaxWidth()) {
        if (granted) {
            CameraPreview(Modifier.fillMaxWidth().height(260.dp), analyzer)
        } else {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Для сканирования нужен доступ к камере.")
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Разрешить камеру") }
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                vm.wantText.set(true)
                vm.status.value = "Читаю текст…"
            }) { Text("Прочитать текст") }
            OutlinedButton(onClick = onBack) { Text("К коллекции") }
        }
        Text(status, Modifier.padding(horizontal = 12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; vm.search(it) },
            label = { Text("Поиск по названию вручную") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
        LazyColumn {
            items(candidates) { e ->
                ListItem(
                    headlineContent = { Text(e.displayTitle) },
                    supportingContent = { Text(e.details) },
                    trailingContent = { Button(onClick = { vm.add(e) }) { Text("Добавить") } }
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(modifier: Modifier, analyzer: ImageAnalysis.Analyzer) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            try {
                future.get().unbindAll()
            } catch (e: Exception) {
            }
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}
