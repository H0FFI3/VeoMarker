package de.example.veomarker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()

    val games = dao.games()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun game(id: Long) = dao.game(id)
    fun marks(gameId: Long) = dao.marks(gameId)

    fun createGame(title: String) = viewModelScope.launch {
        dao.insertGame(Game(title = title.trim()))
    }

    fun deleteGame(g: Game) = viewModelScope.launch { dao.deleteGame(g) }

    fun startGame(g: Game) = viewModelScope.launch {
        dao.updateGame(g.copy(startedAt = System.currentTimeMillis()))
    }

    fun setOffset(g: Game, seconds: Int) = viewModelScope.launch {
        dao.updateGame(g.copy(offsetSec = seconds))
    }

    /** Speichert die Marke sofort (Zeitstempel ist exakt) und meldet die ID zurück. */
    fun addMark(gameId: Long, elapsedMs: Long, onCreated: (Long) -> Unit) =
        viewModelScope.launch {
            val id = dao.insertMark(Mark(gameId = gameId, elapsedMs = elapsedMs))
            onCreated(id)
        }

    fun setTag(markId: Long, tag: String) = viewModelScope.launch { dao.updateTag(markId, tag) }
    fun deleteMark(m: Mark) = viewModelScope.launch { dao.deleteMark(m) }
}
