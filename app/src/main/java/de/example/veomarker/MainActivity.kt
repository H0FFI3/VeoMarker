package de.example.veomarker

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bildschirm am Spielfeldrand nicht ausgehen lassen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.systemBarsPadding()) { App(vm) }
                }
            }
        }
    }
}

// ---------- Hilfsfunktionen ----------

/** ms -> "mm:ss" bzw. "h:mm:ss", negativ mit Minus. */
fun fmt(ms: Long): String {
    val t = abs(ms) / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    val body = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    return if (ms < 0) "-$body" else body
}

/** "12:34", "1:02:03", "-1:30" oder "90" (= Sekunden) -> Sekunden. */
fun parseSeconds(input: String): Int? {
    val t = input.trim()
    if (t.isEmpty()) return null
    val neg = t.startsWith("-")
    val parts = t.removePrefix("-").removePrefix("+").split(":").map { it.toIntOrNull() ?: return null }
    val secs = when (parts.size) {
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> return null
    }
    return if (neg) -secs else secs
}

// ---------- Navigation (bewusst simpel) ----------

@Composable
fun App(vm: MainViewModel) {
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val id = selected
    if (id == null) {
        GameListScreen(vm) { selected = it }
    } else {
        BackHandler { selected = null }
        GameScreen(vm, id) { selected = null }
    }
}

// ---------- Spieleliste ----------

@Composable
fun GameListScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {
    val games by vm.games.collectAsState()
    var title by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Game?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Veo Marker", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Neues Spiel, z. B. TSV – FC, 24.11.") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = title.isNotBlank(),
                onClick = { vm.createGame(title); title = "" }
            ) { Text("Anlegen") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(games, key = { it.id }) { g ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpen(g.id) }) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(g.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { toDelete = g }) { Text("Löschen") }
                    }
                }
            }
        }
    }

    toDelete?.let { g ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Spiel löschen?") },
            text = { Text("\"${g.title}\" und alle Marken werden gelöscht.") },
            confirmButton = { TextButton(onClick = { vm.deleteGame(g); toDelete = null }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Abbrechen") } }
        )
    }
}

// ---------- Spiel-Ansicht ----------

@Composable
fun GameScreen(vm: MainViewModel, gameId: Long, onBack: () -> Unit) {
    val game by remember(gameId) { vm.game(gameId) }.collectAsState(initial = null)
    val marks by remember(gameId) { vm.marks(gameId) }.collectAsState(initial = emptyList())
    game?.let { GameContent(vm, it, marks, onBack) }
}

@Composable
fun GameContent(vm: MainViewModel, game: Game, marks: List<Mark>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(game.startedAt) {
        while (true) { now = System.currentTimeMillis(); delay(250) }
    }

    // --- Spracheingabe (Google-Dialog, braucht keine Mikrofon-Berechtigung in der App) ---
    var pendingMarkId by rememberSaveable { mutableStateOf<Long?>(null) }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val id = pendingMarkId
        pendingMarkId = null
        if (res.resultCode == Activity.RESULT_OK && id != null) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) vm.setTag(id, text)
        }
    }
    fun dictate(markId: Long) {
        pendingMarkId = markId
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tag sprechen")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            pendingMarkId = null
            Toast.makeText(ctx, "Keine Spracherkennung auf diesem Gerät gefunden", Toast.LENGTH_LONG).show()
        }
    }

    var offsetText by remember(game.offsetSec) { mutableStateOf(fmt(game.offsetSec * 1000L)) }
    var editing by remember { mutableStateOf<Mark?>(null) }
    val start = game.startedAt

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text(game.title, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))

        if (start == null) {
            Button(
                onClick = { vm.startGame(game) },
                modifier = Modifier.fillMaxWidth().height(96.dp)
            ) { Text("Anpfiff – Zeit starten", style = MaterialTheme.typography.titleLarge) }
        } else {
            val elapsed = now - start
            Text("Spielzeit  ${fmt(elapsed)}", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Videozeit  ${fmt(elapsed + game.offsetSec * 1000L)}",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    // Zeitstempel wird im Moment des Klicks genommen, bevor das Sprechen beginnt
                    val el = System.currentTimeMillis() - start
                    vm.addMark(game.id, el) { newId -> dictate(newId) }
                },
                modifier = Modifier.fillMaxWidth().height(120.dp)
            ) { Text("Marke setzen + sprechen", style = MaterialTheme.typography.titleLarge) }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = offsetText,
                onValueChange = { offsetText = it },
                label = { Text("Anpfiff im Video bei (mm:ss)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val sec = parseSeconds(offsetText)
                if (sec == null) Toast.makeText(ctx, "Ungültig. Beispiel: 12:34", Toast.LENGTH_SHORT).show()
                else vm.setOffset(game, sec)
            }) { Text("Speichern") }
        }
        Text(
            "Der Offset lässt sich jederzeit ändern, auch nach dem Spiel.",
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(marks, key = { it.id }) { m ->
                MarkRow(
                    mark = m,
                    offsetSec = game.offsetSec,
                    onDictate = { dictate(m.id) },
                    onEdit = { editing = m },
                    onDelete = { vm.deleteMark(m) }
                )
            }
        }
    }

    editing?.let { m ->
        var text by remember(m.id) { mutableStateOf(m.tag) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Tag bearbeiten") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }) },
            confirmButton = { TextButton(onClick = { vm.setTag(m.id, text.trim()); editing = null }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
fun MarkRow(
    mark: Mark,
    offsetSec: Int,
    onDictate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    fmt(mark.elapsedMs + offsetSec * 1000L),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("Spielzeit ${fmt(mark.elapsedMs)}", style = MaterialTheme.typography.bodySmall)
            }
            Text(if (mark.tag.isBlank()) "(kein Tag)" else mark.tag)
            Row {
                TextButton(onClick = onDictate) { Text("Sprechen") }
                TextButton(onClick = onEdit) { Text("Bearbeiten") }
                TextButton(onClick = onDelete) { Text("Löschen") }
            }
        }
    }
}
