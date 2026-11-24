package de.example.veomarker

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Ein Spiel. startedAt = Uhrzeit (ms), zu der du "Anpfiff" gedrückt hast. */
@Entity(tableName = "games")
data class Game(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startedAt: Long? = null,
    /** Position des Anpfiffs im Veo-Video in Sekunden (kann negativ sein). */
    val offsetSec: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** Eine Marke. elapsedMs = Zeit seit Anpfiff in ms. */
@Entity(
    tableName = "marks",
    foreignKeys = [ForeignKey(
        entity = Game::class,
        parentColumns = ["id"],
        childColumns = ["gameId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("gameId")]
)
data class Mark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val elapsedMs: Long,
    val tag: String = ""
)

@Dao
interface AppDao {
    @Query("SELECT * FROM games ORDER BY createdAt DESC")
    fun games(): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun game(id: Long): Flow<Game?>

    @Query("SELECT * FROM marks WHERE gameId = :gameId ORDER BY elapsedMs")
    fun marks(gameId: Long): Flow<List<Mark>>

    @Insert suspend fun insertGame(g: Game): Long
    @Update suspend fun updateGame(g: Game)
    @Delete suspend fun deleteGame(g: Game)

    @Insert suspend fun insertMark(m: Mark): Long
    @Delete suspend fun deleteMark(m: Mark)

    @Query("UPDATE marks SET tag = :tag WHERE id = :id")
    suspend fun updateTag(id: Long, tag: String)
}

@Database(entities = [Game::class, Mark::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDb::class.java, "veomarker.db"
            ).build().also { instance = it }
        }
    }
}
