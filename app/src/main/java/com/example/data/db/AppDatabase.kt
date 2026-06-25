package com.example.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Representação de uma faixa de música
data class Track(
    val id: String, // URI local ou URL web
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: String,
    val isLocal: Boolean,
    val album: String = "",
    val albumArt: String? = null
) {
    fun toFavoriteEntity() = FavoriteTrackEntity(
        trackId = id,
        title = title,
        artist = artist,
        duration = duration,
        uri = uri,
        isLocal = isLocal,
        album = album
    )

    fun toPlaylistTrackEntity(playlistId: Long) = PlaylistTrackEntity(
        playlistId = playlistId,
        trackId = id,
        title = title,
        artist = artist,
        duration = duration,
        uri = uri,
        isLocal = isLocal,
        album = album
    )
}

// Entidades do Banco de Dados
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val coverUri: String? = null
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: String,
    val isLocal: Boolean,
    val album: String = ""
) {
    fun toTrack() = Track(
        id = trackId,
        title = title,
        artist = artist,
        duration = duration,
        uri = uri,
        isLocal = isLocal,
        album = album
    )
}

@Entity(tableName = "favorite_tracks")
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: String,
    val isLocal: Boolean,
    val album: String = ""
) {
    fun toTrack() = Track(
        id = trackId,
        title = title,
        artist = artist,
        duration = duration,
        uri = uri,
        isLocal = isLocal,
        album = album
    )
}

// DAO
@Dao
interface MusicDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET coverUri = :coverUri WHERE id = :playlistId")
    suspend fun updatePlaylistCover(playlistId: Long, coverUri: String?)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun updatePlaylistName(playlistId: Long, newName: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId")
    fun getTracksForPlaylist(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deletePlaylistTrack(playlistId: Long, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteTracksForPlaylist(playlistId: Long)

    @Query("SELECT * FROM favorite_tracks")
    fun getAllFavorites(): Flow<List<FavoriteTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE trackId = :trackId")
    suspend fun deleteFavorite(trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: String): Boolean
}

// Banco de Dados
@Database(
    entities = [PlaylistEntity::class, PlaylistTrackEntity::class, FavoriteTrackEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "horizon_music_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
