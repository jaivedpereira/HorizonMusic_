package com.example.ui

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.*
import com.example.data.player.MusicPlayerManager
import com.example.data.player.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.musicDao()
    
    val playerManager = MusicPlayerManager(application)

    // SharedPreferences para salvar configurações do usuário persistentes
    private val prefs = application.getSharedPreferences("horizon_settings", Context.MODE_PRIVATE)

    // Preferência: Mostrar capas das músicas (Padrão: true)
    private val _showCovers = MutableStateFlow(prefs.getBoolean("show_covers", true))
    val showCovers: StateFlow<Boolean> = _showCovers.asStateFlow()

    // Preferência: Pasta específica para varredura de arquivos locais (Padrão: "" -> Todo o armazenamento)
    private val _customFolder = MutableStateFlow(prefs.getString("custom_folder", "") ?: "")
    val customFolder: StateFlow<String> = _customFolder.asStateFlow()

    // Preferência: Modo de Desempenho Extremo para desativar todas as animações pesadas e efeitos
    private val _extremePerformanceMode = MutableStateFlow(prefs.getBoolean("extreme_performance_mode", false))
    val extremePerformanceMode: StateFlow<Boolean> = _extremePerformanceMode.asStateFlow()

    // Preferência: Qualidade do Áudio Simulada / Cache de Buffer (Normal / Alta / Hi-Fi)
    private val _audioQuality = MutableStateFlow(prefs.getString("audio_quality", "Alta") ?: "Alta")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    // Preferência: Equalizador / Preset do Equalizador (Flat / Bass Boost / Vocal / Eletrônica)
    private val _eqPreset = MutableStateFlow(prefs.getString("eq_preset", "Flat") ?: "Flat")
    val eqPreset: StateFlow<String> = _eqPreset.asStateFlow()

    // Timer para Dormir ativo (minutos restantes, 0 se desativado)
    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    // Estados da UI
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _scannedLocalTracks = MutableStateFlow<List<Track>>(emptyList())
    val scannedLocalTracks: StateFlow<List<Track>> = _scannedLocalTracks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    // Faixas da Nuvem / Destaques inclusas para garantir que o app funcione na hora
    val cloudTracks = listOf(
        Track(
            id = "cloud_1",
            title = "Horizon Dreams",
            artist = "Lofi Hour",
            duration = 302000,
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            isLocal = false,
            album = "Horizon Retro",
            albumArt = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400&q=80"
        ),
        Track(
            id = "cloud_2",
            title = "Cyber Neon",
            artist = "Synth Lord",
            duration = 425000,
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            isLocal = false,
            album = "Neon Grid",
            albumArt = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=400&q=80"
        ),
        Track(
            id = "cloud_3",
            title = "Deep Space Pulse",
            artist = "Orbit Crew",
            duration = 344000,
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            isLocal = false,
            album = "Stargazing",
            albumArt = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=400&q=80"
        ),
        Track(
            id = "cloud_4",
            title = "Infinity Glow",
            artist = "Sunset Glider",
            duration = 302000,
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            isLocal = false,
            album = "Aether Wave",
            albumArt = "https://images.unsplash.com/photo-1507676184212-d03ab07a01bf?w=400&q=80"
        ),
        Track(
            id = "cloud_5",
            title = "Stardust Transit",
            artist = "Galactic Nomad",
            duration = 363000,
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            isLocal = false,
            album = "Celestial",
            albumArt = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=400&q=80"
        )
    )

    // Observa os Favoritos do Banco de Dados
    val favorites: StateFlow<List<Track>> = dao.getAllFavorites()
        .map { list -> list.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Observa as Playlists do Banco de Dados
    val playlists: StateFlow<List<PlaylistEntity>> = dao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combina faixas locais + faixas na nuvem
    val allAvailableTracks: StateFlow<List<Track>> = combine(_scannedLocalTracks, flowOf(cloudTracks)) { local, cloud ->
        cloud + local
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), cloudTracks)

    // Filtra as faixas baseadas na busca
    val filteredTracks: StateFlow<List<Track>> = combine(allAvailableTracks, _searchQuery) { tracks, query ->
        if (query.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Observa as faixas da playlist selecionada atualmente
    val selectedPlaylistTracks: StateFlow<List<Track>> = _selectedPlaylistId.flatMapLatest { playlistId ->
        if (playlistId == null) {
            flowOf(emptyList())
        } else {
            dao.getTracksForPlaylist(playlistId).map { list -> list.map { it.toTrack() } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentTrackLyrics = MutableStateFlow("")
    val currentTrackLyrics: StateFlow<String> = _currentTrackLyrics.asStateFlow()

    init {
        // Tenta escanear inicialmente (o app lidará com permissão na UI)
        scanLocalAudio()

        // Observa mudanças de faixa para carregar as letras automaticamente
        viewModelScope.launch {
            playerManager.currentTrack.collect { track ->
                if (track != null) {
                    _currentTrackLyrics.value = getTrackLyrics(track.id)
                } else {
                    _currentTrackLyrics.value = ""
                }
            }
        }
    }

    // Atualiza a configuração de mostrar capas de músicas
    fun setShowCovers(enabled: Boolean) {
        prefs.edit().putBoolean("show_covers", enabled).apply()
        _showCovers.value = enabled
    }

    // Atualiza o diretório de varredura específico
    fun setCustomFolder(path: String) {
        prefs.edit().putString("custom_folder", path.trim()).apply()
        _customFolder.value = path.trim()
        // Executa uma nova varredura imediatamente para atualizar a fila com a nova pasta
        scanLocalAudio()
    }

    // Configura o Modo de Desempenho Extremo
    fun setExtremePerformanceMode(enabled: Boolean) {
        prefs.edit().putBoolean("extreme_performance_mode", enabled).apply()
        _extremePerformanceMode.value = enabled
        // Se ativado, também desliga as capas de música para máxima fluidez
        if (enabled) {
            setShowCovers(false)
        }
    }

    // Configura a Qualidade do Áudio
    fun setAudioQuality(quality: String) {
        prefs.edit().putString("audio_quality", quality).apply()
        _audioQuality.value = quality
    }

    // Configura o Preset de Equalizador
    fun setEqPreset(preset: String) {
        prefs.edit().putString("eq_preset", preset).apply()
        _eqPreset.value = preset
    }

    // Configura o Sleep Timer (minutos)
    fun setSleepTimer(minutes: Int) {
        _sleepTimerMinutes.value = minutes
        sleepTimerJob?.cancel()
        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                var timeLeft = minutes
                while (timeLeft > 0) {
                    kotlinx.coroutines.delay(60000L) // Aguarda 1 minuto
                    timeLeft--
                    _sleepTimerMinutes.value = timeLeft
                }
                // Se o tempo acabar e a música estiver tocando, pausa
                playerManager.pause()
            }
        }
    }

    // Limpa o cache de imagem do Coil de maneira segura
    fun clearCoversCache(context: Context) {
        try {
            val imageLoader = coil.ImageLoader(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Erro ao limpar cache de imagens", e)
        }
    }

    // Escaneia arquivos do armazenamento local
    fun scanLocalAudio() {
        viewModelScope.launch {
            _isScanning.value = true
            val tracks = loadLocalTracksFromStorage()
            _scannedLocalTracks.value = tracks
            _isScanning.value = false
        }
    }

    private suspend fun loadLocalTracksFromStorage(): List<Track> = withContext(Dispatchers.IO) {
        val tracksList = mutableListOf<Track>()
        val context = getApplication<Application>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            "_data" // Obtém o caminho do arquivo físico local
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val filterPath = _customFolder.value

        try {
            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataColumn = cursor.getColumnIndex("_data")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Sem Título"
                    val artist = cursor.getString(artistColumn) ?: "Artista Desconhecido"
                    val duration = cursor.getLong(durationColumn)
                    val album = cursor.getString(albumColumn) ?: ""
                    val albumId = cursor.getLong(albumIdColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    
                    val filePath = if (dataColumn != -1) cursor.getString(dataColumn) ?: "" else ""

                    // Se houver filtro de pasta configurado, valida se o arquivo pertence a ela
                    if (filterPath.isNotEmpty() && filePath.isNotEmpty()) {
                        if (!filePath.startsWith(filterPath, ignoreCase = true)) {
                            continue // Ignora músicas que não estão dentro da pasta específica do usuário
                        }
                    }

                    val albumArtUri = "content://media/external/audio/albumart/$albumId"

                    tracksList.add(
                        Track(
                            id = contentUri.toString(),
                            title = title,
                            artist = artist,
                            duration = duration,
                            uri = contentUri.toString(),
                            isLocal = true,
                            album = album,
                            albumArt = albumArtUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Erro ao carregar arquivos locais", e)
        }
        tracksList
    }

    // Métodos de Controle do Reprodutor
    fun playTrack(track: Track, fromList: List<Track>) {
        val index = fromList.indexOf(track)
        playerManager.setQueue(fromList, if (index != -1) index else 0)
    }

    fun playAll(tracks: List<Track>) {
        if (tracks.isNotEmpty()) {
            playerManager.setQueue(tracks, 0)
        }
    }

    // Métodos de Busca
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Métodos de Playlist
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                dao.insertPlaylist(PlaylistEntity(name = name))
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            dao.deleteTracksForPlaylist(playlistId)
            dao.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
        }
    }

    fun selectPlaylist(playlistId: Long?) {
        _selectedPlaylistId.value = playlistId
    }

    fun updatePlaylistCover(playlistId: Long, coverUri: String?) {
        viewModelScope.launch {
            dao.updatePlaylistCover(playlistId, coverUri)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            dao.insertPlaylistTrack(track.toPlaylistTrackEntity(playlistId))
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            dao.deletePlaylistTrack(playlistId, trackId)
        }
    }

    // Métodos de Favoritos
    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val isFav = dao.isFavorite(track.id)
            if (isFav) {
                dao.deleteFavorite(track.id)
            } else {
                dao.insertFavorite(track.toFavoriteEntity())
            }
        }
    }

    fun isFavorite(trackId: String): Flow<Boolean> = flow {
        emit(dao.isFavorite(trackId))
    }.flowOn(Dispatchers.IO)

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            dao.updatePlaylistName(playlistId, newName)
        }
    }

    fun saveTrackLyrics(trackId: String, lyrics: String) {
        val lyricsPrefs = getApplication<Application>().getSharedPreferences("horizon_lyrics", Context.MODE_PRIVATE)
        lyricsPrefs.edit().putString(trackId, lyrics).apply()
        if (playerManager.currentTrack.value?.id == trackId) {
            _currentTrackLyrics.value = lyrics
        }
    }

    fun getTrackLyrics(trackId: String): String {
        val lyricsPrefs = getApplication<Application>().getSharedPreferences("horizon_lyrics", Context.MODE_PRIVATE)
        return lyricsPrefs.getString(trackId, "") ?: ""
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
