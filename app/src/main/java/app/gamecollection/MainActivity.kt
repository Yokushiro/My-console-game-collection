package app.gamecollection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val PLATFORMS = listOf(
    "PlayStation", "PlayStation 2", "PlayStation 3", "PSP", "PS Vita",
    "Xbox", "Xbox 360", "Nintendo 64", "GameCube", "Wii",
    "Game Boy Advance", "Nintendo DS", "Nintendo 3DS",
    "Sega Saturn", "Sega Dreamcast", "Mega Drive / Genesis", "Mega-CD / Sega CD"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { PlatformList() }
            }
        }
    }
}

// Заглушка: проверяет, что сборка APK через GitHub Actions работает.
@Composable
private fun PlatformList() {
    LazyColumn(contentPadding = PaddingValues(top = 48.dp, bottom = 16.dp)) {
        item { ListItem(headlineContent = { Text("Моя коллекция игр", style = MaterialTheme.typography.headlineMedium) }) }
        items(PLATFORMS) { ListItem(headlineContent = { Text(it) }) }
    }
}
